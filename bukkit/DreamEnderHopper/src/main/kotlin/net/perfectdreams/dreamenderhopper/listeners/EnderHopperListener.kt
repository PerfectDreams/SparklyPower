package net.perfectdreams.dreamenderhopper.listeners

import me.ryanhamshire.GriefPrevention.ClaimPermission
import me.ryanhamshire.GriefPrevention.GriefPrevention
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.extensions.clickedOnBlock
import net.perfectdreams.dreamcore.utils.extensions.rightClick
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamenderhopper.DreamEnderHopper
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.block.Hopper
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.*

class EnderHopperListener(val m: DreamEnderHopper) : Listener {
    private val settingAHopperDestination = mutableMapOf<Player, Hopper>()
    private val alreadyCheckingHopperOnNextTick = mutableSetOf<Location>()

    // WorldGuard: Hoppers cause a HUGE performance impact due to "InventoryMoveItemEventDebounce"
    // To avoid it, enable "ignore-hopper-item-move-events" in WorldGuard's configuration

    // Must be on highest priority
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(e: BlockPlaceEvent) {
        val isAEnderHopper = e.itemInHand.itemMeta.persistentDataContainer.get(DreamEnderHopper.HOPPER_TELEPORTER)

        if (isAEnderHopper) {
            val hopper = e.block.getState(false) as Hopper
            hopper.persistentDataContainer.set(DreamEnderHopper.HOPPER_TELEPORTER, true)
            hopper.update()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent) {
        val hopper = e.block.getState(false) as? Hopper ?: return
        val isAEnderHopper = hopper.persistentDataContainer.get(DreamEnderHopper.HOPPER_TELEPORTER)

        if (isAEnderHopper) {
            e.isCancelled = true
            e.block.type = Material.AIR
            e.block.world.dropItem(
                e.block.location,
                m.createEnderHopper()
            )
        }
    }

    // Must be on highest priority
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRightClick(e: PlayerInteractEvent) {
        if (!e.clickedOnBlock)
            return

        val clickedBlock = e.clickedBlock ?: return

        val hopperSource = settingAHopperDestination[e.player]
        if (hopperSource != null && hopperSource.location != clickedBlock.location) {
            e.isCancelled = true
            val state = clickedBlock.getState(false)
            if (state is Container) {
                if (state.world != hopperSource.world) {
                    e.player.sendMessage("§cO destino precisa ser no mesmo mundo!")
                    return
                }

                val claim = GriefPrevention.instance.dataStore.getClaimAt(clickedBlock.location, false, null)

                if (claim == null || claim.ownerName == e.player.name || claim.checkPermission(e.player, ClaimPermission.Inventory, null) == null) {
                    hopperSource.persistentDataContainer.set(DreamEnderHopper.HOPPER_COORDINATES, "${clickedBlock.x};${clickedBlock.y};${clickedBlock.z}")
                    hopperSource.update()
                    e.player.sendMessage("§aO destino do Ender Hopper foi alterado!")
                    settingAHopperDestination.remove(e.player)
                } else {
                    e.player.sendMessage("§cVocê não tem permissão para colocar que o destino do Ender Hopper seja aqui!")
                }
            }
            return
        }

        // Don't block players trying to put hoppers near it
        if (e.item?.type == Material.HOPPER)
            return

        when (val result = getEnderHopperInformation(clickedBlock)) {
            is EnderHopperInformation -> {
                if (e.rightClick) {
                    e.isCancelled = true

                    if (hopperSource != null) {
                        e.player.sendMessage("§cVocê cancelou a alteração do destino do Ender Hopper")
                        settingAHopperDestination.remove(e.player)
                        return
                    }
                    settingAHopperDestination[e.player] = result.enderHopperState
                    e.player.sendMessage("§eClique no contêiner (baú, funil, etc) que você deseja que seja o meu destino!")
                } else {
                    // We will only cancel right clicks to avoid players not being able to break the block
                    e.player.sendMessage("§eDestino do Ender Hopper:")
                    e.player.sendMessage("§aX: §e${result.targetX}")
                    e.player.sendMessage("§aY: §e${result.targetY}")
                    e.player.sendMessage("§aZ: §e${result.targetZ}")
                    e.player.sendMessage("§ePara alterar o destino, clique com botão direito em mim!")
                }
            }

            is EnderHopperWithoutTarget -> {
                if (e.rightClick) {
                    e.isCancelled = true

                    if (hopperSource != null) {
                        e.player.sendMessage("§cVocê cancelou a alteração do destino do Ender Hopper")
                        settingAHopperDestination.remove(e.player)
                        return
                    }
                    settingAHopperDestination[e.player] = result.enderHopperState
                    e.player.sendMessage("§eClique no contêiner (baú, funil, etc) que você deseja que seja o meu destino!")
                } else {
                    // We will only cancel right clicks to avoid players not being able to break the block
                    e.player.sendMessage("§cEu não possuo um destino! Para configurar um destino para mim, clique com botão direito em mim!")
                }
            }
            NotAnEnderHopper -> {}
        }
    }

    // Must be on highest priority
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onItemMove(e: InventoryMoveItemEvent) {
        when (val result = getEnderHopperInformation(e.destination)) {
            is EnderHopperInformation -> {
                e.isCancelled = true
                // The InventoryMoveItemEvent is called for every item in the hopper
                // To avoid checking the same hopper multiple times, we will ignore it if this hopper is already scheduled to be executed on the next tick
                if (alreadyCheckingHopperOnNextTick.contains(result.enderHopperState.location))
                    return

                alreadyCheckingHopperOnNextTick.add(result.enderHopperState.location)

                m.launchMainThread {
                    delayTicks(1L)
                    alreadyCheckingHopperOnNextTick.remove(result.enderHopperState.location)

                    // Don't rely on the "inventory.contents", the stack size will always be 1 for... some reason?
                    // So let's make our own hopper, with blackjack, and hookers!
                    // Technically, if we handle all items here, because the server is single threaded, it *shouldn't* cause any dupe issues
                    // https://www.spigotmc.org/threads/hopper-reports-wrong-item-stack-size-inside-of-inventorymoveitemevent-event.534714/
                    processHopperItems(e.source, result)

                    // Also check self, this may happen if the plugin was disabled or something
                    if (!result.enderHopperState.inventory.isEmpty)
                        processHopperItems(result.enderHopperState.inventory, result)
                }
            }
            is EnderHopperWithoutTarget -> {
                // If we don't have a target, cancel the event and move on
                e.isCancelled = true
                return
            }
            NotAnEnderHopper -> {
                // Do nothing
                return
            }
        }
    }

    // Must be on highest priority
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onItemDrop(e: InventoryPickupItemEvent) {
        when (val result = getEnderHopperInformation(e.inventory)) {
            is EnderHopperInformation -> {
                e.isCancelled = true

                val success = processHopperItem(e.item.itemStack, result)
                if (success)
                    e.item.remove()

                // Also check self, this may happen if the plugin was disabled or something
                if (!result.enderHopperState.inventory.isEmpty)
                    processHopperItems(result.enderHopperState.inventory, result)
            }
            is EnderHopperWithoutTarget -> {
                // If we don't have a target, cancel the event and move on
                e.isCancelled = true
                return
            }
            NotAnEnderHopper -> {
                // Do nothing
                return
            }
        }
    }

    private fun processHopperItems(inventory: Inventory, enderHopperInformation: EnderHopperInformation) {
        // First things first, is the chunk loaded?
        // If it isn't, bail out!
        if (!enderHopperInformation.enderHopperState.world.isChunkLoaded(enderHopperInformation.targetX / 16, enderHopperInformation.targetZ / 16))
            return

        // Transfer all items from the source to the target inventory
        val targetContainer = enderHopperInformation.enderHopperState.world.getBlockAt(enderHopperInformation.targetX, enderHopperInformation.targetY, enderHopperInformation.targetZ).getState(false) as? Container
        if (targetContainer == null) {
            // Unknown target, remove destination from the hopper state
            val state = enderHopperInformation.enderHopperState
            state.persistentDataContainer.remove(DreamEnderHopper.HOPPER_COORDINATES)
            state.update()
            return
        }
        val targetInventory = targetContainer.inventory

        // Add the item to the target container
        var atLeastOneItemWasTeleported = false

        // We will compare the holder instead of the inventory because double chests have different inventories depending on where you clicked
        // This doesn't work for double chests!
        if (inventory != targetContainer.inventory) {
            when (inventory) {
                is FurnaceInventory -> {
                    val item = inventory.result
                    if (item != null && targetInventory.canHoldItem(item)) {
                        atLeastOneItemWasTeleported = true

                        // Add item to target
                        targetInventory.addItem(item)

                        // Remove item from the source inventory
                        inventory.result = null
                    }
                }

                is BrewerInventory -> {
                    // only teleport the result of the brewing
                    val rawSlotResults = listOf(0, 1, 2)
                    val results = rawSlotResults.mapNotNull { inventory.getItem(it) }

                    if (results.isNotEmpty()) {
                        results.forEach { item ->
                            if (targetInventory.canHoldItem(item)) {
                                atLeastOneItemWasTeleported = true

                                // Add item to target
                                targetInventory.addItem(item)

                                // Remove item from the source inventory
                                inventory.setItem(inventory.contents.indexOf(item), null)
                            }
                        }
                    }
                }

                else -> {
                    for ((index, item) in inventory.withIndex()) {
                        if (item != null && targetInventory.canHoldItem(item)) {
                            atLeastOneItemWasTeleported = true

                            // Remove item from the source inventory
                            // We can't use removeItem because that causes issues if we are trying to add/remove an item with >= 33 quantity, if the source and the target
                            // are the same container
                            inventory.setItem(index, null)

                            // Add item to target
                            targetInventory.addItem(item)
                        }
                    }
                }
            }
        } else {
            // If it is the same inventory, let's just "pretend" that it worked
            // Because if we don't, this causes a duplication issue if the item being added has >= 33 amount on its stack!
            // The reason the dupe happens is because, if the item "stacks", it won't be the same "item" anymore to the removeItem call (because the amount is different), causing issues
            // (The dupe was actually fixed, but I wanted to keep this as an "easter egg" :3)
            // https://imgur.com/L9XrlG9
            // https://canary.discord.com/channels/320248230917046282/1024132819158564874/1033162453372108800
            if (!inventory.isEmpty) {
                spawnBrokenEffects(targetContainer.block)
                spawnBrokenEffects(enderHopperInformation.enderHopperState.block)
            }
            return
        }

        // Successful transfer!
        if (atLeastOneItemWasTeleported) {
            spawnEffects(targetContainer.block)
            spawnEffects(enderHopperInformation.enderHopperState.block)
        } else {
            m.logger.info { "[Multi Item] Hopper at ${targetContainer.location.x} ${targetContainer.location.y} ${targetContainer.location.z} didn't have any items moved!" }
        }
    }

    private fun processHopperItem(item: ItemStack, enderHopperInformation: EnderHopperInformation): Boolean {
        // Transfer a single item to the hopper
        // This won't delete the "item" from the world!
        val targetContainer = enderHopperInformation.enderHopperState.world.getBlockAt(enderHopperInformation.targetX, enderHopperInformation.targetY, enderHopperInformation.targetZ).getState(false) as? Container
        if (targetContainer == null) {
            // Unknown target, remove destination from the hopper state
            val state = enderHopperInformation.enderHopperState
            state.persistentDataContainer.remove(DreamEnderHopper.HOPPER_COORDINATES)
            state.update()
            return false
        }
        val targetInventory = targetContainer.inventory

        // Add the item to the target container
        var atLeastOneItemWasTeleported = false

        if (targetInventory.canHoldItem(item)) {
            atLeastOneItemWasTeleported = true

            // Add item to target
            targetInventory.addItem(item)
        }

        // Successful transfer!
        if (atLeastOneItemWasTeleported) {
            spawnEffects(targetContainer.block)
            spawnEffects(enderHopperInformation.enderHopperState.block)
        } else {
            m.logger.info { "[Multi Item] Hopper at ${targetContainer.location.x} ${targetContainer.location.y} ${targetContainer.location.z} didn't have any items moved!" }
        }

        return atLeastOneItemWasTeleported
    }

    private fun spawnEffects(block: Block) {
        block.world.playSound(
            block.location.add(0.5, 0.0, 0.5),
            Sound.ENTITY_ENDERMAN_TELEPORT,
            SoundCategory.BLOCKS,
            0.5f,
            DreamUtils.random.nextFloat(0.7f, 1.4f)
        )

        block.world.spawnParticle(
            Particle.PORTAL,
            block.location.add(0.5, 0.0, 0.5),
            25,
            0.5,
            0.5,
            0.5
        )
    }

    private fun spawnBrokenEffects(block: Block) {
        block.world.playSound(
            block.location.add(0.5, 0.0, 0.5),
            Sound.ENTITY_GENERIC_EXPLODE,
            SoundCategory.BLOCKS,
            0.5f,
            DreamUtils.random.nextFloat(0.7f, 1.4f)
        )

        block.world.spawnParticle(
            Particle.EXPLOSION,
            block.location.add(0.5, 0.0, 0.5),
            5,
            0.5,
            0.5,
            0.5
        )
    }

    private fun getEnderHopperInformation(inventory: Inventory): EnderHopperResult {
        val holder = inventory.getHolder(false) ?: return NotAnEnderHopper
        if (holder !is Hopper)
            return NotAnEnderHopper

        // Okay, so it is a hopper!
        val isHopperTeleporter = holder.persistentDataContainer.get(DreamEnderHopper.HOPPER_TELEPORTER)
        if (!isHopperTeleporter)
            return NotAnEnderHopper

        val coordinates = holder.persistentDataContainer.get(DreamEnderHopper.HOPPER_COORDINATES) ?: return EnderHopperWithoutTarget(holder)

        val (xStr, yStr, zStr) = coordinates.split(";")
        val x = xStr.toInt()
        val y = yStr.toInt()
        val z = zStr.toInt()

        return EnderHopperInformation(
            holder,
            x,
            y,
            z
        )
    }

    private fun getEnderHopperInformation(block: Block): EnderHopperResult {
        if (block.type != Material.HOPPER)
            return NotAnEnderHopper

        val holder = block.getState(false) // Not using snapshots gives a nice performance boost
        if (holder !is Hopper)
            return NotAnEnderHopper

        // Okay, so it is a hopper!
        val isHopperTeleporter = holder.persistentDataContainer.get(DreamEnderHopper.HOPPER_TELEPORTER)
        if (!isHopperTeleporter)
            return NotAnEnderHopper

        val coordinates = holder.persistentDataContainer.get(DreamEnderHopper.HOPPER_COORDINATES) ?: return EnderHopperWithoutTarget(holder)

        val (xStr, yStr, zStr) = coordinates.split(";")
        val x = xStr.toInt()
        val y = yStr.toInt()
        val z = zStr.toInt()

        return EnderHopperInformation(
            holder,
            x,
            y,
            z
        )
    }

    sealed class EnderHopperResult

    object NotAnEnderHopper : EnderHopperResult()
    class EnderHopperWithoutTarget(val enderHopperState: Hopper) : EnderHopperResult()

    data class EnderHopperInformation(
        val enderHopperState: Hopper,
        val targetX: Int,
        val targetY: Int,
        val targetZ: Int
    ) : EnderHopperResult()
}