package net.perfectdreams.dreamchestshopstuff.listeners

import com.Acrobot.Breeze.Utils.InventoryUtil
import com.Acrobot.ChestShop.Commands.AccessToggle
import com.Acrobot.ChestShop.Events.PreShopCreationEvent
import com.Acrobot.ChestShop.Events.PreTransactionEvent
import com.Acrobot.ChestShop.Events.TransactionEvent
import com.Acrobot.ChestShop.Listeners.Modules.StockCounterModule
import com.Acrobot.ChestShop.Signs.ChestShopSign
import com.Acrobot.ChestShop.Signs.ChestShopSign.ITEM_LINE
import com.Acrobot.ChestShop.Utils.uBlock
import net.md_5.bungee.api.ChatColor
import net.perfectdreams.dreamchestshopstuff.DreamChestShopStuff
import net.perfectdreams.dreamcore.utils.SparklyNamespacedKey
import net.perfectdreams.dreamcore.utils.extensions.rightClick
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.awt.Color

class ColorizeShopSignsListener(val m: DreamChestShopStuff) : Listener {
    companion object {
        private val COLOR_LOGO_RED = ChatColor.of(Color(237, 46, 22))
        private val COLOR_LOGO_AQUA = ChatColor.of(Color(1, 235, 247))
    }

    val RAINBOW_NAME_SHOP_SIGN_KEY = SparklyNamespacedKey("rainbow_name_shop_sign")

    // We can't ignore cancelled events because ChestShop may have already cancelled our event here
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        if (!event.rightClick)
            return

        val clickedBlock = event.clickedBlock ?: return
        val item = event.item ?: return

        if (!(item.type == Material.WHITE_WOOL && item.itemMeta?.hasCustomModelData() == true && item.itemMeta?.customModelData == 1))
            return

        // Checks from PlayerInteract
        val sign = clickedBlock.state as? Sign ?: return
        if (!ChestShopSign.isValid(sign))
            return

        val player = event.player

        if (!(!AccessToggle.isIgnoring(player) && ChestShopSign.canAccess(player, sign) && !ChestShopSign.isAdminShop(sign)))
            return

        if (sign.persistentDataContainer.has(RAINBOW_NAME_SHOP_SIGN_KEY, PersistentDataType.BYTE)) {
            player.sendMessage("§cO poder da lã arco-íris já está incorporado a sua placa de loja!")
            return
        }
        sign.persistentDataContainer.set(RAINBOW_NAME_SHOP_SIGN_KEY, PersistentDataType.BYTE, 1)

        val chestShopInventory = uBlock.findConnectedContainer(sign).inventory

        updateSignColorBasedOnStockQuantity(sign, chestShopInventory)

        item.amount--

