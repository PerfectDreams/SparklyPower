package net.perfectdreams.dreamemotes.commands

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.displayNameWithoutDecorations
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.createMenu
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamemotes.DreamEmotes
import net.perfectdreams.dreamemotes.gestures.SparklyGesturesRegistry
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.logging.Level

class GestureCommand(val m: DreamEmotes) : SparklyCommandDeclarationWrapper {
    companion object {
        val SLOT_1 = buildSlotList(0, 1)
        val SLOT_2 = buildSlotList(3, 1)
        val SLOT_3 = buildSlotList(6, 1)
        val SLOT_4 = buildSlotList(0, 3)
        val SLOT_5 = buildSlotList(3, 3)
        val SLOT_6 = buildSlotList(6, 3)

        val SLOTS = listOf(
            SLOT_1,
            SLOT_2,
            SLOT_3,
            SLOT_4,
            SLOT_5,
            SLOT_6
        )

        private fun buildSlotList(startX: Int, startY: Int): List<Int> {
            return buildList {
                repeat(3) { x ->
                    repeat(2) { y ->
                        add((startX + x) + ((startY + y) * 9))
                    }
                }
            }
        }
    }

    override fun declaration() = sparklyCommand(listOf("gesture", "emote", "gesto")) {
        permission = "dreamemotes.emote"
        executor = EmoteExecutor(m)
    }

    class EmoteExecutor(val m: DreamEmotes) : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            // val file = word("file")
            val animation = optionalWord("animation")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()
            val requestedLocation = player.location

            if (!player.hasPermission("dreamemotes.bypassworldrestriction") && requestedLocation.world.name !in m.config.allowedWorlds) {
                player.sendMessage(
                    textComponent {
                        color(NamedTextColor.RED)
                        content("Você não pode usar gestos neste mundo!")
                    }
                )
                return
            }

            if (player.isBedrockClient) {
                player.sendMessage(
                    textComponent {
                        color(NamedTextColor.RED)
                        content("Apenas players Minecraft: Java Edition podem usar os gestos do SparklyPower!")
                    }
                )
                return
            }

            val animationName = args[options.animation]

