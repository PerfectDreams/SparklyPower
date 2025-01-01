package net.perfectdreams.dreamemotes.gestures

import com.destroystokyo.paper.profile.PlayerProfile
import com.destroystokyo.paper.profile.ProfileProperty
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundGameEventPacket
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.Vec3
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.DreamUtils
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.hidePlayerWithoutRemovingFromPlayerList
import net.perfectdreams.dreamcore.utils.extensions.sendPacket
import net.perfectdreams.dreamemotes.DreamEmotes
import net.perfectdreams.dreamemotes.OrbitalCamera
import net.perfectdreams.dreamemotes.StatueBase
import net.perfectdreams.dreamemotes.blockbench.BlockbenchModel
import net.perfectdreams.dreamemotes.commands.GestureCommand.MineSkinRatelimitHeaders
import net.perfectdreams.dreamemotes.tables.CachedGestureSkinHeads
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

class SparklyGesturesManager(val m: DreamEmotes) {
    val mineSkinRequestMutex = Mutex()
    var mineSkinRatelimitHeaders: MineSkinRatelimitHeaders? = null
    val logger = KotlinLogging.logger {}

    /**
     * Clones the image
     */
    private fun BufferedImage.clone() = deepCopy(this)

    private fun deepCopy(bi: BufferedImage): BufferedImage {
        val cm = bi.colorModel
        val isAlphaPremultiplied = cm.isAlphaPremultiplied
        val raster = bi.copyData(bi.raster.createCompatibleWritableRaster())
        return BufferedImage(cm, raster, isAlphaPremultiplied, null)
    }

    suspend fun getOrCreatePlayerGesturePlaybackSkins(player: Player): GestureSkinHeads {
        val playerSkinUrl = player.playerProfile.textures.skin

        val skinParts = mutableMapOf<String, StatueBase.StatuePart>()

        data class SplittedPart(
            val partName: String,
            val faceName: String,
            val x: Int,
            val y: Int,
            val width: Int,
            val height: Int
        )

        val splittedParts = mutableListOf<SplittedPart>()

        for (line in File(m.dataFolder, "power.png.txt").readLines()) {
            val split = line.split(";")
            val partName = split[0]
            val faceName = split[1]
            val topX = split[2].toInt()
            val topY = split[3].toInt()
            val bottomX = split[4].toInt()
            val bottomY = split[5].toInt()
            val width = bottomX - topX
            val height = bottomY - topY

            splittedParts.add(
                SplittedPart(
                    partName.removePrefix("[PART] "),
                    faceName,
                    topX,
                    topY,
                    width,
                    height
                )
            )
        }

        fun toSkinPart(splittedPart: SplittedPart): StatueBase.StatuePart.SkinPart {
            return StatueBase.StatuePart.SkinPart(
                splittedPart.x,
                splittedPart.y,
                splittedPart.width,
                splittedPart.height
            )
        }

        splittedParts.groupBy { it.partName }
            .forEach {
                skinParts[it.key] = StatueBase.StatuePart(
                    toSkinPart(it.value.first { it.faceName == "front" }),
                    toSkinPart(it.value.first { it.faceName == "front_layer" }),

                    toSkinPart(it.value.first { it.faceName == "back" }),
                    toSkinPart(it.value.first { it.faceName == "back_layer" }),

                    toSkinPart(it.value.first { it.faceName == "left" }),
                    toSkinPart(it.value.first { it.faceName == "left_layer" }),

                    toSkinPart(it.value.first { it.faceName == "right" }),
                    toSkinPart(it.value.first { it.faceName == "right_layer" }),

                    toSkinPart(it.value.first { it.faceName == "top" }),
                    toSkinPart(it.value.first { it.faceName == "top_layer" }),

                    toSkinPart(it.value.first { it.faceName == "bottom" }),
                    toSkinPart(it.value.first { it.faceName == "bottom_layer" }),
                ).also {
                    println(it)
                }
            }

        val playerSkin = if (playerSkinUrl != null) {
            ImageIO.read(playerSkinUrl)
        } else m.defaultSkin
        val isValidOldStyleSkin = playerSkin.width == 64 && playerSkin.height == 32

        val skinImage = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB) // Fixes paletted skins (Like Notch's skin)
        val skinImageGraphics = skinImage.createGraphics()
        skinImageGraphics.drawImage(playerSkin, 0, 0, null)

