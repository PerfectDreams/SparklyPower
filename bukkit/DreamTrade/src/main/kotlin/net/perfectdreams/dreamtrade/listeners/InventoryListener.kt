package net.perfectdreams.dreamtrade.listeners

import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamtrade.DreamTrade
import net.perfectdreams.dreamtrade.structures.TradeSlots
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class InventoryListener(val m: DreamTrade) : Listener {
    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked
        val inventory = e.clickedInventory ?: return

        val (tradeInventory, tradePair) = m.getTradeDetails(player.uniqueId)

        if (tradeInventory == null || tradePair == null) return

        if (e.isShiftClick)
            e.isCancelled = true

        if (inventory != tradeInventory) return

        val (playerOneUniqueId, playerTwoUniqueId) = tradePair

        val playerOneIsReady = e.inventory.getItem(TradeSlots.PLAYER_ONE_CONFIRMATION)?.type == Material.GREEN_CONCRETE
        val playerTwoIsReady = e.inventory.getItem(TradeSlots.PLAYER_TWO_CONFIRMATION)?.type == Material.GREEN_CONCRETE

        if (isInvalidSlotPlayer(e.slot, player.uniqueId == playerOneUniqueId))
            e.isCancelled = true

        when (player.uniqueId) {
            playerOneUniqueId -> {
                if (playerOneIsReady)
                    e.isCancelled = true

                if (e.slot == TradeSlots.PLAYER_ONE_CONFIRMATION) {
                    toggleReadyState(e, inventory, TradeSlots.PLAYER_ONE_CONFIRMATION, !playerOneIsReady)

                    if (playerTwoIsReady) m.processTrade(tradePair, inventory)
                }

                if (e.slot in TradeSlots.PLAYER_ONE_SONECAS_ROW && !playerOneIsReady)
                    m.handleSonecas(e, true)
            }

            playerTwoUniqueId -> {
                if (playerTwoIsReady)
                    e.isCancelled = true

                if (e.slot == TradeSlots.PLAYER_TWO_CONFIRMATION) {
                    toggleReadyState(e, inventory, TradeSlots.PLAYER_TWO_CONFIRMATION, !playerTwoIsReady)

                    if (playerOneIsReady)
                        m.processTrade(tradePair, inventory)
                }

                if (e.slot in TradeSlots.PLAYER_TWO_SONECAS_ROW && !playerTwoIsReady)
                    m.handleSonecas(e, false)
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        val player = e.whoClicked
        val inventory = e.inventory

        val (tradeInventory, tradePair) = m.getTradeDetails(player.uniqueId)

        if (tradeInventory == null || tradePair == null) return

        if (inventory != tradeInventory) return

        e.isCancelled = true
    }

    private fun isInvalidSlotPlayer(slot: Int, isPlayerOne: Boolean): Boolean {
        return when {
            slot in TradeSlots.DECORATION_SLOTS -> true
            isPlayerOne && (slot in TradeSlots.PLAYER_TWO_AVAILABLE_SLOTS || slot in TradeSlots.PLAYER_TWO_SONECAS_ROW || slot == TradeSlots.PLAYER_TWO_CONFIRMATION) -> true
            !isPlayerOne && (slot in TradeSlots.PLAYER_ONE_AVAILABLE_SLOTS || slot in TradeSlots.PLAYER_ONE_SONECAS_ROW || slot == TradeSlots.PLAYER_ONE_CONFIRMATION) -> true
            else -> false
        }
    }

    private fun toggleReadyState(e: InventoryClickEvent, inventory: Inventory, confirmationSlot: Int, isReady: Boolean) {
        e.isCancelled = true

        val material = if (isReady) Material.GREEN_CONCRETE else Material.RED_CONCRETE

        inventory.setItem(confirmationSlot, ItemStack(material).meta<ItemMeta> {
            displayName(textComponent {
                color(if (isReady) NamedTextColor.GREEN else NamedTextColor.RED)
                append(if (isReady) "Pronto!" else "Não está pronto!")
            })
        })
    }
}