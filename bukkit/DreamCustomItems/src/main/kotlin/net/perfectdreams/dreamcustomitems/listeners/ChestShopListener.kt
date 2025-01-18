package net.perfectdreams.dreamcustomitems.listeners

import com.Acrobot.ChestShop.Events.ItemParseEvent
import com.Acrobot.ChestShop.Events.ItemStringQueryEvent
import net.perfectdreams.dreamcustomitems.items.SparklyItemsRegistry
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class ChestShopListener : Listener {
    // We need both
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onParse(e: ItemParseEvent) {
        val sparklyItem = SparklyItemsRegistry.items.values.firstOrNull { it.data.chestShopName == e.itemString }
        if (sparklyItem != null) {
            e.item = sparklyItem.createItemStack()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onQuery(e: ItemStringQueryEvent) {
        val sparklyItem = SparklyItemsRegistry.getMatchedItem(e.item) ?: return
        val chestShopName = sparklyItem.data.chestShopName ?: return
        e.itemString = chestShopName
    }
}