        if (isValidOldStyleSkin) {
            // Converts a skin to 1.8 format
            // This is *technically* not needed if we aren't editing the user's sweatshirt, but oh well...
            fun flipAndPaste(bufferedImage: BufferedImage, x: Int, y: Int) {
                skinImageGraphics.drawImage(
                    bufferedImage,
                    x + bufferedImage.width,
                    y,
                    -bufferedImage.width,
                    bufferedImage.height,
                    null
                )
            }

            // i have no idea what I'm doing
            val leg0 = skinImage.getSubimage(0, 16, 16, 4).clone()
            val leg1 = skinImage.getSubimage(4, 20, 8, 12).clone()
            val leg2 = skinImage.getSubimage(0, 20, 4, 12).clone()
            val leg3 = skinImage.getSubimage(12, 20, 4, 12).clone()

            val arm0 = skinImage.getSubimage(40, 16, 16, 4).clone()
            val arm1 = skinImage.getSubimage(4 + 40, 20, 8, 12).clone()
            val arm2 = skinImage.getSubimage(0 + 40, 20, 4, 12).clone()
            val arm3 = skinImage.getSubimage(12 + 40, 20, 4, 12).clone()

            skinImageGraphics.drawImage(leg0, 16, 48, null)
            flipAndPaste(leg1, 16, 52)
            flipAndPaste(leg2, 24, 52)
            flipAndPaste(leg3, 28, 52)

            skinImageGraphics.drawImage(arm0, 32, 48, null)
            flipAndPaste(arm1, 32, 52)
            flipAndPaste(arm2, 40, 52)
            flipAndPaste(arm3, 44, 52)
        }

        // Yes, this is VERY MESSY
        val skinModelForArmName = player.playerProfile.textures.skinModel.name.lowercase()

        val torsoTopSkin = createSkinOfPart(skinParts["torso_top"]!!, skinImage)
        val torsoBottomSkin = createSkinOfPart(skinParts["torso_bottom"]!!, skinImage)
        val armLeftTopSkin = createSkinOfPart(skinParts["arm_left_top_${skinModelForArmName}"]!!, skinImage)
        val armLeftBottomSkin = createSkinOfPart(skinParts["arm_left_bottom_${skinModelForArmName}"]!!, skinImage)
        val armRightTopSkin = createSkinOfPart(skinParts["arm_right_top_${skinModelForArmName}"]!!, skinImage)
        val armRightBottomSkin = createSkinOfPart(skinParts["arm_right_bottom_${skinModelForArmName}"]!!, skinImage)
        val legLeftTopSkin = createSkinOfPart(skinParts["leg_left_top"]!!, skinImage)
        val legLeftBottomSkin = createSkinOfPart(skinParts["leg_left_bottom"]!!, skinImage)
        val legRightTopSkin = createSkinOfPart(skinParts["leg_right_top"]!!, skinImage)
        val legRightBottomSkin = createSkinOfPart(skinParts["leg_right_bottom"]!!, skinImage)

        val torsoTopSkinHash = SHAsum(torsoTopSkin)
        val torsoBottomSkinHash = SHAsum(torsoBottomSkin)
        val armLeftTopSkinHash = SHAsum(armLeftTopSkin)
        val armLeftBottomSkinHash = SHAsum(armLeftBottomSkin)
        val armRightTopSkinHash = SHAsum(armRightTopSkin)
        val armRightBottomSkinHash = SHAsum(armRightBottomSkin)
        val legLeftTopSkinHash = SHAsum(legLeftTopSkin)
        val legLeftBottomSkinHash = SHAsum(legLeftBottomSkin)
        val legRightTopSkinHash = SHAsum(legRightTopSkin)
        val legRightBottomSkinHash = SHAsum(legRightBottomSkin)