            if (animationName != null) {
                val animation = SparklyGesturesRegistry.animations[animationName]

                if (animation == null) {
                    player.sendMessage(
                        textComponent {
                            color(NamedTextColor.RED)
                            content("Gesto ${animationName} não existe!")
                        }
                    )
                    return
                }

                m.launchAsyncThread {
                    val gestureSkinHeads = try {
                        m.gesturesManager.getOrCreatePlayerGesturePlaybackSkins(player)
                    } catch (e: Exception) {
                        m.logger.log(Level.WARNING, e) { "Something went wrong while trying to generate and upload the skins for gesture playback!" }
                        player.sendMessage {
                            textComponent {
                                color(NamedTextColor.RED)
                                content("Algo deu errado ao enviar as suas skins! Se você ainda quiser usar o gesto, use o comando novamente!")
                            }
                        }
                        return@launchAsyncThread
                    }

                    onMainThread {
                        val currentPlayerLocation = player.location

                        if (requestedLocation.world == currentPlayerLocation.world && 2 >= currentPlayerLocation.distanceSquared(requestedLocation)) {
                            // Cancel current gesture just for us to get the CORRECT exit location of the player
                            m.gesturesManager.stopGesturePlayback(player)

                            m.gesturesManager.createGesturePlayback(
                                player,
                                currentPlayerLocation,
                                gestureSkinHeads,
                                SparklyGesturesRegistry.blockbenchModels[animation.blockbenchModel]!!,
                                animation
                            )
                        } else {
                            player.sendMessage(
                                textComponent {
                                    color(NamedTextColor.RED)
                                    content("Você se moveu enquanto o gesto estava sendo carregado! Se você quiser usar o gesto, use o comando novamente.")
                                }
                            )
                        }
                    }
                }
            } else {
                val gestures = SparklyGesturesRegistry.animations.entries.take(6)

                val topRow = gestures.take(3)
                val bottomRow = gestures.drop(3).take(3)

                val menu = createMenu(
                    45,
                    textComponent {
                        font(Key.key("sparklypower:gestures/gestures_menu"))
                        color(NamedTextColor.WHITE)
                        appendTextComponent {
                            content("xa")
                        }

                        appendTextComponent {
                            font(Key.key("sparklypower:gestures/gestures_top_row"))
                            content(
                                buildString {
                                    append("y")
                                    for ((index, element) in topRow.withIndex()) {
                                        append(element.value.favoriteGesturesCharacter)
                                        if (index != 2)
                                            append("z")
                                    }
                                }
                            )
                        }

                        appendTextComponent {
                            font(Key.key("sparklypower:gestures/gestures_bottom_row"))
                            content(
                                buildString {
                                    append("y")
                                    for ((index, element) in bottomRow.withIndex()) {
                                        append(element.value.favoriteGesturesCharacter)
                                        if (index != 2)
                                            append("z")
                                    }
                                }
                            )
                        }
                    }
                ) {
                    for ((i, gesture) in gestures.withIndex()) {
                        val slot = SLOTS[i]

                        for (slotIndex in slot) {
                            slot(slotIndex) {
                                this.item = ItemStack.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                                    .meta<ItemMeta> {
                                        setCustomModelData(2)
                                        this.displayNameWithoutDecorations {
                                            color(NamedTextColor.YELLOW)
                                            append(MiniMessage.miniMessage().deserialize(gesture.value.name))
                                        }
                                    }

                                this.onClick {
                                    it.closeInventory()

                                    m.launchAsyncThread {
                                        val gestureSkinHeads =
                                            m.gesturesManager.getOrCreatePlayerGesturePlaybackSkins(player)

                                        onMainThread {
                                            // Cancel current gesture just for us to get the CORRECT exit location of the player
                                            m.gesturesManager.stopGesturePlayback(player)

                                            val currentPlayerLocation = player.location

                                            if (requestedLocation.world == currentPlayerLocation.world && 2 >= currentPlayerLocation.distanceSquared(
                                                    requestedLocation
                                                )
                                            ) {
                                                m.gesturesManager.createGesturePlayback(
                                                    player,
                                                    currentPlayerLocation,
                                                    gestureSkinHeads,
                                                    SparklyGesturesRegistry.blockbenchModels[gesture.value.blockbenchModel]!!,
                                                    gesture.value
                                                )
                                            } else {
                                                player.sendMessage(
                                                    textComponent {
                                                        color(NamedTextColor.RED)
                                                        content("Você se moveu enquanto o gesto estava sendo carregado! Se você quiser usar o gesto, use o comando novamente.")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                menu.sendTo(player)
            }


            /* val player = context.requirePlayer()
            val location = player.location.clone()

            // This can't be used for any rotation because this only rotates the "self" entity, not the entire model
            // To rotate it, we need to rotate the location themselves
            location.yaw = 0f
            location.pitch = 0f

            // Convert the player yaw to something understandable by us because IT DOES NOT MATCH WHAT WE EXPECT
            context.sendMessage("Your yaw is ${player.yaw}")
            val targetPlayerYaw = (360 - (player.yaw + 180) % 360) % 360
            context.sendMessage("Fixed yaw ${targetPlayerYaw}")
            if (true) {
                val blockbenchModel = Json {
                    ignoreUnknownKeys = true
                }.decodeFromString<BlockbenchModel>(File(m.dataFolder, "${args[options.file]}.bbmodel").readText())

                m.launchMainThread {
                    player.sendMessage("Enviando skins... (ou pegando do cache se já tem)")

                    // val baseStatue = Yaml.default.decodeFromString<StatueBase>(File(m.dataFolder, "statue.yml").readText())

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
                    val playerSkin = ImageIO.read(File("C:\\Users\\leona\\AppData\\Roaming\\.minecraft\\resourcepacks\\SparklyPowerPlus\\assets\\minecraft\\textures\\item\\plushies\\power.png"))

                    val torsoTop = createSkinOfPartAndUpload(skinParts["torso_top"]!!, playerSkin)
                    val torsoBottom = createSkinOfPartAndUpload(skinParts["torso_bottom"]!!, playerSkin)
                    val armLeftTop = createSkinOfPartAndUpload(skinParts["arm_left_top"]!!, playerSkin)
                    val armLeftBottom = createSkinOfPartAndUpload(skinParts["arm_left_bottom"]!!, playerSkin)
                    val armRightTop = createSkinOfPartAndUpload(skinParts["arm_right_top"]!!, playerSkin)
                    val armRightBottom = createSkinOfPartAndUpload(skinParts["arm_right_bottom"]!!, playerSkin)
                    val legLeftTop = createSkinOfPartAndUpload(skinParts["leg_left_top"]!!, playerSkin)
                    val legLeftBottom = createSkinOfPartAndUpload(skinParts["leg_left_bottom"]!!, playerSkin)
                    val legRightTop = createSkinOfPartAndUpload(skinParts["leg_right_top"]!!, playerSkin)
                    val legRightBottom = createSkinOfPartAndUpload(skinParts["leg_right_bottom"]!!, playerSkin)

                    player.gameMode = GameMode.SPECTATOR

                    val textDisplay = player.world.spawn(
                        player.location.add(0.0, 5.0, 0.0),
                        TextDisplay::class.java
                    ) {
                        it.teleportDuration = 1
                    }

                    val spectate = ClientboundSetCameraPacket((player as CraftPlayer).handle)
                    // TODO: Add this to the helpful NMS packet changes
                    val f = ClientboundSetCameraPacket::class.java.getDeclaredField("cameraId")
                    f.isAccessible = true
                    f.set(spectate, textDisplay.entityId)

                    player.sendPacket(spectate)

                    val armorStand = player.world.spawn(
                        player.location,
                        ArmorStand::class.java
                    ) {
                        it.isInvisible = true
                    }

                    armorStand.addPassenger(player)

                    val orbitalCamera = OrbitalCamera(m, player, player.location, textDisplay)

                    m.orbitalCameras[player] = orbitalCamera

                    player.world.players.forEach {
                        it.hideEntity(m, player)
                    }

                    val animation = blockbenchModel.animations.first { it.name == args[options.animation] }

                    val elementIdToEntities = mutableMapOf<UUID, ItemDisplay>()

                    val shadow = player.world.spawn(
                        location,
                        TextDisplay::class.java
                    ) {
                        it.isPersistent = false
                        it.isShadowed = true
                        it.shadowRadius = 0.5f
                    }

                    val TARGET_SCALE = 0.06f

                    val animationDuration = animation.length
                    val animationDurationInTicks = (animationDuration * 20).toInt()

                    repeat(animationDurationInTicks) {
                        // This is the ELAPSED TIME of the animation
                        val blockbenchTime = (it / 20.0)

                        for (outline in blockbenchModel.outliner.filter { it.visibility }) {
                            val matrix4f = Matrix4f()
                            var outlineOriginX = outline.origin[0]
                            var outlineOriginY = outline.origin[1]
                            var outlineOriginZ = outline.origin[2]

                            var offsetX = 0.0
                            var offsetY = 0.0
                            var offsetZ = 0.0

                            var outlineRotationX = outline.rotation[0]
                            var outlineRotationY = outline.rotation[1]
                            var outlineRotationZ = outline.rotation[2]

                            val animator = animation.animators[outline.uuid]

                            fun easeLinear(start: Double, end: Double, percent: Double): Double {
                                return start + (end - start) * percent
                            }

                            // TODO: The keyframe values are based from the DEFAULT POSE, FIX THIS


                            // Keyframes in Blockbench are weird, some axis use "-", some use "+"
                            // X and Y: -
                            // Z: +
                            if (animator != null) {
                                run {
                                    // Sort keyframes by their time (I'm not sure WHAT is the order that Blockbench uses, sometimes it is end to start, sometimes it is start to end)
                                    // So we'll sort it ourselves
                                    val sortedKeyframes = animator.keyframes.filter { it.channel == "rotation" }
                                        .sortedBy { it.time }

                                    if (sortedKeyframes.isEmpty())
                                        return@run

                                    val availableKeyframes = sortedKeyframes.filter { blockbenchTime >= it.time }
                                    val futureKeyframes = sortedKeyframes.filter { it.time > blockbenchTime }

                                    // Find the current and next keyframes
                                    val currentKeyframe = availableKeyframes.lastOrNull()
                                    val nextKeyframe = futureKeyframes.firstOrNull()

                                    // player.sendMessage("[BB Time: $blockbenchTime] Current Keyframe: $currentKeyframe")
                                    // player.sendMessage("[BB Time: $blockbenchTime] Next Keyframe: $nextKeyframe")

                                    if (currentKeyframe != null && nextKeyframe != null) {
                                        val rotCur = currentKeyframe.dataPoints.first()
                                        val rotNext = nextKeyframe.dataPoints.first()

                                        val relStart = blockbenchTime - currentKeyframe.time

                                        val progress = relStart / (nextKeyframe.time - currentKeyframe.time)
                                        // player.sendMessage("[BB Time: $blockbenchTime] Animator is not null! Progress is ${progress}")

                                        outlineRotationX = easeLinear(
                                            outlineRotationX - (rotCur.x),
                                            outlineRotationX - (rotNext.x),
                                            progress
                                        )
                                        outlineRotationY = easeLinear(
                                            outlineRotationY - (rotCur.y),
                                            outlineRotationY - (rotNext.y),
                                            progress
                                        )
                                        outlineRotationZ = easeLinear(
                                            outlineRotationZ + (rotCur.z),
                                            outlineRotationZ + (rotNext.z),
                                            progress
                                        )
                                        // player.sendMessage("[BB Time: $blockbenchTime] outlineRotationX (eased): $outlineRotationX")
                                        // player.sendMessage("[BB Time: $blockbenchTime] outlineRotationY (eased): $outlineRotationY")
                                        // player.sendMessage("[BB Time: $blockbenchTime] outlineRotationZ (eased): $outlineRotationZ")
                                    } else if (currentKeyframe != null) {
                                        val rotCur = currentKeyframe.dataPoints.first()

                                        // hold last keyframe
                                        // FOR SOME REASON BLOCKBENCH USES INVERTED VALUES FOR KEYFRAMES
                                        outlineRotationX -= (rotCur.x)
                                        outlineRotationY -= (rotCur.y)
                                        outlineRotationZ += (rotCur.z)
                                    }
                                }

                                run {
                                    // Sort keyframes by their time (I'm not sure WHAT is the order that Blockbench uses, sometimes it is end to start, sometimes it is start to end)
                                    // So we'll sort it ourselves
                                    val sortedKeyframes = animator.keyframes.filter { it.channel == "position" }
                                        .sortedBy { it.time }

                                    if (sortedKeyframes.isEmpty())
                                        return@run

                                    val availableKeyframes = sortedKeyframes.filter { blockbenchTime >= it.time }
                                    val futureKeyframes = sortedKeyframes.filter { it.time > blockbenchTime }

                                    // Find the current and next keyframes
                                    val currentKeyframe = availableKeyframes.lastOrNull()
                                    val nextKeyframe = futureKeyframes.firstOrNull()

                                    if (currentKeyframe != null && nextKeyframe != null) {
                                        val rotCur = currentKeyframe.dataPoints.first()
                                        val rotNext = nextKeyframe.dataPoints.first()

                                        val relStart = blockbenchTime - currentKeyframe.time

                                        // player.sendMessage("Current Keyframe: $currentKeyframe")
                                        // player.sendMessage("Next Keyframe: $nextKeyframe")
                                        // player.sendMessage("Animator is not null! Progress is ${(currentKeyframe.time + relStart) / nextKeyframe.time}")
                                        offsetX -= easeLinear(
                                            rotCur.x,
                                            rotNext.x,
                                            (currentKeyframe.time + relStart) / nextKeyframe.time
                                        )
                                        offsetY -= easeLinear(
                                            rotCur.y,
                                            rotNext.y,
                                            (currentKeyframe.time + relStart) / nextKeyframe.time
                                        )
                                        offsetZ += easeLinear(
                                            rotCur.z,
                                            rotNext.z,
                                            (currentKeyframe.time + relStart) / nextKeyframe.time
                                        )
                                        // player.sendMessage("outlineOriginX (eased): $outlineOriginX")
                                        // player.sendMessage("outlineOriginY (eased): $outlineOriginY")
                                        // player.sendMessage("outlineOriginZ (eased): $outlineOriginZ")
                                    } else if (currentKeyframe != null) {
                                        val rotCur = currentKeyframe.dataPoints.first()

                                        // hold last keyframe
                                        offsetX -= rotCur.x
                                        offsetY -= rotCur.y
                                        offsetZ += rotCur.z
                                    }
                                }
                            }

                            outlineOriginX += offsetX
                            outlineOriginY += offsetY
                            outlineOriginZ += offsetZ

                            val outlineRotationXRad = Math.toRadians(outlineRotationX)
                            val outlineRotationYRad = Math.toRadians(outlineRotationY)
                            val outlineRotationZRad = Math.toRadians(outlineRotationZ)

                            // YES THIS WORKS

                            // Think like this...
                            // This sets the target scale of this matrix4f ("scene")
                            matrix4f.scale(TARGET_SCALE)

                            // player.sendMessage("${outline.name}: outlineOriginX: $outlineOriginX; outlineOriginY: $outlineOriginY; outlineOriginZ: $outlineOriginZ")
                            // player.sendMessage("${outline.name}: outlineRotationXRad: $outlineRotationXRad; outlineRotationYRad: $outlineRotationYRad; outlineRotationZRad: $outlineRotationZRad")

                            matrix4f.rotateY(Math.toRadians(targetPlayerYaw.toDouble()).toFloat())
                            // Then we translate to the origin (pivot point)
                            matrix4f.translate(outlineOriginX.toFloat(), outlineOriginY.toFloat(), outlineOriginZ.toFloat())

                            // YES THIS IS THE ROTATION ORDER (Z -> Y -> X) BLOCKBENCH USES (I DON'T KNOW HOW)
                            // CHATGPT TOLD ME IT IS LIKE THIS SO WE TAKE THOSE W I GUESS
                            // https://chatgpt.com/c/67664f62-f33c-8007-afb1-a88159d98143
                            // I DON'T KNOW WHY THE ROTATION ORDER MATTERS
                            //
                            // The reason it matters (probably?) is that the rotation order impacts the rotation, so you need to match what rotation your 3D editor uses
                            matrix4f
                                .rotateZ(outlineRotationZRad.toFloat()) // Rotate around Z-axis
                                .rotateY(outlineRotationYRad.toFloat()) // Rotate around Y-axis
                                .rotateX(outlineRotationXRad.toFloat()); // Rotate around X-axis

                            // And translate it back to the world origin
                            matrix4f.translate(
                                -outlineOriginX.toFloat(),
                                -outlineOriginY.toFloat(),
                                -outlineOriginZ.toFloat()
                            )

                            val elements = blockbenchModel.elements.filter { it.uuid in outline.children }

                            for (element in elements) {
                                val topX = element.from[0]
                                val topY = element.from[1]
                                val topZ = element.from[2]
                                val bottomX = element.to[0]
                                val bottomY = element.to[1]
                                val bottomZ = element.to[2]

                                val centerX = ((topX + bottomX) / 2)
                                val centerY = ((topY + bottomY) / 2)
                                val centerZ = ((topZ + bottomZ) / 2)

                                val scaleX = abs(topX - element.to[0])
                                val scaleY = abs(topY - element.to[1])
                                val scaleZ = abs(topZ - element.to[2])

                                val originX = element.origin[0]
                                val originY = element.origin[1]
                                val originZ = element.origin[2]

                                // Yes we INTENTIONALLY use bottomY
                                val transformedPos = Vector3f((centerX + offsetX).toFloat(), (bottomY + offsetY).toFloat(), (centerZ + offsetZ).toFloat())
                                matrix4f.transformPosition(transformedPos)

                                val sourceLocation = location.clone().add(
                                    transformedPos.x.toDouble(),
                                    transformedPos.y.toDouble(),
                                    transformedPos.z.toDouble()
                                )

                                val existingEntity = elementIdToEntities[element.uuid]

                                if (existingEntity != null) {
                                    existingEntity.teleport(sourceLocation)

                                    existingEntity.setTransformationMatrix(
                                        Matrix4f()
                                            .rotateZ(outlineRotationZRad.toFloat()) // Rotate around Z-axis
                                            .rotateY(outlineRotationYRad.toFloat()) // Rotate around Y-axis
                                            .rotateX(outlineRotationXRad.toFloat()) // Rotate around X-axis
                                            .rotateLocalY(Math.toRadians(targetPlayerYaw.toDouble()).toFloat()) // This rotates "globally"
                                            .scale(
                                                (scaleX.toFloat() * 2f) * TARGET_SCALE,
                                                (scaleY.toFloat() * 2f) * TARGET_SCALE,
                                                (scaleZ.toFloat() * 2f) * TARGET_SCALE
                                            )
                                    )
                                } else {
                                    val entity = location.world.spawn(
                                        // We INTENTIONALLY use topY instead of centerY, because Minecraft scales based on the item's TOP LOCATION, not the CENTER
                                        sourceLocation, // location.clone().add(centerX, bottomY, centerZ),
                                        ItemDisplay::class.java
                                    ) {
                                        // A normal player head item has 0.5 scale in Blockbench
                                        // A cube is 2x2x2 in Blockbench

                                        it.interpolationDelay = 1
                                        it.teleportDuration = 1

                                        // if (true || outline.name == "arm_right") {
                                        // If we use outlineRotationX.toFloat, it does work, but why that works while toRadians is borked??
                                        // player.sendMessage("outlineRotationXRad: $outlineRotationXRad")
                                        // player.sendMessage("outlineRotationYRad: $outlineRotationYRad")
                                        // player.sendMessage("outlineRotationZRad: $outlineRotationZRad")

                                        it.setTransformationMatrix(
                                            Matrix4f()
                                                .rotateZ(outlineRotationZRad.toFloat()) // Rotate around Z-axis
                                                .rotateY(outlineRotationYRad.toFloat()) // Rotate around Y-axis
                                                .rotateX(outlineRotationXRad.toFloat()) // Rotate around X-axis
                                                .scale(
                                                    (scaleX.toFloat() * 2f) * TARGET_SCALE,
                                                    (scaleY.toFloat() * 2f) * TARGET_SCALE,
                                                    (scaleZ.toFloat() * 2f) * TARGET_SCALE
                                                )
                                        )

                                        it.setItemStack(
                                            ItemStack(Material.PLAYER_HEAD)
                                                .meta<SkullMeta> {
                                                    when (element.name) {
                                                        "torso_top" -> {
                                                            val profile = createProfileFromMineSkinResponse(torsoTop)
                                                            this.playerProfile = profile
                                                        }
                                                        "torso_bottom" -> {
                                                            val profile = createProfileFromMineSkinResponse(torsoBottom)
                                                            this.playerProfile = profile
                                                        }

                                                        "leg_left_top" -> {
                                                            val profile = createProfileFromMineSkinResponse(legLeftTop)
                                                            this.playerProfile = profile
                                                        }
                                                        "leg_left_bottom" -> {
                                                            val profile = createProfileFromMineSkinResponse(legLeftBottom)
                                                            this.playerProfile = profile
                                                        }

                                                        "leg_right_top" -> {
                                                            val profile = createProfileFromMineSkinResponse(legRightTop)
                                                            this.playerProfile = profile
                                                        }
                                                        "leg_right_bottom" -> {
                                                            val profile = createProfileFromMineSkinResponse(legRightBottom)
                                                            this.playerProfile = profile
                                                        }

                                                        "arm_left_top" -> {
                                                            val profile = createProfileFromMineSkinResponse(armLeftTop)
                                                            this.playerProfile = profile
                                                        }
                                                        "arm_left_bottom" -> {
                                                            val profile = createProfileFromMineSkinResponse(armLeftBottom)
                                                            this.playerProfile = profile
                                                        }

                                                        "arm_right_top" -> {
                                                            val profile = createProfileFromMineSkinResponse(armRightTop)
                                                            this.playerProfile = profile
                                                        }
                                                        "arm_right_bottom" -> {
                                                            val profile = createProfileFromMineSkinResponse(armRightBottom)
                                                            this.playerProfile = profile
                                                        }

                                                        else -> {
                                                            this.playerProfile = player.playerProfile
                                                        }
                                                    }
                                                }
                                        )
                                    }

                                    elementIdToEntities[element.uuid] = entity
                                }
                            }
                        }

                        delayTicks(1L)
                    }

                    player.sendMessage("Finished!")

                    delayTicks(200L)

                    elementIdToEntities.forEach { t, u ->
                        u.remove()
                    }

                    player.world.players.forEach {
                        it.showEntity(m, player)
                    }

                    shadow.remove()
                    armorStand.remove()
                    player.gameMode = GameMode.SURVIVAL

                    val spectate2 = ClientboundSetCameraPacket((player as CraftPlayer).handle)
                    // TODO: Add this to the helpful NMS packet changes
                    val f2 = ClientboundSetCameraPacket::class.java.getDeclaredField("cameraId")
                    f2.isAccessible = true
                    f2.set(spectate2, player.entityId)

                    player.sendPacket(spectate2)
                    orbitalCamera.alive = false
                }
                return
            } */
        }
    }

    class MineSkinRatelimitHeaders(
        val remaining: Int,
        val reset: Long
    )
}