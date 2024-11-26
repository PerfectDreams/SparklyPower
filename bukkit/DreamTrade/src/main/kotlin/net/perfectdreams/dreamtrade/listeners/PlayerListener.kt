package net.perfectdreams.dreamtrade.listeners

import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.canHoldItem
import net.perfectdreams.dreamtrade.DreamTrade
import net.perfectdreams.dreamtrade.structures.TradeSlots
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class PlayerListener(val m: DreamTrade) : Listener {

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // remove the player of the pending trades if he quits
        if (m.pendingTrades.contains(event.player.uniqueId)) {
            m.pendingTrades.remove(event.player.uniqueId)
            return
        }

        // remove the player of the disabled trades if he quits
        if (m.playersWithDisabledTrade.contains(event.player.uniqueId)) {
            m.playersWithDisabledTrade.remove(event.player.uniqueId)
            return
        }

        // remove the player of the active trades if he quits
        // add his items to correios because we cannot add to his inventory anymore
        val (tradeInventory, tradePair) = m.getTradeDetails(event.player.uniqueId)

        if (tradeInventory == null || tradePair == null) return

        val onlinePlayerUniqueId = if (tradePair.first == event.player.uniqueId) tradePair.second else tradePair.first
        val onlinePlayerSlots = if (onlinePlayerUniqueId == tradePair.first) TradeSlots.PLAYER_ONE_AVAILABLE_SLOTS else TradeSlots.PLAYER_TWO_AVAILABLE_SLOTS
        val onlinePlayerItems = onlinePlayerSlots.mapNotNull { tradeInventory.getItem(it) }
        val onlinePlayer = Bukkit.getPlayer(onlinePlayerUniqueId) ?: run {
            m.correios.addItem(onlinePlayerUniqueId, *onlinePlayerItems.toTypedArray())
            return
        }

        val quittedPlayerUniqueId = event.player.uniqueId
        val quittedPlayerSlots = if (quittedPlayerUniqueId == tradePair.first) TradeSlots.PLAYER_ONE_AVAILABLE_SLOTS else TradeSlots.PLAYER_TWO_AVAILABLE_SLOTS
        val quittedPlayerItems = quittedPlayerSlots.mapNotNull { tradeInventory.getItem(it) }

        onlinePlayerItems.forEach {
            if (onlinePlayer.inventory.canHoldItem(it)) {
                onlinePlayer.inventory.addItem(it)
            } else {
                m.correios.addItem(onlinePlayer, it)
            }
        }

        quittedPlayerItems.forEach {
            m.correios.addItem(quittedPlayerUniqueId, it)
        }

        m.activeTrades.remove(tradePair)

        onlinePlayer.closeInventory()

        onlinePlayer.sendMessage(textComponent {
            append(DreamTrade.prefix)
            appendSpace()
            append("§cO jogador ${event.player.name} saiu do servidor, a troca foi cancelada! Seus itens foram devolvidos.")
        })
    }
}