        val allHashes = listOf(
            torsoTopSkinHash,
            torsoBottomSkinHash,
            armLeftTopSkinHash,
            armLeftBottomSkinHash,
            armRightTopSkinHash,
            armRightBottomSkinHash,
            legLeftTopSkinHash,
            legLeftBottomSkinHash,
            legRightTopSkinHash,
            legRightBottomSkinHash
        )

        var torsoTop: SkinResult? = null
        var torsoBottom: SkinResult? = null

        var armLeftTop: SkinResult? = null
        var armLeftBottom: SkinResult? = null

        var armRightTop: SkinResult? = null
        var armRightBottom: SkinResult? = null

        var legLeftTop: SkinResult? = null
        var legLeftBottom: SkinResult? = null

        var legRightTop: SkinResult? = null
        var legRightBottom: SkinResult? = null

        val now = Instant.now()
        transaction(Databases.databaseNetwork) {
            val cachedResults = CachedGestureSkinHeads.selectAll().where {
                CachedGestureSkinHeads.skinHash inList allHashes
            }.toList()

            for (cachedResult in cachedResults) {
                val result = SkinResult(cachedResult[CachedGestureSkinHeads.value], cachedResult[CachedGestureSkinHeads.signature])

                // We can't use "when" here because a skin may have multiple matching hashes
                // (Example: If a player has a solid color skin)
                // To work around this, we'll do multiple if statements
                val cachedHash = cachedResult[CachedGestureSkinHeads.skinHash]
                if (cachedHash == torsoTopSkinHash) {
                    torsoTop = result
                }
                if (cachedHash == torsoBottomSkinHash) {
                    torsoBottom = result
                }
                if (cachedHash == armLeftTopSkinHash) {
                    armLeftTop = result
                }
                if (cachedHash == armLeftBottomSkinHash) {
                    armLeftBottom = result
                }
                if (cachedHash == armRightTopSkinHash) {
                    armRightTop = result
                }
                if (cachedHash == armRightBottomSkinHash) {
                    armRightBottom = result
                }
                if (cachedHash == legLeftTopSkinHash) {
                    legLeftTop = result
                }
                if (cachedHash == legLeftBottomSkinHash) {
                    legLeftBottom = result
                }
                if (cachedHash == legRightTopSkinHash) {
                    legRightTop = result
                }
                if (cachedHash == legRightBottomSkinHash) {
                    legRightBottom = result
                }
            }

            CachedGestureSkinHeads.update({ CachedGestureSkinHeads.skinHash inList allHashes }) {
                it[CachedGestureSkinHeads.lastUsedAt] = now
            }
        }

        val all = listOf(
            torsoTop,
            torsoBottom,
            armLeftTop,
            armLeftBottom,
            armRightTop,
            armRightBottom,
            legLeftTop,
            legLeftBottom,
            legRightTop,
            legRightBottom
        )

        val missingSkins = all.count { it == null }

        var sentSkinUploadMessage = false
        fun sendSkinUploadMessage() {
            if (!sentSkinUploadMessage) {
                player.sendMessage(
                    textComponent {
                        content("Estamos fazendo mágica com a sua skin para que você possa usar gestos! Isso levará alguns minutos...")
                        color(NamedTextColor.YELLOW)
                    }
                )
            }
            sentSkinUploadMessage = true
        }

        var uploaded = 0
        fun sendSkinProgressMessage() {
            uploaded++
            player.sendMessage(
                textComponent {
                    content("Progresso de mágica com a sua skin: $uploaded/$missingSkins")
                    color(NamedTextColor.YELLOW)
                }
            )
        }

        if (torsoTop == null) {
            // Bukkit.broadcastMessage("torso top")
            sendSkinUploadMessage()
            torsoTop = uploadSkin(torsoTopSkin)
            sendSkinProgressMessage()
        }
        if (torsoBottom == null) {
            // Bukkit.broadcastMessage("torso bottom")
            sendSkinUploadMessage()
            torsoBottom = uploadSkin(torsoBottomSkin)
            sendSkinProgressMessage()
        }

