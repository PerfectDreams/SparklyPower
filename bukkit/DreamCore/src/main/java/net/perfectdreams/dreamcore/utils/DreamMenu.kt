package net.perfectdreams.dreamcore.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.perfectdreams.dreamcore.utils.extensions.meta
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Uma classe para criar menus de uma maneira simples e fácil!
 */
class DreamMenu(
	val size: Int,
	val title: Component,
	val cancelItemMovement: Boolean,
	val slots: MutableList<DreamMenuSlot>
) {
	fun createInventory(): Inventory {
		val inventory = Bukkit.createInventory(DreamMenuHolder(this), size, title)
		refreshInventory(inventory)

		return inventory
	}

	fun sendTo(player: Player) {
		player.openInventory(createInventory())
	}

	fun setSlot(inventory: Inventory, x: Int, y: Int, block: DreamMenuSlotBuilder.() -> Unit) = setSlot(inventory, x + (y * 9), block)

	fun setSlot(inventory: Inventory, index: Int, block: DreamMenuSlotBuilder.() -> Unit) {
		val slot = DreamMenuSlotBuilder(index).apply(block).build()
		slots.removeIf { it.position == index }
		slots.add(slot)
		refreshInventory(inventory)
	}

	fun refreshInventory(inventory: Inventory) {
		slots.forEach {
			if (it.item != null)
				inventory.setItem(it.position, it.item)
		}
		repeat(size) {
			val item = inventory.getItem(it)
			if (item == null) {
				inventory.setItem(
					it,
					ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
						.meta<ItemMeta> {
							displayName(Component.empty())
							setCustomModelData(2)
							isHideTooltip = true // Do not show the item's tooltip!
						}
				)
			}
		}
	}

	class DreamMenuSlot(val position: Int, val item: ItemStack?, val onClick: (ClickContext.(HumanEntity) -> Unit)?)

	class DreamMenuHolder(val menu: DreamMenu) : InventoryHolder {
		override fun getInventory(): Inventory {
			throw UnsupportedOperationException()
		}
	}
}

fun createMenu(size: Int, title: String, block: DreamMenuBuilder.() -> Unit) = DreamMenuBuilder(size, LegacyComponentSerializer.legacySection().deserialize(title)).apply(block).build()

fun createMenu(size: Int, title: TextComponent, block: DreamMenuBuilder.() -> Unit) = DreamMenuBuilder(size, title).apply(block).build()

class DreamMenuBuilder(val size: Int, val title: Component) {
	private val slots = mutableListOf<DreamMenu.DreamMenuSlot>()
	var cancelItemMovement: Boolean = true

	fun slot(x: Int, y: Int, block: DreamMenuSlotBuilder.() -> Unit) = slot(x + (y * 9), block)

	fun slot(index: Int, block: DreamMenuSlotBuilder.() -> Unit) {
		val slot = DreamMenuSlotBuilder(index).apply(block).build()
		slots.add(slot)
	}

	fun build(): DreamMenu = DreamMenu(size, title, cancelItemMovement, slots)
}

class DreamMenuSlotBuilder(val index: Int) {
	var item: ItemStack? = null
	private var onClick: (ClickContext.(HumanEntity) -> Unit)? = null

	fun onClick(callback: ClickContext.(HumanEntity) -> Unit) {
		onClick = callback
	}

	fun build(): DreamMenu.DreamMenuSlot {
		return DreamMenu.DreamMenuSlot(index, item, onClick)
	}
}

class DreamMenuListener : Listener {
	@EventHandler
	fun onMove(e: InventoryClickEvent) {
		// We block EVERYTHING
		// No, we don't care that the user cannot manipulate their inventory while the menu is open
		// JUST CLOSE IT BEFORE TRYING TO MESS WITH YOUR INVENTORY
		// Sorry, but trying to be "nice" just causes a lot of dupe issues
		val inventoryHolder = e.inventory.holder as? DreamMenu.DreamMenuHolder
		val clickedInventoryHolder = e.clickedInventory?.holder as? DreamMenu.DreamMenuHolder

		val targetInventoryHolder = inventoryHolder ?: clickedInventoryHolder ?: return

		val dreamMenu = targetInventoryHolder.menu

		if (dreamMenu.cancelItemMovement)
			e.isCancelled = true

		// Are we clicking on the menu? If not, bail out!
		// This avoids bugs with users clicking on their inventory triggering actions on the menu
		if (e.clickedInventory?.holder != targetInventoryHolder)
			return

		val clickedSlot = e.slot
		val slot = dreamMenu.slots.firstOrNull { it.position == clickedSlot }

		if (slot != null) {
			// Technically the upper inventory will ALWAYS BE a DreamMenu, no exceptions (I hope!)
			val clickContext = ClickContext(dreamMenu, e.inventory)
			slot.onClick?.invoke(clickContext, e.whoClicked)
		}
	}

	@EventHandler
	fun onMove(e: InventoryDragEvent) {
		val clickedInventoryHolder = e.inventory.holder

		if (clickedInventoryHolder !is DreamMenu.DreamMenuHolder)
			return

		val dreamMenu = clickedInventoryHolder.menu

		if (dreamMenu.cancelItemMovement)
			e.isCancelled = true
	}

	@EventHandler
	fun onItemMove(e: InventoryMoveItemEvent) {
		// This is used to block the following:
		// Click on your inventory (the player's inventory) twice on a item
		// This drags the item from the DreamMenu to your inventory
		//
		// We also block drag from the player's inventory to DreamMenu and vice-versa

		// Calling CraftInventory.getHolder() is very intensive, and InventoryMoveItemEvent is called for hoppers too!
		// To avoid a big performance impact, we will check if the type is a CHEST (which doesn't need to call the holder) and THEN we will try calling the holder
		// This way we can skip a very resource intensive check, sweet!
		val destinationHolder = if (e.destination.type == InventoryType.CHEST) e.destination.getHolder(false) else null
		val sourceHolder = if (e.source.type == InventoryType.CHEST) e.source.getHolder(false) else null

		// If the destination or the source is a DreamMenuHolder...
		val dreamMenuHolder = if (destinationHolder is DreamMenu.DreamMenuHolder)
			destinationHolder
		else if (sourceHolder is DreamMenu.DreamMenuHolder)
			sourceHolder
		else return

		// The item click is already handled by InventoryClickEvent so we don't need to care about this
		val dreamMenu = dreamMenuHolder.menu

		if (dreamMenu.cancelItemMovement)
			e.isCancelled = true
	}
}

class ClickContext(val menu: DreamMenu, val inventory: Inventory) {
	fun setSlot(x: Int, y: Int, block: DreamMenuSlotBuilder.() -> Unit) = setSlot(x + (y * 9), block)

	fun setSlot(index: Int, block: DreamMenuSlotBuilder.() -> Unit) {
		menu.setSlot(inventory, index, block)
	}
}