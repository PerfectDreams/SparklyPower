package net.perfectdreams.dreamcustomitems.listeners

import me.ryanhamshire.GriefPrevention.GriefPrevention
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.extensions.rightClick
import net.perfectdreams.dreamcustomitems.DreamCustomItems
import net.perfectdreams.dreamcustomitems.holders.CustomItemRecipeHolder
import net.perfectdreams.dreamcustomitems.holders.MicrowaveHolder
import net.perfectdreams.dreamcustomitems.holders.SuperFurnaceHolder
import net.perfectdreams.dreamcustomitems.items.Microwave
import net.perfectdreams.dreamcustomitems.items.SuperFurnace
import net.perfectdreams.dreamcustomitems.items.TrashCan
import net.perfectdreams.dreamcustomitems.utils.CustomItems
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.block.Skull
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ExplosionPrimeEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.logging.Level

class CustomHeadsListener(val m: DreamCustomItems) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCustomItemPlace(e: BlockPlaceEvent) {
        val itemInHand = e.itemInHand

        if (itemInHand.type == Material.PLAYER_HEAD && itemInHand.hasItemMeta() && itemInHand.itemMeta.persistentDataContainer.has(CustomItems.IS_MICROWAVE_KEY, PersistentDataType.BYTE)) {
            val microwave = e.block.state as Skull
            val microwaveDataModel = MicrowaveDataModel(e.block.location)
            val microwaveJSONDataModel = DreamUtils.gson.toJson(microwaveDataModel)

            microwave.persistentDataContainer.set(CustomItems.IS_MICROWAVE_KEY, PersistentDataType.STRING, microwaveJSONDataModel)
            microwave.update()
        }

        if (itemInHand.type == Material.PLAYER_HEAD && itemInHand.hasItemMeta() && itemInHand.itemMeta.persistentDataContainer.has(CustomItems.IS_SUPERFURNACE_KEY, PersistentDataType.BYTE)) {
            val superfurnace = e.block.state as Skull
            val superfurnaceDataModel = SuperfurnaceDataModel(e.block.location)
            val superfurnaceJSONDataModel = DreamUtils.gson.toJson(superfurnaceDataModel)

            superfurnace.persistentDataContainer.set(CustomItems.IS_SUPERFURNACE_KEY, PersistentDataType.STRING, superfurnaceJSONDataModel)
            superfurnace.update()
        }