        if (armLeftTop == null) {
            // Bukkit.broadcastMessage("arm left top")
            sendSkinUploadMessage()
            armLeftTop = uploadSkin(armLeftTopSkin)
            sendSkinProgressMessage()
        }
        if (armLeftBottom == null) {
            // Bukkit.broadcastMessage("arm left bottom")
            sendSkinUploadMessage()
            armLeftBottom = uploadSkin(armLeftBottomSkin)
            sendSkinProgressMessage()
        }

        if (armRightTop == null) {
            // Bukkit.broadcastMessage("arm right top")
            sendSkinUploadMessage()
            armRightTop = uploadSkin(armRightTopSkin)
            sendSkinProgressMessage()
        }
        if (armRightBottom == null) {
            // Bukkit.broadcastMessage("arm right bottom")
            sendSkinUploadMessage()
            armRightBottom = uploadSkin(armRightBottomSkin)
            sendSkinProgressMessage()
        }

        if (legLeftTop == null) {
            // Bukkit.broadcastMessage("leg left top")
            sendSkinUploadMessage()
            legLeftTop = uploadSkin(legLeftTopSkin)
            sendSkinProgressMessage()
        }
        if (legLeftBottom == null) {
            // Bukkit.broadcastMessage("leg left bottom")
            sendSkinUploadMessage()
            legLeftBottom = uploadSkin(legLeftBottomSkin)
            sendSkinProgressMessage()
        }

        if (legRightTop == null) {
            // Bukkit.broadcastMessage("leg right top")
            sendSkinUploadMessage()
            legRightTop = uploadSkin(legRightTopSkin)
            sendSkinProgressMessage()
        }
        if (legRightBottom == null) {
            // Bukkit.broadcastMessage("arm right bottom")
            sendSkinUploadMessage()
            legRightBottom = uploadSkin(legRightBottomSkin)
            sendSkinProgressMessage()
        }

