package net.perfectdreams.pantufa.interactions.vanilla.minecraft

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.MessageCreate
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.FileUpload
import net.perfectdreams.dreamcore.utils.skins.StoredDatabaseSkin
import net.perfectdreams.loritta.common.commands.CommandCategory
import net.perfectdreams.loritta.morenitta.interactions.UnleashedContext
import net.perfectdreams.loritta.morenitta.interactions.commands.*
import net.perfectdreams.loritta.morenitta.interactions.commands.options.ApplicationCommandOptions
import net.perfectdreams.pantufa.*
import net.perfectdreams.pantufa.api.commands.styled
import net.perfectdreams.pantufa.tables.PlayerSkins
import net.perfectdreams.pantufa.utils.Constants
import net.perfectdreams.pantufa.utils.Emotes
import net.perfectdreams.pantufa.utils.ImageUtils
import net.perfectdreams.pantufa.utils.SkinRendererUtils
import net.perfectdreams.pantufa.utils.extensions.toJDA
import net.sparklypower.rpc.PrestartPantufaPrintShopCustomMapsRequest
import net.sparklypower.rpc.PrestartPantufaPrintShopCustomMapsResponse
import net.sparklypower.tables.PlayerPantufaPrintShopCustomMaps
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.util.*
import javax.imageio.ImageIO

class CustomMapCommand(val m: PantufaBot) : SlashCommandDeclarationWrapper {
    companion object {
        val MAP_SIZE_REGEX = Regex("([0-9]+)x([0-9]+)")

        private val SKINS = listOf(
            "/skins/loritta.png",
            "/skins/pantufa.png",
            "/skins/gabriela.png",
        )
    }

    override fun command() = slashCommand("mapapersonalizado", "Crie mapas personalizados usando pesadelos", CommandCategory.MINECRAFT) {
        requireMinecraftAccount = true

        executor = CustomMapExecutor(m)
    }

    class CustomMapExecutor(val m: PantufaBot) : LorittaSlashCommandExecutor() {
        companion object {
            private val logger = KotlinLogging.logger {}
        }

        inner class Options : ApplicationCommandOptions() {
            val image = attachment("image", "A imagem que você deseja transformar em mapas")

            val size = string("size", "Tamanho da imagem em mapas, por exemplo, 2x2")

            val resizeMethod = string("resize_method", "Como a imagem será redimensionada para caber nos mapas") {
                choice("Esticar a imagem até preencher a imagem", ResizeMethod.SCALE_TO_FIT.name)
                choice("Ajustar a imagem até ela conter dentro da imagem", ResizeMethod.CONTAIN.name)
            }

            val copies = optionalInteger("copies", "Quantas cópias do mapa você deseja")
        }

        override val options = Options()

