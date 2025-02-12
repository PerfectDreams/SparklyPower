package net.perfectdreams.dreammapwatermarker.commands

import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.perfectdreams.dreamcash.tables.Cashes
import net.perfectdreams.dreamcore.tables.Users
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.hoverText
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.generateCommandInfo
import net.perfectdreams.dreamcore.utils.lore
import net.perfectdreams.dreamcore.utils.set
import net.perfectdreams.dreammapwatermarker.DreamMapWatermarker
import net.perfectdreams.dreammapwatermarker.DreamMapWatermarker.Companion.LOCK_MAP_CRAFT_KEY
import net.perfectdreams.dreammapwatermarker.DreamMapWatermarker.Companion.MAP_CUSTOM_OWNER_KEY
import net.perfectdreams.dreammapwatermarker.tables.PlayerPantufaPrintShopCustomMaps
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class PhotocopyCommand(val m: DreamMapWatermarker) : SparklyCommandDeclarationWrapper {
    companion object {
        private const val PANTUFA_PRINT_SHOP_MAP_COPY_PESADELOS_COST = 6L
        private val prefix
            get() = textComponent {
                append("[") {
                    color(NamedTextColor.GRAY)
                }
                append("Gráfica da ") {
                    color(NamedTextColor.LIGHT_PURPLE)
                }
                append("Gabriela") {
                    color(NamedTextColor.DARK_PURPLE)
                    decorate(TextDecoration.BOLD)
                }
                append("]") {
                    color(NamedTextColor.GRAY)
                }
            }
    }

    override fun declaration() = sparklyCommand(listOf("photocopy", "xerox")) {
        permission = "dreammapwatermarker.photocopy"

        executor = PhotocopyCommandExecutor()
    }

    inner class PhotocopyCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val action = optionalWord("requestId or action") { context, builder ->
                transaction(Databases.databaseNetwork) {
                    PlayerPantufaPrintShopCustomMaps.selectAll()
                        .where {
                            PlayerPantufaPrintShopCustomMaps.requestedBy eq context.requirePlayer().uniqueId
                        }
                        .forEach {
                            builder.suggest(it[PlayerPantufaPrintShopCustomMaps.id].toString())
                        }
                }
            }
            val copies = optionalInteger("copies")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()
            val action = args[options.action] ?: run {
                return context.sendMessage {
                    append(prefix)
                    appendSpace()
                    append(
                        generateCommandInfo(
                            "xerox",
                            mapOf(
                                "<requestId>" to "ID do pedido",
                                "[copies]" to "Quantidade de cópias (Padrão: 1)"
                            )
                        )
                    )
                }
            }
            val copies = args[options.copies] ?: 1

            val doneRequest = DreamMapWatermarker.donePendingRequests.firstOrNull { it.player == player.uniqueId }

            var info = "Player ${player.name} (${player.uniqueId}) is trying to do a map copy request!"

            // cooldown system
            if (doneRequest != null && doneRequest.requestInMillis > System.currentTimeMillis() && !player.hasPermission("dreammapwatermarker.bypasscopycooldown")) {
                val requestInMillis = doneRequest.requestInMillis - System.currentTimeMillis()
                val minutes = TimeUnit.MILLISECONDS.toMinutes(requestInMillis)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(requestInMillis) % 60

                info += " But he's at cooldown! Formatted cooldown: $minutes minutes, $seconds seconds."

                m.logger.info { info }

                return context.sendMessage {
                    append(prefix)
                    appendSpace()
                    append("§cVocê já fez uma cópia de mapa recentemente! Você deve esperar ${if (minutes == 1L) "1 minuto" else "$minutes minutos"} e ${if (seconds == 1L) "1 segundo" else "$seconds segundos"} para fazer outra.")
                }
            } else if (doneRequest != null && doneRequest.requestInMillis <= System.currentTimeMillis()) {
                info += " Player is not at cooldown anymore, so we can safely remove him from the cooldown list!"

                m.logger.info { info }

                DreamMapWatermarker.donePendingRequests.remove(doneRequest)
            }

            // Limit the quantity of copies to 64 copies
            if (copies > 32) {
                info += " But he's trying to make more than 32 copies! Bail out!"

                m.logger.info { info }

                return context.sendMessage {
                    append(prefix)
                    appendSpace()
                    append("Calma lá meu patrão, mais de 32 cópias? Você quer falir a gráfica da Gabriela?") {
                        color(NamedTextColor.RED)
                    }
                }
            }

            when (action) {
                // Check if the player is trying to cancel or accept the copy request
                "cancelar" -> return cancelRequest(context, player)

                "aceitar" -> return processRequest(context, player, copies)
            }

            // Check if the player is already making a copy request
            if (DreamMapWatermarker.pendingCopyRequests.containsKey(player.uniqueId)) {
                info += " But he's already making a copy request! Bail out!"

                m.logger.info { info }

                return context.sendMessage {
                    append(prefix)
                    appendSpace()
                    append("Você já está fazendo uma cópia de mapa! Por favor, espere até que a cópia atual seja concluída!") {
                        color(NamedTextColor.RED)
                    }
                }
            }

            // If the action is not "cancelar", then it should be a number, otherwise we should return an error message
            val requestId = action.toLongOrNull() ?: run {
                info += " But the request ID is null! Bail out!"

                m.logger.warning { info }

                return context.sendMessage {
                    append(prefix)
                    appendSpace()

                    append("Você precisa colocar o ID do pedido!") {
                        color(NamedTextColor.RED)
                    }

                    append(" Você pode encontrar o ID do pedido ao observar o item de um mapa já existente.") {
                        color(NamedTextColor.GRAY)
                    }
                }
            }

            // We should code inside a transaction block!
            // We're not storing the data inside DAO, we're interacting with the DB directly.
            transaction(Databases.databaseNetwork) {
                // Fetch the map data with its request ID
                val customMap = PlayerPantufaPrintShopCustomMaps.selectAll()
                    .where {
                        PlayerPantufaPrintShopCustomMaps.id eq requestId and (PlayerPantufaPrintShopCustomMaps.requestedBy eq player.uniqueId)
                    }
                    .limit(1)
                    .firstOrNull()

                // Check if the custom map exists
                if (customMap == null) {
                    info += " But the custom map is null and the provided ID it's ${requestId}. Bail out!"

                    m.logger.warning { info }

                    return@transaction context.sendMessage {
                        append(prefix)
                        appendSpace()
                        append("§cNão foi possível encontrar algum pedido seu com o ID §b$requestId§c!")
                        append(" Você pode encontrar o ID do pedido ao observar o item de um mapa já existente.") {
                            color(NamedTextColor.GRAY)
                        }
                    }
                }

                // If the map is not approved, it should not be copied
                if (customMap[PlayerPantufaPrintShopCustomMaps.approvedBy] == null) {
                    info += " But the custom map is not approved! Bail out!"

                    m.logger.warning { info }

                    return@transaction context.sendMessage {
                        append(prefix)
                        appendSpace()
                        append("§cVocê só pode copiar mapas que foram aprovados!")
                    }
                }

                val requesterUniqueId = customMap[PlayerPantufaPrintShopCustomMaps.requestedBy]

                // Format the array of map IDs
                val mapsIds = customMap[PlayerPantufaPrintShopCustomMaps.mapIds]
                    ?.removePrefix("[")
                    ?.removeSuffix("]")
                    ?.split(",")
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?: run {
                        info += " But the map IDs are null! Bail out!"

                        m.logger.warning { info }

                        return@transaction context.sendMessage {
                            append(prefix)
                            appendSpace()
                            append("Não foi possível encontrar os mapas gerados para este pedido!")
                        }
                    }

                m.logger.info { "Successfully got the map info and its shenanigans! Next step: Fetch player's name and its cash." }

                // Fetch the user's username.
                val requesterUserName = Users.selectAll()
                    .where {
                        Users.id eq requesterUniqueId
                    }
                    .limit(1)
                    .firstOrNull()
                    ?.get(Users.username)

                // Check if the requester username is null (just in case)
                if (requesterUserName == null) {
                    m.logger.warning { "Requester username is null while requesting a map copy! Requester UUID: $requesterUniqueId" }

                    return@transaction context.sendMessage {
                        append(prefix)
                        appendSpace()
                        append("§cNão foi possível encontrar o usuário que pediu o mapa!")
                    }
                }

                // Even if we're inside a transaction block, we need to insert another transaction here.
                val cash = Cashes.selectAll()
                    .where { Cashes.uniqueId eq player.uniqueId }
                    .limit(1)
                    .firstOrNull()
                    ?.get(Cashes.cash)

                // Decode the B64 images to BufferedImages.
                val images = customMap[PlayerPantufaPrintShopCustomMaps.mapImages].split(",")
                    .map { ImageIO.read(Base64.getDecoder().decode(it).inputStream()) }

                // Calculate the total price of the copies
                val totalPrice = (images.size * PANTUFA_PRINT_SHOP_MAP_COPY_PESADELOS_COST) * copies

                // Create a new data instance of the copy request
                DreamMapWatermarker.pendingCopyRequests[player.uniqueId] = DreamMapWatermarker.CustomMapCopyRequest(
                    player.uniqueId,
                    requestId,
                    copies,
                    System.currentTimeMillis(),
                    totalPrice,
                    mapsIds
                )

                // Check if the cash is not null and if the player has enough cash to make the copies
                if (cash == null || totalPrice > cash) {
                    m.logger.warning { "Couldn't fetch cash or the player hasn't enough cash while making a map copy request." }

                    return@transaction context.sendMessage {
                        append(prefix)
                        appendSpace()
                        append("§cVocê não tem pesadelos suficientes para fazer cópias deste mapa! (§b$totalPrice pesadelos§c)")
                    }
                }

                m.logger.info { "All steps passed successfully! sending the confirmation message to the player." }

                context.sendMessage {
                    append(prefix)
                    appendSpace()
                    append("§7Você solicitou a cópia de §b$copies §7mapas do pedido §b$requestId§7 por §b$totalPrice§7 pesadelos! Está tudo correto?")
                    appendNewline()
                    appendTextComponent {
                        content("§a[Clique aqui para confirmar]")
                        hoverText {
                            append("§7Clique aqui para confirmar a cópia de mapa!")
                        }
                        clickEvent(ClickEvent.callback ({
                            processRequest(context, player, copies)
                        }, ClickCallback.Options.builder().uses(1).build()))
                    }
                    appendSpace()
                    append("•")
                    appendSpace()
                    appendTextComponent {
                        content("§c[Clique aqui para cancelar]")
                        hoverText {
                            append("§7Clique aqui para cancelar a cópia de mapa!")
                        }
                        clickEvent(ClickEvent.callback ({
                            cancelRequest(context, player)
                        }, ClickCallback.Options.builder().uses(1).build()))
                    } // cancelar
                    appendNewline()
                    append("§7Caso não consiga clicar, §6/xerox aceitar §7ou §6/xerox cancelar§7.")
                }
            }
        }

        private fun cancelRequest(context: CommandContext, player: Player) {
            var info = "Player ${player.name} (${player.uniqueId}) is trying to cancel the map copy request!"

            if (!DreamMapWatermarker.pendingCopyRequests.containsKey(player.uniqueId)) {
                info += " But he doesn't even in the pending copy requests list. Bail out!"

                m.logger.info { info }
                return
            }

            DreamMapWatermarker.pendingCopyRequests.remove(player.uniqueId)

            m.logger.info { "Player ${player.name} (${player.uniqueId}) cancelled the map copy request!" }

            return context.sendMessage {
                append(prefix)
                appendSpace()
                append("§cCópia de mapa cancelada!")
            }
        }

        private fun processRequest(context: CommandContext, player: Player, copies: Int) {
            var info = "Player ${player.name} (${player.uniqueId}) is trying to confirm the copy request!"

            if (!DreamMapWatermarker.pendingCopyRequests.containsKey(player.uniqueId)) {
                info += " But he doesn't even in the pending copy requests list. Bail out!"

                m.logger.info { info }

                return context.sendMessage {
                    append(prefix)
                    appendSpace()
                    append("Você não está fazendo nenhuma cópia de mapa!") {
                        color(NamedTextColor.RED)
                    }
                }
            }

            val request = DreamMapWatermarker.pendingCopyRequests.remove(player.uniqueId) ?: run {
                info += " But the request is null. Bail out!"

                m.logger.warning { info }
                return
            }

            val delay = (copies * TimeUnit.MINUTES.toMillis(5)) / 64

            transaction(Databases.databaseNetwork) {
                Cashes.update({ Cashes.uniqueId eq player.uniqueId }) {
                    with(SqlExpressionBuilder) {
                        it[Cashes.cash] = Cashes.cash - request.price
                    }
                }
            }

            val maps = request.mapIds.map {
                ItemStack(Material.FILLED_MAP)
                    .lore(
                        "§7Diretamente da §dGráfica da Gabriela§7...",
                        "§7(temos os melhores preços da região!)",
                        "§7§oUm incrível mapa para você!",
                        "§7",
                        "§7Mapa feito para §a${player.name} §e(◠‿◠✿)",
                        "§7",
                        "§7ID do Pedido: §6${request.requestId}"
                    ).apply {
                        this.addUnsafeEnchantment(Enchantment.INFINITY, 1)
                        this.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                        this.meta<MapMeta> {
                            this.mapId = it
                            this.persistentDataContainer.set(LOCK_MAP_CRAFT_KEY, PersistentDataType.BYTE, 1)
                            this.persistentDataContainer.set(MAP_CUSTOM_OWNER_KEY, PersistentDataType.STRING, player.uniqueId.toString())
                            this.persistentDataContainer.set(DreamMapWatermarker.PRINT_SHOP_REQUEST_ID_KEY, request.requestId)
                        }
                    }
            }

            if (copies > 0) {
                for (i in 0 until copies) {
                    m.dreamCorreios.addItem(
                        player.uniqueId,
                        *maps.toTypedArray()
                    )
                }
            }

            m.logger.info { "Successfully generated ${request.copies} copies for ${request.requestId} custom map request! Cash cost: ${request.price}" }

            request.requestInMillis = System.currentTimeMillis() + delay

            DreamMapWatermarker.donePendingRequests.add(request)

            return context.sendMessage {
                append(prefix)
                appendSpace()
                append("§aForam feitas ${request.copies} ${if (request.copies == 1) "cópia" else "cópias"} de mapa do pedido §b${request.requestId}§a por §b${request.price} §apesadelos! §7(Total de mapas: ${request.mapIds.size * request.copies})")
            }
        }
    }
}