        return GestureSkinHeads(
            player.playerProfile,

            createProfileFromMineSkinResponse(torsoTop!!),
            createProfileFromMineSkinResponse(torsoBottom!!),

            createProfileFromMineSkinResponse(armLeftTop!!),
            createProfileFromMineSkinResponse(armLeftBottom!!),

            createProfileFromMineSkinResponse(armRightTop!!),
            createProfileFromMineSkinResponse(armRightBottom!!),

            createProfileFromMineSkinResponse(legLeftTop!!),
            createProfileFromMineSkinResponse(legLeftBottom!!),

            createProfileFromMineSkinResponse(legRightTop!!),
            createProfileFromMineSkinResponse(legRightBottom!!),
        )
    }

    fun createGesturePlayback(
        player: Player,
        targetLocation: Location,
        gestureSkinHeads: GestureSkinHeads,
        blockbenchModel: BlockbenchModel,
        animation: SparklyGestureData
    ) {
        stopGesturePlayback(player)

        // val animation = blockbenchModel.animations.first { it.name == animationName }

        // This can't be used for any rotation because this only rotates the "self" entity, not the entire model
        // To rotate it, we need to rotate the location themselves
        //
        // We are storing the targetYaw of the location in another variable before passing it thru here
        val targetYaw = (360 - (targetLocation.yaw + 180) % 360) % 360

        val realTargetLocation = targetLocation.clone()
        realTargetLocation.yaw = 0f
        realTargetLocation.pitch = 0f

        // Text Displays do have the BAAAAD behavior of causing an weird "shift" motion when the player stops spectating an entity
        // Armor Stands do not have this issue, but it requires modifications to let the server send pos/rot updates every 1 tick instead of every 4
        //
        // I've tried manually resyncing the armor stand position, but it doesn't seem to work (sad)
        val armorStandCamera = player.world.spawn(
            player.location.add(0.0, 5.0, 0.0),
            TextDisplay::class.java
        ) {
            // it.text(textComponent("*câmera*"))
            it.teleportDuration = 1
            it.isPersistent = false
        }

        val gameModeChange = ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, 3f)

        val spectate = ClientboundSetCameraPacket((player as CraftPlayer).handle)
        // TODO: Add this to the helpful NMS packet changes
        val f = ClientboundSetCameraPacket::class.java.getDeclaredField("cameraId")
        f.isAccessible = true
        f.set(spectate, armorStandCamera.entityId)

        // We do this via packets because we have regions that the player can't ride vehicles and because only the player needs to know if they are sitting
        // (because we intercept via packets) we don't really need to make this using the API
        val entityToBeMountedNetworkId = Entity.nextEntityId()
        val entityToBeMountedPacket = ClientboundAddEntityPacket(
            entityToBeMountedNetworkId,
            UUID.randomUUID(),
            player.location.x,
            player.location.y + 1.8,
            player.location.z,
            0.0f,
            0.0f,
            EntityType.TEXT_DISPLAY,
            0,
            Vec3.ZERO,
            0.0
        )
        val setPassengersPacket = ClientboundSetPassengersPacket((player as CraftPlayer).handle)
        // TODO: Add this to SparklyPaper's NMS helper stuff
        val vehicleF = ClientboundSetPassengersPacket::class.java.getDeclaredField("vehicle")
        val passengersF = ClientboundSetPassengersPacket::class.java.getDeclaredField("passengers")
        vehicleF.isAccessible = true
        passengersF.isAccessible = true
        vehicleF.setInt(setPassengersPacket, entityToBeMountedNetworkId)
        passengersF.set(setPassengersPacket, intArrayOf(player.entityId))

        // entityToBeMounted.addPassenger(player)

        player.sendPacket(ClientboundBundlePacket(listOf(gameModeChange, spectate, entityToBeMountedPacket, setPassengersPacket)))

        // Focus the camera around the player's eye height
        val orbitalCamera = OrbitalCamera(m, player, targetLocation.clone().add(0.0, 1.8, 0.0), armorStandCamera)

        m.orbitalCameras[player] = orbitalCamera

        val soundEmitterEntity = player.world.spawn(
            targetLocation,
            TextDisplay::class.java
        ) {
            // it.text(textComponent("*câmera*"))
            it.teleportDuration = 1
            it.isPersistent = false
        }

        Bukkit.getOnlinePlayers()
            .forEach {
                if (!it.isBedrockClient) {
                    it.hidePlayerWithoutRemovingFromPlayerList(m, player)
                }
            }

        val playerGesturePlayback = PlayerGesturePlayback(
            m,
            player,
            blockbenchModel,
            animation,
            gestureSkinHeads,
            player.playerProfile.textures.skinModel,
            realTargetLocation,
            targetLocation,
            targetYaw,
            orbitalCamera,
            armorStandCamera,
            soundEmitterEntity,
            entityToBeMountedNetworkId
        )

        m.activeGesturePlaybacks[player] = playerGesturePlayback
    }

    fun stopGesturePlayback(player: Player) {
        val gesturePlayback = m.activeGesturePlaybacks.remove(player) ?: return

        gesturePlayback.stop()
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun SHAsum(convertme: ByteArray?): String {
        val md = MessageDigest.getInstance("SHA-1")
        return byteArray2Hex(md.digest(convertme))
    }

    private fun byteArray2Hex(hash: ByteArray): String {
        val formatter = Formatter()
        for (b in hash) {
            formatter.format("%02x", b)
        }
        return formatter.toString()
    }

    private suspend fun uploadSkin(imageAsByteArray: ByteArray): SkinResult {
        val hash = SHAsum(imageAsByteArray)

        return this.mineSkinRequestMutex.withLock {
            // Yes, we query this again because the skin may have been uploaded by another player or something
            // While it would be better to query within the "uploadSkinInner" call, because we only use a single mutex, we can query outside of it...
            val cachedResult = transaction(Databases.databaseNetwork) {
                CachedGestureSkinHeads.selectAll().where {
                    CachedGestureSkinHeads.skinHash eq hash
                }.firstOrNull()
            }

            if (cachedResult != null) {
                return@withLock SkinResult(
                    cachedResult[CachedGestureSkinHeads.value],
                    cachedResult[CachedGestureSkinHeads.signature]
                )
            }

            val skinResult = uploadSkinInner(imageAsByteArray, hash)

            val now = Instant.now()

            transaction(Databases.databaseNetwork) {
                // Check if it is cached
                val r = CachedGestureSkinHeads.insert {
                    it[CachedGestureSkinHeads.skinHash] = hash
                    it[CachedGestureSkinHeads.value] = skinResult.value
                    it[CachedGestureSkinHeads.signature] = skinResult.signature
                    it[CachedGestureSkinHeads.generatedAt] = now
                    it[CachedGestureSkinHeads.lastUsedAt] = now
                }

                return@transaction r
            }

            return@withLock skinResult
        }
    }

    private suspend fun uploadSkinInner(imageAsByteArray: ByteArray, hash: String): SkinResult {
        val mineSkinRatelimitHeaders = this.mineSkinRatelimitHeaders
        if (mineSkinRatelimitHeaders != null) {
            // Do we need to wait?
            if (mineSkinRatelimitHeaders.remaining == 0) {
                // Yeah, we do!
                val diff = (mineSkinRatelimitHeaders.reset * 1_000) - System.currentTimeMillis()
                logger.info { "Backing off for ${diff}ms because we are ratelimited!" }
                delay(diff)
            }
        }

        val mineSkinResponse = DreamUtils.http.submitFormWithBinaryData(
            "https://api.mineskin.org/generate/upload",
            formData = formData {
                append("name", "Player Uploaded Skin")
                append("visibility", 1)
                append(
                    "variant",
                    "classic"
                )
                append("file", imageAsByteArray, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"skin.png\"")
                })
            }
        ) {
            userAgent("SparklyPower-Pantufa")
            header(
                "Authorization",
                "Bearer ${m.config.mineskinApi.apiKey}"
            )
        }

        val responseAsString = mineSkinResponse.bodyAsText()

        if (mineSkinResponse.status == HttpStatusCode.TooManyRequests) {
            logger.warn { "Unexpected 429, retrying in 1s... ${responseAsString}" }
            delay(1_000)
            return uploadSkinInner(imageAsByteArray, hash)
        }

        if (mineSkinResponse.status != HttpStatusCode.OK) {
            error("Something went wrong while sending skin! Status: ${mineSkinResponse.status}; Body: $responseAsString")
        }

        this.mineSkinRatelimitHeaders = MineSkinRatelimitHeaders(
            mineSkinResponse.headers["x-ratelimit-remaining"]!!.toInt(),
            mineSkinResponse.headers["x-ratelimit-reset"]!!.toLong(),
        )

        val element = Json.parseToJsonElement(responseAsString)

        val mineSkinAccountUniqueId = element
            .jsonObject["data"]!!
            .jsonObject["uuid"]!!
            .jsonPrimitive
            .content

        val mineSkinTextureValue = element
            .jsonObject["data"]!!
            .jsonObject["texture"]!!
            .jsonObject["value"]!!
            .jsonPrimitive
            .content

        val mineSkinTextureSignature = element
            .jsonObject["data"]!!
            .jsonObject["texture"]!!
            .jsonObject["signature"]!!
            .jsonPrimitive
            .content

        val now = Instant.now()

        return SkinResult(
            mineSkinTextureValue,
            mineSkinTextureSignature
        )
    }

    private fun createSkinOfPart(part: StatueBase.StatuePart, playerSkin: BufferedImage): ByteArray {
        val newSkin = BufferedImage(playerSkin.width, playerSkin.height, BufferedImage.TYPE_INT_ARGB)
        val newSkinGraphics = newSkin.createGraphics()

        val bodyTop = playerSkin.getSubimage(part.top.x, part.top.y, part.top.width, part.top.height)
        val bodyFront = playerSkin.getSubimage(part.front.x, part.front.y, part.front.width, part.front.height)
        val bodyBack = playerSkin.getSubimage(part.back.x, part.back.y, part.back.width, part.back.height)
        val bodySide1 = playerSkin.getSubimage(part.left.x, part.left.y, part.left.width, part.left.height)
        val bodySide2 = playerSkin.getSubimage(part.right.x, part.right.y, part.right.width, part.right.height)
        val bodyBottom = playerSkin.getSubimage(part.bottom.x, part.bottom.y, part.bottom.width, part.bottom.height)

        newSkinGraphics.drawImage(bodyTop, 8, 0, 8, 8, null)
        newSkinGraphics.drawImage(bodyFront, 8, 8, 8, 8, null)
        newSkinGraphics.drawImage(bodyBack, 24, 8, 8, 8, null)
        newSkinGraphics.drawImage(bodySide1, 0, 8, 8, 8, null)
        newSkinGraphics.drawImage(bodySide2, 16, 8, 8, 8, null)
        newSkinGraphics.drawImage(bodyBottom, 16, 0, 8, 8, null)

        val bodyTopSecondaryLayer = playerSkin.getSubimage(part.topSecondaryLayer.x, part.topSecondaryLayer.y, part.topSecondaryLayer.width, part.topSecondaryLayer.height)
        val bodyFrontSecondLayer = playerSkin.getSubimage(part.frontSecondaryLayer.x, part.frontSecondaryLayer.y, part.frontSecondaryLayer.width, part.frontSecondaryLayer.height)
        val bodyBackSecondLayer = playerSkin.getSubimage(part.backSecondaryLayer.x, part.backSecondaryLayer.y, part.backSecondaryLayer.width, part.backSecondaryLayer.height)
        val bodySide1SecondLayer = playerSkin.getSubimage(part.leftSecondaryLayer.x, part.leftSecondaryLayer.y, part.leftSecondaryLayer.width, part.leftSecondaryLayer.height)
        val bodySide2SecondLayer = playerSkin.getSubimage(part.rightSecondaryLayer.x, part.rightSecondaryLayer.y, part.rightSecondaryLayer.width, part.rightSecondaryLayer.height)
        val bodyBottomSecondLayer = playerSkin.getSubimage(part.bottomSecondaryLayer.x, part.bottomSecondaryLayer.y, part.bottomSecondaryLayer.width, part.bottomSecondaryLayer.height)

        newSkinGraphics.drawImage(bodyTopSecondaryLayer, 40, 0, 8, 8, null)
        newSkinGraphics.drawImage(bodyFrontSecondLayer, 40, 8, 8, 8, null)
        newSkinGraphics.drawImage(bodyBackSecondLayer, 56, 8, 8, 8, null)
        newSkinGraphics.drawImage(bodySide1SecondLayer, 32, 8, 8, 8, null)
        newSkinGraphics.drawImage(bodySide2SecondLayer, 48, 8, 8, 8, null)
        newSkinGraphics.drawImage(bodyBottomSecondLayer, 48, 0, 8, 8, null)

        val baos = ByteArrayOutputStream()
        ImageIO.write(newSkin, "png", baos)
        return baos.toByteArray()
    }

    private fun createProfileFromMineSkinResponse(element: SkinResult): PlayerProfile {
        // While a skin can be loaded without a signature, the client checks if the domain ends with minecraft.net
        // (See TextureUrlChecker)
        val profile = Bukkit.createProfile(UUID(0L, 0L), "")
        profile.setProperty(
            ProfileProperty(
                "textures",
                element.value,
                element.signature
            )
        )

        return profile
    }

    private suspend fun createSkinOfPartAndUpload(part: StatueBase.StatuePart, playerSkin: BufferedImage): SkinResult {
        val skin = createSkinOfPart(part, playerSkin)
        val response = uploadSkin(skin)
        return response
    }

    data class SkinResult(
        val value: String,
        val signature: String
    )
}