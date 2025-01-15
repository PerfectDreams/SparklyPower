package net.perfectdreams.dreamtreeassist

import me.ryanhamshire.GriefPrevention.GriefPrevention
import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamtreeassist.listeners.ItemDropListener
import net.perfectdreams.dreamtreeassist.listeners.PlayerListener
import net.perfectdreams.dreamtreeassist.utils.BlockLocation
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.Item
import org.bukkit.event.Listener
import org.bukkit.metadata.FixedMetadataValue
import java.util.*

class DreamTreeAssist : KotlinPlugin(), Listener {
	companion object {
		val SAPLINGS = setOf(
			Material.ACACIA_SAPLING,
			Material.SPRUCE_SAPLING,
			Material.DARK_OAK_SAPLING,
			Material.BIRCH_SAPLING,
			Material.OAK_SAPLING,
			Material.JUNGLE_SAPLING,
			Material.CHERRY_SAPLING,
			Material.PALE_OAK_SAPLING
		)

		val WORLDS = setOf(
			"world",
			"Survival2"
		)
	}

	val trackedSaplings = mutableListOf<UUID>()
	val placedLogs = mutableSetOf<BlockLocation>()

	override fun softEnable() {
		super.softEnable()

		registerEvents(PlayerListener(this))
		registerEvents(ItemDropListener(this))

		launchMainThread {
			while (true) {
				val itemsToBeRemoved = mutableSetOf<UUID>()

				for (droppedItemUniqueId in trackedSaplings) {
					val droppedItem = Bukkit.getEntity(droppedItemUniqueId) as? Item
					if (droppedItem == null) {
						// Item does not exist! Moving on...
						itemsToBeRemoved.add(droppedItemUniqueId)
						continue
					}

					if (droppedItem.ticksLived >= (20 * 15) && !droppedItem.hasMetadata("checkedTree")) { // 15s
						val blockAtDrop = droppedItem.location.block

						droppedItem.setMetadata("checkedTree", FixedMetadataValue(this@DreamTreeAssist, true))

						if (blockAtDrop.type == Material.AIR && (blockAtDrop.getRelative(BlockFace.DOWN).type == Material.GRASS_BLOCK || blockAtDrop.getRelative(BlockFace.DOWN).type == Material.DIRT)) {
							val claim = GriefPrevention.instance.dataStore.getClaimAt(blockAtDrop.location, false, null)

							if (claim != null) // Do not transform into sapling if it is a protected terrain
								continue

							blockAtDrop.type = droppedItem.itemStack.type

							if (droppedItem.itemStack.amount == 1)
								droppedItem.remove()
							else
								droppedItem.itemStack.amount -= 1
						}

						itemsToBeRemoved.add(droppedItemUniqueId)
					}
				}

				trackedSaplings.removeAll(itemsToBeRemoved)

				delayTicks(20L)
			}
		}
	}
}