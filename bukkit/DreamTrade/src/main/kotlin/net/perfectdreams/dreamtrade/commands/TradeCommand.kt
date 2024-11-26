package net.perfectdreams.dreamtrade.commands

import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendCommand
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.canHoldItem
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamtrade.DreamTrade
import net.perfectdreams.dreamtrade.structures.TradeSlots
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID

class TradeCommand(val m: DreamTrade) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("trade", "troca")) {
        // /trade ativar - enable trade requests
        subcommand(listOf("ativar", "enable")) {
            executor = TradeEnableCommandExecutor()
        }

        // /trade desativar - disable trade requests
        subcommand(listOf("desativar", "disable")) {
            executor = TradeDisableCommandExecutor()
        }

        // /trade convidar <player> - invite a player to trade
        subcommand(listOf("convidar", "invite")) {
            executor = TradeInviteCommandExecutor()
        }

        // /trade aceitar - accept the trade request and opens the trade inventory
        subcommand(listOf("aceitar", "accept")) {
            executor = TradeAcceptCommandExecutor()
        }

        // /trade recusar - deny the trade request
        subcommand(listOf("recusar", "deny")) {
            executor = TradeDenyCommandExecutor()
        }

        // /trade cancelar - cancel the trade (after the trade is accepted)
        subcommand(listOf("cancelar", "cancel")) {
            executor = TradeCancelCommandExecutor()
        }