        override suspend fun execute(context: UnleashedContext, args: SlashCommandArguments) {
            val attachment = args[options.image]
            val size = args[options.size]
            val resizeMethod = ResizeMethod.valueOf(args[options.resizeMethod])
            val copies = args[options.copies] ?: 0

            if (copies > 32) {
                context.reply(true) {
                    styled(
                        "Quantidade de cópias inválida! Máximo de 32 cópias!"
                    )
                }
                return
            }

            context.deferChannelMessage(true)

            val account = context.retrieveConnectedMinecraftAccountOrFail()

            val match = MAP_SIZE_REGEX.matchEntire(size)
            if (match == null) {
                context.reply(true) {
                    styled(
                        "Tamanho de mapa inválido!"
                    )
                }
                return
            }

            val targetItemFrameWidth = match.groupValues[1].toInt()
            val targetItemFrameHeight = match.groupValues[2].toInt()

            if (targetItemFrameWidth !in 1..10) {
                context.reply(true) {
                    styled(
                        "Tamanho de mapa inválido!"
                    )
                }
                return
            }
            if (targetItemFrameHeight !in 1..10) {
                context.reply(true) {
                    styled(
                        "Tamanho de mapa inválido!"
                    )
                }
                return
            }

            // Generate image
            val targetImageWidth = targetItemFrameWidth * 128
            val targetImageHeight = targetItemFrameHeight * 128

            val sourceImage = ImageUtils.downloadImage(attachment.url)

            if (sourceImage == null) {
                context.reply(true) {
                    styled(
                        "Imagem inválida!"
                    )
                }
                return
            }

            // Load the player's skin, this will be used in the map preview
            // This code is from DreamCore's SkinsListener
            // Load current skin from the database, if present
            val data = m.transactionOnSparklyPowerDatabase {
                val playerSkin = PlayerSkins.selectAll().where { PlayerSkins.id eq account.uniqueId }
                    .limit(1)
                    .firstOrNull() ?: return@transactionOnSparklyPowerDatabase null

                Json.decodeFromString<StoredDatabaseSkin>(playerSkin[PlayerSkins.data])
            }

            val skinData = if (data is StoredDatabaseSkin.MojangSkin) {
                data.value
            } else {
                null
            }

            var playerSkin: BufferedImage? = null
            var isPlayerSkinSlim = false

            if (skinData != null) {
                val skinDataAsJson = Json.parseToJsonElement(Base64.getDecoder().decode(skinData).toString(Charsets.UTF_8))

                val skinUrl = skinDataAsJson
                    .jsonObject["textures"]
                    ?.jsonObject
                    ?.get("SKIN")
                    ?.jsonObject
                    ?.get("url")
                    ?.jsonPrimitive
                    ?.content

                if (skinUrl != null) {
                    try {
                        playerSkin = ImageIO.read(URL(skinUrl))

                        isPlayerSkinSlim = skinDataAsJson
                            .jsonObject["textures"]
                            ?.jsonObject
                            ?.get("SKIN")
                            ?.jsonObject
                            ?.get("metadata")
                            ?.jsonObject
                            ?.get("model")
                            ?.jsonPrimitive
                            ?.content == "slim"
                    } catch (e: IOException) {
                        logger.warn(e) { "Something went wrong while trying to download skins" }
                    }
                }
            }

            if (playerSkin == null) {
                // This should NEVER be null
                playerSkin = ImageIO.read(PantufaBot::class.java.getResourceAsStream("/skins/steve.png"))!!
            }

            // TODO: Don't resize it in this way
            val sourceImageResized = when (resizeMethod) {
                ResizeMethod.SCALE_TO_FIT -> toBufferedImage(sourceImage.getScaledInstance(targetImageWidth, targetImageHeight, BufferedImage.SCALE_SMOOTH))
                ResizeMethod.CONTAIN -> {
                    // Contain is a bit more hard to do, but not impossible!
                    // First, we need to create a base image
                    val base = BufferedImage(targetImageWidth, targetImageHeight, BufferedImage.TYPE_INT_ARGB)
                    val baseGraphics = base.createGraphics()

                    // Now, we need to calculate the target width/height of the image for the contain
                    // What's the largest of both axis?
                    val isItemFrameTargetBiggerOnTheWidth = targetItemFrameWidth > targetItemFrameHeight
                    val isImageSourceBiggerOnTheWidth = targetImageWidth > targetImageHeight

                    val targetContainWidth: Int
                    val targetContainHeight: Int

                    // This is a bit hard to handle because we need to think about both sides: The target item frame size, and the source image size
                    // TODO: Fix this
                    if (isItemFrameTargetBiggerOnTheWidth) {
                        // println("item frame bigger on the width, image bigger on the width")
                        targetContainWidth = targetImageWidth
                        // originalWidth --- targetImageWidth
                        // originalHeight --- x
                        targetContainHeight = (targetContainWidth * sourceImage.height) / sourceImage.width
                    } else {
                        // println("bigger on the height")
                        targetContainHeight = targetImageHeight
                        // originalWidth --- targetImageWidth
                        // originalHeight --- x
                        targetContainWidth = (targetContainHeight * sourceImage.width) / sourceImage.height
                    }

                    // println("Target contain width: $targetContainWidth")
                    // println("Target contain height: $targetContainHeight")

                    val targetX = (targetImageWidth - targetContainWidth) / 2
                    val targetY = (targetImageHeight - targetContainHeight) / 2

                    // println("TargetX: $targetX")
                    // println("TargetY: $targetY")
                    baseGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    baseGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                    baseGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    baseGraphics.drawImage(
                        sourceImage,
                        targetX,
                        targetY,
                        targetContainWidth,
                        targetContainHeight,
                        null
                    )

                    base
                }
            }

            var itemFrameX = 0
            var itemFrameY = 0
            val generatedItemFrameImages = mutableListOf<ItemFrameImage>()

            while (true) {
                val image = sourceImageResized.getSubimage(itemFrameX * 128, itemFrameY * 128, 128, 128)

                if (isImageEmpty(image)) {
                    generatedItemFrameImages.add(ItemFrameImage.EmptyFrame(itemFrameX, itemFrameY))
                } else {
                    generatedItemFrameImages.add(ItemFrameImage.FrameImage(itemFrameX, itemFrameY, image))
                }

                itemFrameX++
                if (itemFrameX == targetItemFrameWidth) {
                    itemFrameX = 0
                    itemFrameY++
                    if (itemFrameY == targetItemFrameHeight) {
                        break
                    }
                }
            }

            /* val targetFolder = File("D:\\SparklyPowerAssets\\ItemFrameGenScratchPad")

            generatedItemFrameImages.forEach {
                println(it)
                when (it) {
                    is ItemFrameImage.EmptyFrame -> {}
                    is ItemFrameImage.FrameImage -> {
                        ImageIO.write(
                            it.image,
                            "png",
                            File(targetFolder, "${it.x}_${it.y}.png"),
                        )
                    }
                }
            } */

            val amountOfImagesOnItemFrames = generatedItemFrameImages.count {
                it is ItemFrameImage.FrameImage
            }

            if (amountOfImagesOnItemFrames == 0) {
                context.reply(true) {
                    styled(
                        "Se a gráfica gerasse os mapas com a imagem que você deu, a gráfica só te daria mapas vazios!"
                    )
                }
                return
            }

            // Now let's generate a cool item frame preview
            val previewImage = BufferedImage((targetItemFrameWidth + 4) * 128, (targetItemFrameHeight + 2) * 128, BufferedImage.TYPE_INT_ARGB)
            val previewImageGraphics = previewImage.createGraphics()

            val itemFrameBackImage = ImageIO.read(PantufaBot::class.java.getResourceAsStream("/item_frame.png"))
            val quartzBlockSideImage = ImageIO.read(PantufaBot::class.java.getResourceAsStream("/quartz_block_side.png"))
            val quartzPillarSideImage = ImageIO.read(PantufaBot::class.java.getResourceAsStream("/quartz_pillar.png"))

            val itemFrameBack128Image = itemFrameBackImage.getScaledInstance(128, 128, BufferedImage.SCALE_FAST)
            val quartzBlockSide128Image = quartzBlockSideImage.getScaledInstance(128, 128, BufferedImage.SCALE_FAST)
            val quartzPillarSide128Image = quartzPillarSideImage.getScaledInstance(128, 128, BufferedImage.SCALE_FAST)

            // Render a small statue
            val skinStatueImage = SkinRendererUtils.createStatueOfSkin(playerSkin, isPlayerSkinSlim)
            val pantufaStatueImage = SkinRendererUtils.createStatueOfSkin(ImageIO.read(PantufaBot::class.java.getResourceAsStream(SKINS.random())), true)

            // ImageIO.write(skinStatueImage, "png", File(targetFolder, "statue.png"))
            // ImageIO.write(pantufaStatueImage, "png", File(targetFolder, "pantufa_status.png"))

            // Now let's render the preview!
            for (x in 0 until previewImage.width step 128) {
                for (y in 0 until previewImage.height step 128) {
                    if (x == 0 || x == previewImage.width - 128) {
                        previewImageGraphics.drawImage(quartzPillarSide128Image, x, y, null)
                    } else {
                        previewImageGraphics.drawImage(quartzBlockSide128Image, x, y, null)
                    }

                    // In this current coordinate, do we have an item frame?
                    // Keep in mind that this needs to be offset by 1!
                    val itemFrameOnThisPixel = generatedItemFrameImages.firstOrNull {
                        it.x == (x / 128) - 2 && it.y == (y / 128) - 1
                    }

                    if (itemFrameOnThisPixel != null) {
                        // Yeah we do have a frame!
                        if (itemFrameOnThisPixel is ItemFrameImage.FrameImage) {
                            // We will only render frames with images tho, not transparent images
                            previewImageGraphics.drawImage(itemFrameBack128Image, x, y, null)
                            previewImageGraphics.drawImage(itemFrameOnThisPixel.image, x, y, null)
                        }
                    }
                }
            }

            // Draw player statue
            previewImageGraphics.drawImage(skinStatueImage, 64, previewImage.height - 256, 128, 256, null)
            // Draw pantufa status
            previewImageGraphics.drawImage(pantufaStatueImage, previewImage.width - 192, previewImage.height - 256, 128, 256, null)

            val imageAsByteArray = ByteArrayOutputStream().use {
                ImageIO.write(previewImage, "png", it)
                it.toByteArray()
            }

            val prestartResponse = Json.decodeFromString<PrestartPantufaPrintShopCustomMapsResponse>(
                PantufaBot.http.post("${m.config.sparklyPower.server.sparklyPowerSurvival.apiUrl.removeSuffix("/")}/pantufa/prestart-pantufa-print-shop-maps") {
                    setBody(
                        Json.encodeToString(
                            PrestartPantufaPrintShopCustomMapsRequest(account.uniqueId.toString(), amountOfImagesOnItemFrames, copies)
                        )
                    )
                }.bodyAsText()
            )

            when (prestartResponse) {
                PrestartPantufaPrintShopCustomMapsResponse.PluginUnavailable -> {
                    context.reply(true) {
                        styled(
                            "Plugin não está disponível, tente novamente mais tarde!",
                            Constants.ERROR
                        )
                    }
                }
                is PrestartPantufaPrintShopCustomMapsResponse.NotEnoughPesadelos -> {
                    context.reply(true) {
                        styled(
                            "Você não tem pesadelos suficientes!",
                            Constants.ERROR
                        )
                    }
                }
                is PrestartPantufaPrintShopCustomMapsResponse.Success -> {
                    context.reply(true) {
                        if (amountOfImagesOnItemFrames == 1) {
                            styled(
                                "**Pré-visualização do Mapa Personalizado**",
                                Emotes.PantufaHi
                            )
                        } else {
                            styled(
                                "**Pré-visualização dos Mapas Personalizados**",
                                Emotes.PantufaHi
                            )
                        }

                        styled(
                            "**Cópias:** $copies",
                            Emotes.PantufaLurk
                        )

                        styled(
                            "**Preço:** ${Emotes.Pesadelos} ${prestartResponse.totalCost} pesadelos",
                            Emotes.PantufaCoffee
                        )

                        styled(
                            "**Atenção:** A qualidade dos mapas dentro do SparklyPower não será a mesma devido a limitações do Minecraft",
                            Emotes.PantufaMegaphone
                        )

                        styled(
                            "Após enviar para a gráfica você terá que esperar a equipe aprovar a imagem e, quando ela for aprovada, você receberá os mapas na `/warp correios`!",
                            Emotes.PantufaLurk
                        )

                        files += FileUpload.fromData(imageAsByteArray, "map.png")

                        actionRow(
                            pantufa.interactivityManager.buttonForUser(
                                context.user,
                                ButtonStyle.PRIMARY,
                                "Enviar para a Gráfica",
                                {
                                    loriEmoji = Emotes.FilledMap
                                }
                            ) { context ->
                                context.invalidateComponentCallback()
                                context.deferChannelMessage(true)

                                val mapRequestId = m.transactionOnSparklyPowerDatabase {
                                    PlayerPantufaPrintShopCustomMaps.insertAndGetId {
                                        it[PlayerPantufaPrintShopCustomMaps.requestedBy] = account.uniqueId
                                        it[PlayerPantufaPrintShopCustomMaps.mapImagesCount] = amountOfImagesOnItemFrames
                                        it[PlayerPantufaPrintShopCustomMaps.mapImages] = generatedItemFrameImages
                                            .filterIsInstance<ItemFrameImage.FrameImage>()
                                            .joinToString(",") {
                                                val itemFrameImageAsByteArray = ByteArrayOutputStream().use { baos ->
                                                    ImageIO.write(it.image, "png", baos)
                                                    baos.toByteArray()
                                                }

                                                Base64.getEncoder().encodeToString(itemFrameImageAsByteArray)
                                            }
                                        it[PlayerPantufaPrintShopCustomMaps.requestedAt] = Instant.now()
                                        it[PlayerPantufaPrintShopCustomMaps.copies] = copies
                                    }
                                }

                                val channel = context.jda.getGuildChannelById(pantufa.config.sparklyPower.guild.pantufaPrintShopChannelId)!! as MessageChannel
                                channel.sendMessage(
                                    MessageCreate {
                                        styled(
                                            "**Imagem pedida por:** ${context.user.asMention} [**`${account.username}`** (`${account.uniqueId}`)]"
                                        )

                                        styled(
                                            "**Tamanho da imagem:** ${targetItemFrameWidth}x${targetItemFrameHeight} ($amountOfImagesOnItemFrames mapas)"
                                        )

                                        styled(
                                            "**Cópias:** $copies"
                                        )

                                        styled(
                                            "**Preço:** ${Emotes.Pesadelos} ${prestartResponse.totalCost} pesadelos",
                                            Emotes.PantufaCoffee
                                        )

                                        files += FileUpload.fromData(imageAsByteArray, "map.png")

                                        // This cannot be with interactivityManager because we need this to persist between Pantufa restarts
                                        actionRow(
                                            button(
                                                "approve_map:${mapRequestId.value}",
                                                "Aprovar Imagem",
                                                style = ButtonStyle.PRIMARY,
                                                emoji = Emotes.PantufaThumbsUp.toJDA()
                                            )
                                        )
                                    }
                                ).await()

                                context.reply(true) {
                                    styled(
                                        "Imagem enviada para a equipe aprovar! Quando a imagem for aprovada, você receberá os itens na sua caixa postal na `/warp correios`!"
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        fun isImageEmpty(image: BufferedImage): Boolean {
            for (x in 0 until image.width) {
                for (y in 0 until image.height) {
                    if (Color(image.getRGB(x, y), true).alpha != 0) {
                        return false
                    }
                }
            }
            return true
        }

        fun toBufferedImage(img: Image): BufferedImage {
            if (img is BufferedImage) {
                return img
            }

            // Create a buffered image with transparency
            val bimage = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB)

            // Draw the image on to the buffered image
            val bGr: Graphics2D = bimage.createGraphics()
            bGr.drawImage(img, 0, 0, null)
            bGr.dispose()

            // Return the buffered image
            return bimage
        }
    }

    enum class ResizeMethod {
        SCALE_TO_FIT,
        CONTAIN
    }

    sealed class ItemFrameImage(val x: Int, val y: Int) {
        class FrameImage(x: Int, y: Int, val image: BufferedImage) : ItemFrameImage(x, y)
        class EmptyFrame(x: Int, y: Int) : ItemFrameImage(x, y)
    }
}