        if (itemInHand.type == Material.PLAYER_HEAD && itemInHand.hasItemMeta() && itemInHand.itemMeta.persistentDataContainer.has(CustomItems.IS_TRASHCAN_KEY, PersistentDataType.BYTE)) {
            val trashcan = e.block.state as Skull
            val trashcanDataModel = TrashCanDataModel(e.block.location)
            val trashcanJSONDataModel = DreamUtils.gson.toJson(trashcanDataModel)

            trashcan.persistentDataContainer.set(CustomItems.IS_TRASHCAN_KEY, PersistentDataType.STRING, trashcanJSONDataModel)
            trashcan.update()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCustomItemBreak(e: BlockFromToEvent) {
        if (e.toBlock.type == Material.PLAYER_HEAD || e.toBlock.type == Material.PLAYER_WALL_HEAD) {
            onCustomHeadBreak(null, e.toBlock) {
                e.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCustomItemBreak(e: BlockBreakEvent) {
        val clickedBlock = e.block

        if (clickedBlock.type == Material.PLAYER_HEAD || clickedBlock.type == Material.PLAYER_WALL_HEAD) {
            onCustomHeadBreak(e.player, clickedBlock) {
                e.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCustomItemBreak(e: BlockPistonExtendEvent) {
        for (block in e.blocks) {
            if (block.type == Material.PLAYER_HEAD || block.type == Material.PLAYER_WALL_HEAD) {
                val MICROWAVE = (block.state as Skull).persistentDataContainer.get(CustomItems.IS_MICROWAVE_KEY, PersistentDataType.STRING)
                val SUPERFURNACE = (block.state as Skull).persistentDataContainer.get(CustomItems.IS_SUPERFURNACE_KEY, PersistentDataType.STRING)
                val TRASHCAN = (block.state as Skull).persistentDataContainer.get(CustomItems.IS_TRASHCAN_KEY, PersistentDataType.STRING)

                if (MICROWAVE != null || SUPERFURNACE != null || TRASHCAN != null) {
                    e.isCancelled = true
                    return
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCustomItemBreak(e: BlockExplodeEvent) {
        e.blockList().removeIf { block ->
            if (block.type == Material.PLAYER_HEAD || block.type == Material.PLAYER_WALL_HEAD) {
                val MICROWAVE = (block.state as Skull).persistentDataContainer.get(CustomItems.IS_MICROWAVE_KEY, PersistentDataType.STRING)
                val SUPERFURNACE = (block.state as Skull).persistentDataContainer.get(CustomItems.IS_SUPERFURNACE_KEY, PersistentDataType.STRING)
                val TRASHCAN = (block.state as Skull).persistentDataContainer.get(CustomItems.IS_TRASHCAN_KEY, PersistentDataType.STRING)

                MICROWAVE != null || SUPERFURNACE != null || TRASHCAN != null
            } else false
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCustomItemBreak(e: EntityExplodeEvent) {
        e.blockList().removeIf { block ->
            if (block.type == Material.PLAYER_HEAD || block.type == Material.PLAYER_WALL_HEAD) {
                val MICROWAVE = (block.state as Skull).persistentDataContainer.get(CustomItems.IS_MICROWAVE_KEY, PersistentDataType.STRING)
                val SUPERFURNACE = (block.state as Skull).persistentDataContainer.get(CustomItems.IS_SUPERFURNACE_KEY, PersistentDataType.STRING)
                val TRASHCAN = (block.state as Skull).persistentDataContainer.get(CustomItems.IS_TRASHCAN_KEY, PersistentDataType.STRING)

                MICROWAVE != null || SUPERFURNACE != null || TRASHCAN != null
            } else false
        }
    }

    private fun onCustomHeadBreak(player: Player?, clickedBlock: Block, cancelEvent: () -> (Unit)) {
        val MICROWAVE = (clickedBlock.state as Skull).persistentDataContainer.get(CustomItems.IS_MICROWAVE_KEY, PersistentDataType.STRING)
        val SUPERFURNACE = (clickedBlock.state as Skull).persistentDataContainer.get(CustomItems.IS_SUPERFURNACE_KEY, PersistentDataType.STRING)
        val TRASHCAN = (clickedBlock.state as Skull).persistentDataContainer.get(CustomItems.IS_TRASHCAN_KEY, PersistentDataType.STRING)

        when {
            MICROWAVE != null -> {
                val microwaveDataModel = DreamUtils.gson.fromJson(MICROWAVE, MicrowaveDataModel::class.java)
                val microwaveItems: ArrayList<ItemStack> = arrayListOf()

                if (m.microwaves[microwaveDataModel.location] != null) {
                    val microwave = m.microwaves[microwaveDataModel.location] ?: return

                    microwave.stop() // Stop the microwave when breaking

                    microwave.inventory.viewers.forEach {
                        it.closeInventory()
                    }

                    m.microwaves.remove(microwaveDataModel.location)

                    for (i in 3..5) {
                        val item = microwave.inventory.getItem(i)

                        if (item != null) microwaveItems.add(item)
                    }
                } else {
                    microwaveDataModel.items?.forEach {
                        if (it != null) {
                            val deserialized = deserializeItemFromBase64OrFallbackToNullIfInvalid(it)

                            if (deserialized != null)
                                microwaveItems.add(deserialized)
                        }
                    }
                }

                val drops = listOf(CustomItems.MICROWAVE.clone()) + microwaveItems
                clickedBlock.type = Material.AIR
                cancelEvent.invoke()

                drops.forEach { with (clickedBlock) { world.dropItemNaturally(location, it) } }

                // TODO: Why do we need to fake the block drop item?
                if (player != null)
                    Bukkit.getPluginManager().callEvent(BlockDropItemEvent(clickedBlock, clickedBlock.state, player, listOf()))
            }

            SUPERFURNACE != null -> {
                val superfurnaceData = DreamUtils.gson.fromJson(SUPERFURNACE, SuperfurnaceDataModel::class.java)
                val superFurnaceItems = arrayListOf<ItemStack>()

                if (m.superfurnaces[superfurnaceData.location] != null) {
                    val superfurnace = m.superfurnaces[superfurnaceData.location] ?: return

                    superfurnace.stop() // Stop the super furnace when breaking

                    superfurnace.inventory.viewers.forEach {
                        it.closeInventory()
                    }

                    m.superfurnaces.remove(superfurnaceData.location)

                    val superFurnaceSlots = listOf(0, 1, 2, 3, 4, 5, 18, 19, 20, 21, 22, 23, 27,28, 29, 30, 31, 32)

                    for (i in superFurnaceSlots) {
                        val item = superfurnace.inventory.getItem(i)

                        if (item != null) superFurnaceItems.add(item)
                    }
                } else {
                    superfurnaceData.items?.forEach {
                        if (it != null) {
                            val deserialized = deserializeItemFromBase64OrFallbackToNullIfInvalid(it)

                            if (deserialized != null) {
                                superFurnaceItems.add(deserialized)
                            }
                        }
                    }
                }

                val drops = listOf(CustomItems.SUPERFURNACE.clone()) + superFurnaceItems
                clickedBlock.type = Material.AIR
                cancelEvent.invoke()

                with (clickedBlock) { drops.forEach { world.dropItemNaturally(location, it) } }

                // TODO: Why do we need to fake the block drop item?
                if (player != null)
                    Bukkit.getPluginManager().callEvent(BlockDropItemEvent(clickedBlock, clickedBlock.state, player, listOf()))
            }

            TRASHCAN != null -> {
                val drops = listOf(CustomItems.TRASHCAN.clone())
                clickedBlock.type = Material.AIR
                cancelEvent.invoke()

                with (clickedBlock) { world.dropItemNaturally(location, drops.single()) }

                // TODO: Why do we need to fake the block drop item?
                if (player != null)
                    Bukkit.getPluginManager().callEvent(BlockDropItemEvent(clickedBlock, clickedBlock.state, player, listOf()))
            }

            else -> return
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCustomItemClick(e: PlayerInteractEvent) {
        if (!e.rightClick)
            return

        val clickedBlock = e.clickedBlock ?: return

        if (clickedBlock.type == Material.PLAYER_HEAD || clickedBlock.type == Material.PLAYER_WALL_HEAD) {
            val SUPERFURNACE = (clickedBlock.state as Skull).persistentDataContainer.get(CustomItems.IS_SUPERFURNACE_KEY, PersistentDataType.STRING)
            val TRASHCAN = (clickedBlock.state as Skull).persistentDataContainer.get(CustomItems.IS_TRASHCAN_KEY, PersistentDataType.STRING)
            val MICROWAVE = (clickedBlock.state as Skull).persistentDataContainer.get(CustomItems.IS_MICROWAVE_KEY, PersistentDataType.STRING)

            if (SUPERFURNACE != null || TRASHCAN != null || MICROWAVE != null) {
                e.isCancelled = true

                val claim = GriefPrevention.instance.dataStore.getClaimAt(clickedBlock.location, false, null)

                if (claim != null && (claim.ownerName != e.player.name && claim.allowContainers(e.player) != null)) {
                    e.player.sendMessage("§cVocê não tem permissão para mexer neste item!")
                    return
                }

                when {
                    MICROWAVE != null -> {
                        val microwaveDataModel = DreamUtils.gson.fromJson(MICROWAVE, MicrowaveDataModel::class.java)

                        if (m.microwaves[microwaveDataModel.location] == null) {
                            val newMicrowave = Microwave(m, microwaveDataModel.location)
                            newMicrowave.inventory.setItem(3, microwaveDataModel.items?.get(0)?.let { deserializeItemFromBase64OrFallbackToNullIfInvalid(it) })
                            newMicrowave.inventory.setItem(4, microwaveDataModel.items?.get(1)?.let { deserializeItemFromBase64OrFallbackToNullIfInvalid(it) })
                            newMicrowave.inventory.setItem(5, microwaveDataModel.items?.get(2)?.let { deserializeItemFromBase64OrFallbackToNullIfInvalid(it) })

                            m.microwaves[microwaveDataModel.location] = newMicrowave
                        }

                        val microwave = m.microwaves[microwaveDataModel.location] ?: return

                        microwave.open(e.player)
                    }

                    SUPERFURNACE != null -> {
                        val superfurnaceData = DreamUtils.gson.fromJson(SUPERFURNACE, SuperfurnaceDataModel::class.java)

                        if (m.superfurnaces[superfurnaceData.location] == null) {
                            val newSuperFurnace = SuperFurnace(m, superfurnaceData.location)

                            val superFurnaceSlots = listOf(0, 1, 2, 3, 4, 5, 18, 19, 20, 21, 22, 23, 27,28, 29, 30, 31, 32)

                            for ((index, slot) in superFurnaceSlots.withIndex()) {
                                if (superfurnaceData.items?.get(index) != null)
                                    newSuperFurnace.inventory.setItem(slot, superfurnaceData.items[index]?.let { deserializeItemFromBase64OrFallbackToNullIfInvalid(it) })
                            }

                            m.superfurnaces[superfurnaceData.location] = newSuperFurnace
                        }

                        val superfurnace = m.superfurnaces[superfurnaceData.location] ?: return

                        if (!e.player.hasPermission("group.vip") && !e.player.hasPermission("group.vip+") && !e.player.hasPermission("group.vip++")) {
                            e.player.sendMessage("§cVocê não tem permissão para usar essa ferramenta, apenas §b§lVIPs§c!")
                            return
                        }

                        superfurnace.open(e.player)
                    }

                    TRASHCAN != null -> {
                        val trashcanData = DreamUtils.gson.fromJson(TRASHCAN, TrashCanDataModel::class.java)

                        val trashCan = TrashCan(m, trashcanData.location)

                        trashCan.open(e.player)
                    }

                    else -> return
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRecipePreviewClick(e: InventoryClickEvent) {
        // We block EVERYTHING
        // No, we don't care that the user cannot manipulate their inventory while the menu is open
        // JUST CLOSE IT BEFORE TRYING TO MESS WITH YOUR INVENTORY
        // Sorry, but trying to be "nice" just causes a lot of dupe issues
        val inventoryHolder = e.inventory.holder as? CustomItemRecipeHolder
        val clickedInventoryHolder = e.clickedInventory?.holder as? CustomItemRecipeHolder

        if (inventoryHolder != null || clickedInventoryHolder != null)
            e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(e: InventoryClickEvent) {
        val holder = e.clickedInventory?.holder

        when(holder) {
            is MicrowaveHolder -> {
                if (e.slot in 3..5) {
                    if (holder.m.running) {
                        e.isCancelled = true
                        holder.m.location.world.spawnParticle(Particle.ANGRY_VILLAGER, holder.m.location, 10, 1.0, 1.0, 1.0)
                        e.whoClicked.damage(1.0)
                        e.whoClicked.closeInventory()
                        e.whoClicked.sendMessage("§cSua mão queimou por você ter achado que seria uma brilhante ideia mexer em uma comida que está no micro-ondas...")
                    }
                    return
                }

                e.isCancelled = true

                if (e.currentItem?.type == Material.RED_STAINED_GLASS_PANE) {
                    holder.m.start()
                    return
                }

                if (e.currentItem?.type == Material.GREEN_STAINED_GLASS_PANE) {
                    holder.m.stop()
                    return
                }
            }

            is SuperFurnaceHolder -> {
                if (e.slot in 0..5 || e.slot in 18..23 || e.slot in 27..32) {
                    if (holder.m.running) {
                        e.isCancelled = true
                        holder.m.location.world.spawnParticle(Particle.ANGRY_VILLAGER, holder.m.location, 10, 1.0, 1.0, 1.0)
                        e.whoClicked.damage(2.5*2)
                        e.whoClicked.closeInventory()
                        e.whoClicked.sendMessage("§cSua mão queimou por você ter achado que seria uma brilhante ideia mexer em uma super fornalha em funcionamento...")
                    }
                    return
                }

                e.isCancelled = true

                if (e.currentItem?.type == Material.RED_STAINED_GLASS_PANE) {
                    holder.m.start(e.whoClicked as Player)
                    return
                }

                if (e.currentItem?.type == Material.GREEN_STAINED_GLASS_PANE) {
                    holder.m.stop()
                    return
                }
            }

            else -> return
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClose(e: InventoryCloseEvent) {
        val holder = e.inventory.holder

        when(holder) {
            is MicrowaveHolder -> {
                val items = arrayOf(
                    e.inventory.getItem(3)?.let { ItemUtils.serializeItemToBase64(it) },
                    e.inventory.getItem(4)?.let { ItemUtils.serializeItemToBase64(it) },
                    e.inventory.getItem(5)?.let { ItemUtils.serializeItemToBase64(it) }
                )

                val microwave = holder.m.location.block.state as Skull
                val microwaveDataModel = MicrowaveDataModel(holder.m.location, items)
                val microwaveDataModelJson = DreamUtils.gson.toJson(microwaveDataModel)

                microwave.persistentDataContainer.set(CustomItems.IS_MICROWAVE_KEY, PersistentDataType.STRING, microwaveDataModelJson)
                microwave.update()
            }

            is SuperFurnaceHolder -> {
                val superFurnaceSlots = listOf(0, 1, 2, 3, 4, 5, 18, 19, 20, 21, 22, 23, 27,28, 29, 30, 31, 32)
                val items = arrayListOf<String?>()

                for(slot in superFurnaceSlots) {
                    items.add(e.inventory.getItem(slot)?.let { ItemUtils.serializeItemToBase64(it) })
                }

                val superfurnace = holder.m.location.block.state as Skull
                val superfurnaceDataModel = SuperfurnaceDataModel(holder.m.location, items)
                val superfurnaceDataModelJson = DreamUtils.gson.toJson(superfurnaceDataModel)

                superfurnace.persistentDataContainer.set(CustomItems.IS_SUPERFURNACE_KEY, PersistentDataType.STRING, superfurnaceDataModelJson)
                superfurnace.update()
            }
        }
    }

    // This is used because some items may have the old unsupported method of serializing items
    private fun deserializeItemFromBase64OrFallbackToNullIfInvalid(base64: String): ItemStack? {
        try {
            return ItemUtils.deserializeItemFromBase64(base64)
        } catch (e: Exception) {
            m.logger.log(Level.WARNING, e) { "Failed to deserialize item $base64" }
            return null
        }
    }
}

class MicrowaveDataModel(val location: Location, val items: Array<String?>? = null)
class SuperfurnaceDataModel(val location: Location, val items: ArrayList<String?>? = null)
class TrashCanDataModel(val location: Location)