        // /trade abrir - open the trade inventory if the player closed it
        subcommand(listOf("abrir", "open")) {
            executor = TradeOpenCommandExecutor()
        }
    }

    inner class TradeEnableCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            if (player.uniqueId in m.playersWithDisabledTrade) {
                m.playersWithDisabledTrade.remove(player.uniqueId)

                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.GREEN)
                    append("Você ativou os pedidos de troca! Agora você pode receber pedidos de outros jogadores. Para desativar novamente, use o comando ")
                    appendCommand("/trade desativar")
                    append(". Para convidar outros jogadores para trocar, use o comando ")
                    appendCommand("/trade convidar <player>")
                    append("!")
                }
            } else {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você já está com os pedidos de troca ativados!")
                }
            }
        }
    }

    inner class TradeDisableCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            if (player.uniqueId !in m.playersWithDisabledTrade) {
                m.playersWithDisabledTrade.add(player.uniqueId)

                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.GREEN)
                    append("Você desativou os pedidos de troca! Agora você não pode receber pedidos de troca de outros jogadores. Para ativar novamente, use o comando ")
                    appendCommand("/trade ativar")
                    append(".")
                }
            } else {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você já está com os pedidos de troca desativados!")
                }
            }
        }
    }

    inner class TradeInviteCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val player = word("player") { context, builder ->
                Bukkit.getOnlinePlayers().map { it.name }.forEach {
                    builder.suggest(it)
                }
            }
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val playerOne = context.requirePlayer()
            val playerTwo = Bukkit.getPlayer(args[options.player]) ?: run {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Este jogador não está online!")
                }
            }

            if (playerOne == playerTwo) {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não pode convidar você mesmo para trocar!")
                }
            }

            if (playerOne.uniqueId in m.playersWithDisabledTrade) {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não pode convidar jogadores para trocar pois você desativou os pedidos de troca! Para ativar novamente, use o comando ")
                    appendCommand("/trade ativar")
                    append(".")
                }
            }

            if (playerTwo.uniqueId in m.playersWithDisabledTrade) {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não pode convidar este jogador para trocar pois ele desativou os pedidos de troca!")
                }
            }

            if (m.pendingTrades.containsKey(playerOne.uniqueId)) {
                val playerInvited = Bukkit.getPlayer(m.pendingTrades[playerOne.uniqueId]!!)!!

                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você já convidou alguém para trocar! Aguarde a resposta de ${playerInvited.name}.")
                }
            }

            if (m.pendingTrades.containsValue(playerTwo.uniqueId)) {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Este jogador já foi convidado para trocar por alguém! Aguarde o término ou convide outra pessoa.")
                }
            }

            m.pendingTrades[playerOne.uniqueId] = playerTwo.uniqueId

            context.sendMessage {
                append(DreamTrade.prefix)
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Você convidou o jogador ")
                color(NamedTextColor.AQUA)
                append(playerTwo.name)
                color(NamedTextColor.GREEN)
                append(" para trocar! Aguarde a resposta de ")
                color(NamedTextColor.AQUA)
                append(playerTwo.name)
                color(NamedTextColor.GREEN)
                append(".")
            }

            playerTwo.sendMessage(textComponent {
                append(DreamTrade.prefix)
                appendSpace()
                color(NamedTextColor.GREEN)
                append(playerOne.name)
                append(" te convidou para trocar! Para aceitar, use o comando ")
                appendCommand("/trade aceitar")
                append(".")
            })
        }
    }

    inner class TradeAcceptCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            if (!m.pendingTrades.containsValue(player.uniqueId)) {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não foi convidado para trocar!")
                }
            }

            val playerOneUniqueId = m.pendingTrades.filterValues { it == player.uniqueId }.keys.first()

            val playerOne = Bukkit.getPlayer(playerOneUniqueId) ?: run {
                m.pendingTrades.remove(playerOneUniqueId)

                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("O jogador que te convidou para trocar não está mais online!")
                }
            }

            m.pendingTrades.remove(playerOneUniqueId)

            val inventory = m.createTradeInventory(playerOne, player)

            m.activeTrades[Pair(playerOne.uniqueId, player.uniqueId)] = inventory

            player.openInventory(inventory)
            playerOne.openInventory(inventory)

            context.sendMessage {
                append(DreamTrade.prefix)
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Você aceitou o pedido de troca de ")
                append(playerOne.name)
                append("!")
            }

            playerOne.sendMessage(textComponent {
                append(DreamTrade.prefix)
                appendSpace()
                color(NamedTextColor.GREEN)
                append(player.name)
                append(" aceitou o seu pedido de troca!")
            })
        }
    }

    inner class TradeDenyCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            if (!m.pendingTrades.containsValue(player.uniqueId)) {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não foi convidado para trocar!")
                }
            }

            val playerOneUniqueId = m.pendingTrades.filterValues { it == player.uniqueId }.keys.first()

            val playerOne = Bukkit.getPlayer(playerOneUniqueId) ?: run {
                m.pendingTrades.remove(playerOneUniqueId)

                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("O jogador que te convidou para trocar não está mais online!")
                }
            }

            m.pendingTrades.remove(playerOneUniqueId)

            context.sendMessage {
                append(DreamTrade.prefix)
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Você recusou o pedido de troca de ")
                append(playerOne.name)
                append("!")
            }
        }
    }

    inner class TradeCancelCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            val (tradeInventory, tradePair) = m.getTradeDetails(player.uniqueId)

            if (tradeInventory == null || tradePair == null) {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não está em uma troca!")
                }
            }

            val playerOne = Bukkit.getPlayer(tradePair.first) ?: run {
                val playerOneItems = TradeSlots.PLAYER_ONE_AVAILABLE_SLOTS.map { tradeInventory.getItem(it) ?: ItemStack(Material.AIR) }

                m.activeTrades.remove(tradePair)

                playerOneItems.forEach {
                    m.correios.addItem(tradePair.first, it)
                }

                return
            }

            val playerTwo = Bukkit.getPlayer(tradePair.second) ?: run {
                val playerTwoItems = TradeSlots.PLAYER_TWO_AVAILABLE_SLOTS.map { tradeInventory.getItem(it) ?: ItemStack(Material.AIR) }

                m.activeTrades.remove(tradePair)

                playerTwoItems.forEach {
                    m.correios.addItem(tradePair.second, it)
                }

                return
            }

            val playerOneItems = TradeSlots.PLAYER_ONE_AVAILABLE_SLOTS.map { playerOne.inventory.getItem(it) ?: ItemStack(Material.AIR) }
            val playerTwoItems = TradeSlots.PLAYER_TWO_AVAILABLE_SLOTS.map { playerTwo.inventory.getItem(it) ?: ItemStack(Material.AIR) }

            var sentToPlayerOneCorreios = false
            var sentToPlayerTwoCorreios = false

            playerOneItems.forEach { item ->
                if (playerOne.inventory.canHoldItem(item)) {
                    playerOne.inventory.addItem(item)
                } else {
                    m.correios.addItem(playerOne, item)
                    sentToPlayerOneCorreios = true
                }
            }

            playerTwoItems.forEach { item ->
                if (playerTwo.inventory.canHoldItem(item)) {
                    playerTwo.inventory.addItem(item)
                } else {
                    m.correios.addItem(playerTwo, item)
                    sentToPlayerTwoCorreios = true
                }
            }

            m.activeTrades.remove(tradePair)

            playerOne.closeInventory()
            playerTwo.closeInventory()

            playerOne.sendMessage(textComponent {
                append(DreamTrade.prefix)
                appendSpace()
                color(NamedTextColor.RED)
                append("A troca foi cancelada! Os itens colocados na troca foram devolvidos à você.")
                if (sentToPlayerOneCorreios) {
                    append(" Como seu inventário estava cheio, alguns itens precisaram ser enviados para seus correios. Visite ")
                    appendCommand("/warp correios")
                    append(" para pegá-los!")
                }
            })

            playerTwo.sendMessage(textComponent {
                append(DreamTrade.prefix)
                appendSpace()
                color(NamedTextColor.RED)
                append("A troca foi cancelada! Os itens colocados na troca foram devolvidos à você.")
                if (sentToPlayerTwoCorreios) {
                    append(" Como seu inventário estava cheio, alguns itens precisaram ser enviados para seus correios. Visite ")
                    appendCommand("/warp correios")
                    append(" para pegá-los!")
                }
            })
        }
    }

    inner class TradeOpenCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            val (tradeInventory, tradePair) = m.getTradeDetails(player.uniqueId)

            if (tradeInventory == null || tradePair == null) {
                return context.sendMessage {
                    append(DreamTrade.prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não está em uma troca!")
                }
            }

            player.openInventory(tradeInventory)

            context.sendMessage {
                append(DreamTrade.prefix)
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Você abriu o HUD de troca!")
            }
        }
    }
}