        player.sendMessage("§aO poder da lã arco-íris foi incorporado a sua placa de loja, deixando o seu nome na placa colorido!")
    }

    // https://github.com/ChestShop-authors/ChestShop-3/issues/503
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPreShopCreation(event: PreShopCreationEvent) {
        val isAdminShop = ChestShopSign.isAdminShop(ChatColor.stripColor(event.getSignLine(0)))
        if (isAdminShop) {
            event.sign.color = DyeColor.BLUE
            event.signLines = buildColoredSign(
                event.signLines.toList(),
                true,
                true,
                event.sign.persistentDataContainer.has(RAINBOW_NAME_SHOP_SIGN_KEY, PersistentDataType.BYTE),
                event.sign.color
            ).toTypedArray()
            return
        }

        // Admin Shops do not have an inventory, so let's just... not do that
        val itemTradedByShop = StockCounterModule.determineItemTradedByShop(event.getSignLine(ITEM_LINE))
        val chestShopInventory = uBlock.findConnectedContainer(event.sign).inventory
        val numTradedItemsInChest = InventoryUtil.getAmount(itemTradedByShop, chestShopInventory)
        val hasSpaceInChest = InventoryUtil.fits(itemTradedByShop, chestShopInventory)

        event.sign.color = DyeColor.BLUE
        event.signLines = buildColoredSign(
            event.signLines.toList(),
            numTradedItemsInChest != 0,
            hasSpaceInChest,
            event.sign.persistentDataContainer.has(RAINBOW_NAME_SHOP_SIGN_KEY, PersistentDataType.BYTE),
            event.sign.color
        ).toTypedArray()
        event.sign.update()
        return
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val inventoryLocation = event.inventory.location
        if (inventoryLocation == null || !ChestShopSign.isShopBlock(inventoryLocation.block))
            return

        for (shopSign in uBlock.findConnectedShopSigns(event.inventory.holder)) {
            if (ChestShopSign.isAdminShop(shopSign))
                return

            updateSignColorBasedOnStockQuantity(shopSign, event.inventory)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onTransaction(event: PreTransactionEvent) {
        // Used to retroactively apply the new stock counter to old signs
        if (event.transactionOutcome == PreTransactionEvent.TransactionOutcome.NOT_ENOUGH_STOCK_IN_CHEST) {
            for (shopSign in uBlock.findConnectedShopSigns(event.ownerInventory.holder)) {
                updateSignColorBasedOnStockQuantity(shopSign, event.ownerInventory)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onTransaction(event: TransactionEvent) {
        if (ChestShopSign.isAdminShop(event.sign)) {
            // Admin Shops return null for the "event.ownerInventory.holder", so will only update the sign that is provided in the event
            updateSignColorBasedOnStockQuantity(event.sign, event.ownerInventory)
        } else {
            for (shopSign in uBlock.findConnectedShopSigns(event.ownerInventory.holder)) {
                updateSignColorBasedOnStockQuantity(shopSign, event.ownerInventory)
            }
        }
    }

    // From the StockCounterModule class
    private fun updateSignColorBasedOnStockQuantity(sign: Sign, chestShopInventory: Inventory?) {
        val isAdminShop = ChestShopSign.isAdminShop(ChatColor.stripColor(sign.getLine(0)))
        if (!isAdminShop && chestShopInventory != null) {
            val itemTradedByShop: ItemStack = StockCounterModule.determineItemTradedByShop(sign) ?: return
            val numTradedItemsInChest: Int = InventoryUtil.getAmount(itemTradedByShop, chestShopInventory)
            val hasSpaceInChest = InventoryUtil.fits(itemTradedByShop, chestShopInventory)

            // Retroactively apply the blue dye on old signs
            if (sign.getLine(0).startsWith("§1")) {
                sign.color = DyeColor.BLUE
            }

            buildColoredSign(
                (0 until 4).map { sign.getLine(it) },
                numTradedItemsInChest != 0,
                hasSpaceInChest,
                sign.persistentDataContainer.has(RAINBOW_NAME_SHOP_SIGN_KEY, PersistentDataType.BYTE),
                sign.color
            ).forEachIndexed { index, s ->
                sign.setLine(index, s)
            }

            // TODO: We could check if the sign really needs to be updated, instead of calling the update method every time
            sign.update(true)
            return
        }

        buildColoredSign(
            (0 until 4).map { sign.getLine(it) },
            true,
            true,
            sign.persistentDataContainer.has(RAINBOW_NAME_SHOP_SIGN_KEY, PersistentDataType.BYTE),
            sign.color
        ).forEachIndexed { index, s ->
            sign.setLine(index, s)
        }

        // TODO: We could check if the sign really needs to be updated, instead of calling the update method every time
        sign.update(true)
    }

    private fun buildColoredSign(
        lines: List<String>,
        hasStock: Boolean,
        hasSpaceInChest: Boolean,
        hasRainbowName: Boolean,
        color: DyeColor?
    ): List<String> {
        val newLines = lines.map { ChatColor.stripColor(it) }
            .toMutableList()
        val ownerLine = newLines[0]

        // This checks if the sign has stock OR if the sign is not selling items, and then does the same thing for buying items
        // Useful because a sign may have stock
        if ((!hasStock || !newLines[2].contains("B")) && (!hasSpaceInChest || !newLines[2].contains("S"))) {
            return newLines.map {
                "§c$it"
            }
        }

        // Items in the chest, update it to be our custom ChestShop sign color!
        // Owner
        if (ChestShopSign.isAdminShop(ownerLine)) {
            newLines[0] = "${COLOR_LOGO_RED}§lSparkly${COLOR_LOGO_AQUA}§lShop"
        } else {
            if (hasRainbowName) {
                newLines[0] = rainbowify(newLines[0])
            } else {
                // This will use the sign's "sign.color" color!
                if (color == DyeColor.BLUE) {
                    // Because this blue is more vibrant, idk why
                    newLines[0] = "§1${newLines[0]}"
                } else {
                    newLines[0] = newLines[0]
                }
            }
        }

        // Quantity
        newLines[1] = "§0" + newLines[1]

        // Price
        newLines[2] = newLines[2]

        if (newLines[2].contains("B")) {
            if (hasStock) {
                newLines[2] = newLines[2].replace("B", "§aB")
            } else {
                newLines[2] = newLines[2].replace("B", "§cB")
            }
        }

        if (newLines[2].contains("S")) {
            if (hasSpaceInChest) {
                newLines[2] = newLines[2].replace("S", "§4S")
            } else {
                newLines[2] = newLines[2].replace("S", "§cS")
            }
        }

        newLines[2] = newLines[2].replace(":", "§0:")

        // Item
        newLines[3] = "§9" + newLines[3]

        return newLines
    }

    private fun rainbowify(str: String): String {
        var h = 0.0f
        val s = 1f
        val b = 1f

        val steps = 1f / str.length

        var rainbowifiedText = ""
        for (c in str) {
            rainbowifiedText += ChatColor.of(Color.getHSBColor(h, s, b))
            rainbowifiedText += c
            h += steps
        }

        return rainbowifiedText
    }
}