package net.perfectdreams.dreamcustomitems.listeners

import com.viaversion.viaversion.api.minecraft.chunks.ChunkSection
import com.viaversion.viaversion.api.minecraft.chunks.PaletteType
import com.viaversion.viaversion.api.type.types.chunk.ChunkSectionType1_18
import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.shorts.Short2ObjectArrayMap
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcore.utils.get
import net.perfectdreams.dreamcore.utils.packetevents.ClientboundPacketSendEvent
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamcustomitems.DreamCustomItems
import net.perfectdreams.dreamcustomitems.utils.CustomBlocks
import net.perfectdreams.dreamcustomitems.utils.VanillaBlockStateRemapper
import org.bukkit.block.ChiseledBookshelf
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.ProjectileHitEvent
import kotlin.math.absoluteValue

class CustomBlocksListener(
    val m: DreamCustomItems
) : Listener {
    companion object {
        val chunkSectionType = ChunkSectionType1_18(
            Block.BLOCK_STATE_REGISTRY.size(),
            BuiltInRegistries.BIOME_SOURCE.size()
        )
    }

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        // Used to resynchronize custom blocks
        val hitBlock = event.hitBlock ?: return

        // Bukkit.broadcastMessage("Block hit is ${hitBlock}")

        if (hitBlock.type !in CustomBlocks.customBlocksThatUseTargetBlockAsATarget.map { it.sourceBlockData.material })
            return

        // We need to resync because that causes the block to change the state on the client side when hitting it with an arrow
        // Doing an update resyncs the block state on the client
        hitBlock.world.players.forEach {
            it.sendBlockChange(hitBlock.location, hitBlock.blockData)
        }

        m.launchMainThread {
            // However SOMETIMES it doesn't work, so we will update it with a delay
            delayTicks(4)
            hitBlock.world.players.forEach {
                it.sendBlockChange(hitBlock.location, hitBlock.blockData)
            }
        }
    }

    // TODO: Do we need this?
    /* @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInteract(e: PlayerInteractEvent) {
        // Block any book related interactions against a bookshelf
        val hitBlock = e.clickedBlock ?: return
        if (hitBlock.type != Material.CHISELED_BOOKSHELF)
            return

        val blockState = hitBlock.state as ChiseledBookshelf
        val customBlockKey = blockState.persistentDataContainer.get(CustomBlocks.CUSTOM_BLOCK_KEY) ?: return
        val customBlock = CustomBlocks.getCustomBlockById(customBlockKey)

        if (customBlock != null) {
            // Block putting books altogether
            if (e.item?.type in chiseledBookshelfBlockedInteractions) {
                e.isCancelled = true
                return
            }

            // Now here's the thing: The server doesn't allow you to place blocks near chiseled bookshelves... but why?
            // Because if we are clicking on the side that there is books, the server thinks that you are trying to remove books from the bookshelf!
            // To workaround this, we will change the player's state to sneaking
            // We could also workaround this by editing the server... maybe we should do that later
            if (e.player.isSneaking)
                return

            e.player.isSneaking = true

            m.schedule {
                // Reset sneak after 1 tick
                waitFor(1L)

                if (e.player.isSneaking)
                    e.player.isSneaking = false
            }
        }
    } */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    fun onBlockDropItem(event: BlockDropItemEvent) {
        val blockState = event.blockState as? ChiseledBookshelf
        if (blockState != null) {
            // Old code!!
            val customBlockKey = blockState.persistentDataContainer.get(CustomBlocks.CUSTOM_BLOCK_KEY) ?: return
            val customBlock = CustomBlocks.getCustomBlockById(customBlockKey)

            if (customBlock != null) {
                // println("Custom block!")
                val drops = customBlock.blockStates.first().drops.invoke()
                val dropsAsLiterallyItemDrops = drops.map {
                    event.block.world.dropItem(event.block.location, it)
                }
                event.items.clear()
                event.items.addAll(dropsAsLiterallyItemDrops)
            }
        } else {
            val customBlock = CustomBlocks.getCustomBlockOfMaterial(event.blockState.type)

            if (customBlock != null) {
                // println("Custom block!")
                val drops = customBlock.drops.invoke()
                val dropsAsLiterallyItemDrops = drops.map {
                    event.block.world.dropItem(event.block.location, it)
                }
                event.items.clear()
                event.items.addAll(dropsAsLiterallyItemDrops)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCustomBlockPlace(e: BlockPlaceEvent) {
        val customBlock = CustomBlocks.allCustomBlocks.firstOrNull { it.sourceItemCheck.invoke(e.itemInHand) }

        if (customBlock == null) {
            // Null, let's just ignore it then
            return
        }

        e.block.type = customBlock.blockStates.first().sourceBlockData.material
    }

    // TODO: Remove this, we don't need this anymore (however, keeping the event as a log to check which blocks need to be added to the datapack is still good)
    /* @EventHandler
    fun onBlockDamage(event: PlayerBlockDestroySpeedEvent) {
        println("PlayerBlockDestroySpeedEvent: ${event.block.type} is ${event.destroySpeed}")

        if (event.block.type != Material.CHISELED_BOOKSHELF)
            return

        val blockState = event.block.state as? ChiseledBookshelf ?: return
        val customBlockKey = blockState.persistentDataContainer.get(CustomBlocks.CUSTOM_BLOCK_KEY) ?: return
        val customBlock = CustomBlocks.getCustomBlockById(customBlockKey)

        if (customBlock != null) {
            event.destroySpeed = 0.5f
        }
    } */

    @EventHandler
    fun onPacketSend(event: ClientboundPacketSendEvent) {
        val player = event.player

        val msg = event.packet

        // THE MEAT:TM: OF THE CUSTOM BLOCK STUFF (this is VERY hardcore)
        // Reminder that is MOSTLY (some packets may not be ran on the main thread) ran on the main thread, so this needs to be quick and snappy!
        // Keep in mind that a LOT of these requires SparklyPaper's "Helpful NMS packet changes" to avoid reflection usage
        if (msg is ClientboundLevelChunkWithLightPacket) {
            // The check is made here to avoid getting the DreamBedrockIntegrations plugin on every packet
            val isBedrockClient = player.isBedrockClient
            // *Technically* this is bad, we shouldn't access the player world in a async thread
            // HOWEVER we are only using this to get the section count, so it shouldn't be *that* bad
            val playerWorld = player.world

            // println("Map chunk packet")
            // This is actually pretty weird
            // there is actually a "nested" (?) packet
            // and because ProtocolLib reads the non-nested packet, we need to cast to the NMS object and read it from there
            //
            // Very useful! https://nms.screamingsandals.org/
            val clientboundLevelChunkWithLightPacket = msg
            val chunkX = clientboundLevelChunkWithLightPacket.x
            val chunkZ = clientboundLevelChunkWithLightPacket.z

            val coordinateX = clientboundLevelChunkWithLightPacket.x * 16
            val coordinateZ = clientboundLevelChunkWithLightPacket.z * 16
            val chunkDataPacket = clientboundLevelChunkWithLightPacket.chunkData

            val worldMinHeight = playerWorld.minHeight
            val worldMaxHeight = playerWorld.maxHeight
            val worldTrueHeight = (worldMinHeight.absoluteValue + worldMaxHeight)
            val ySectionCount = worldTrueHeight / 16

            // And then we read the byte array from there!
            val originalBufferSize = chunkDataPacket.buffer.size
            val byteArray = chunkDataPacket.buffer

            // println("Chunk at $coordinateX, $coordinateZ")

            // println("Current Chunk: $chunkX, $chunkZ")
            // val copiedBuffer = measureTime { Unpooled.copiedBuffer(byteArray) }
            // val wrappedBuffer = measureTime { Unpooled.wrappedBuffer(byteArray) }

            // println("Took $copiedBuffer with copied buffer for ${packet.x} ${packet.z}")
            // println("Took $wrappedBuffer with wrapped buffer for ${packet.x} ${packet.z}")

            // Let's use a wrappedBuffer instead of a copiedBuffer, we don't really care about rewriting the backed array anyway
            val buf = Unpooled.wrappedBuffer(byteArray)

            // println("Took $time to read the sections")

            var requiresEdits = false
            // println("World: ${event.player.world.minHeight}")
            // println("Sections: $ySectionCount")

            val sections = ArrayList<ChunkSection>(ySectionCount)
            for (i in 0 until ySectionCount) {
                val section = chunkSectionType.read(buf)

                sections.add(section)

                val blockPalette = section.palette(PaletteType.BLOCKS) ?: continue // Does not have any block palette...

                // Keep in mind that having the block in the palette DOES NOT MEAN that the block is actually present in any of the sections, because it seems that the server caches blocks that were in the palette
                // Having the block in the palette: The block MAY or MAY NOT be in the section, but a block will NEVER be in the section while not present in the palette
                // We can't use replaceId because that creates bugs if the source and target block are both present in the chunk
                for (idx in 0 until blockPalette.size()) { // Each section is 4096 blocks
                    try {
                        val paletteBlockId = blockPalette.idByIndex(idx)

                        val newState = VanillaBlockStateRemapper.stateIdToBlockState[paletteBlockId]
                        if (newState != null) {
                            // println("Remapping vanilla block ${Block.BLOCK_STATE_REGISTRY.byId(paletteBlockId)} to $newState")
                            blockPalette.setIdByIndex(idx, Block.BLOCK_STATE_REGISTRY.getId(newState))
                            requiresEdits = true
                            continue
                        }

                        val newCustomBlock = CustomBlocks.stateIdToBlockState[paletteBlockId]
                        if (newCustomBlock != null) {
                            // println("Remapping custom block ${Block.BLOCK_STATE_REGISTRY.byId(paletteBlockId)} to ${newCustomBlock.targetBlockStateNMS}")
                            blockPalette.setIdByIndex(idx, if (isBedrockClient) Block.BLOCK_STATE_REGISTRY.getId(newCustomBlock.fallbackBlockStateNMS) else Block.BLOCK_STATE_REGISTRY.getId(newCustomBlock.targetBlockStateNMS))
                            requiresEdits = true
                            continue
                        }
                    } catch (e: Exception) {
                        // This is here because Netty swallows the exception
                        e.printStackTrace()
                        return
                    }
                }
            }

            if (requiresEdits) {
                // println("Requires edit, so we are going to clear the read buffer")
                // Only rewrite the packet if we really need to edit the packet

                // Optimization: To avoid resizes, let's create a buffer with the same size as the original chunk
                val byteBuf = Unpooled.buffer(originalBufferSize)
                sections.forEach {
                    chunkSectionType.write(byteBuf, it)
                }

                chunkDataPacket.buffer = byteBuf.array() // This requires SparklyPaper's "Helpful NMS packet changes"
                return
            }
            return
        }

        // ATTENTION! ClientboundBlockUpdatePacket and ClientboundSectionBlocksUpdatePacket reuse packets
        // To properly send them downstream
        if (msg is ClientboundBlockUpdatePacket) {
            // println("ClientboundBlockUpdatePacket")
            // The check is made here to avoid getting the DreamBedrockIntegrations plugin on every packet
            val isBedrockClient = player.isBedrockClient

            // println("ClientboundBlockUpdatePacket for $player is in ${Thread.currentThread()}")
            val updatePacket = msg
            // println("ClientboundBlockUpdatePacket for $player is $packet in ${Thread.currentThread()}, isBedrockClient? $isBedrockClient")

            // println("WrapperPlayServerBlockChange for ${updatePacket.pos.x}, ${updatePacket.pos.y}, ${updatePacket.pos.z} - ${updatePacket.blockState.bukkitMaterial}")

            // println("Custom block material is ${updatePacket.blockState.bukkitMaterial}")
            // If it is a target block, we need to sync it to 0 power
            val newState = VanillaBlockStateRemapper.stateToBlockState[updatePacket.blockState]
            if (newState != null) {
                // We create a new packet here because the server resends the same packet to multiple players
                // This is not a specific issue about us mind you, ProtocolLib has the same issue https://github.com/dmulloy2/ProtocolLib/issues/929
                event.packet = ClientboundBlockUpdatePacket(
                    updatePacket.pos,
                    newState
                )
                return
            } else {
                val customBlock = CustomBlocks.getCustomBlockOfNMSState(updatePacket.blockState)
                // println("Custom block material is ${updatePacket.blockState.bukkitMaterial}")
                // println("Custom block is $customBlock")
                if (customBlock != null) {
                    // Oh no, NMS!!!
                    // println("Target is ${customBlock.targetBlockStateNMS}")
                    event.packet = ClientboundBlockUpdatePacket(
                        updatePacket.pos,
                        if (isBedrockClient) customBlock.fallbackBlockStateNMS else customBlock.targetBlockStateNMS
                    )
                }
            }
            return
        }

        if (msg is ClientboundSectionBlocksUpdatePacket) {
            // println("ClientboundSectionBlocksUpdatePacket")
            // The check is made here to avoid getting the DreamBedrockIntegrations plugin on every packet
            val isBedrockClient = player.isBedrockClient

            // println("ClientboundSectionBlocksUpdatePacket for $player is in ${Thread.currentThread()}")
            // println("Multi block change")
            // This is the CHUNK SECTION POSITION
            val sectionBlocksUpdatePacket = msg
            // println("ClientboundSectionBlocksUpdatePacket for $player is $packet in ${Thread.currentThread()}, isBedrockClient? $isBedrockClient")

            val sectionPos = sectionBlocksUpdatePacket.sectionPos
            val origin = sectionPos.origin()

            val shorts = sectionBlocksUpdatePacket.positions
            // println(blockPosition.x.toString() + ", " + blockPosition.y + ", " + blockPosition.z)

            // println("Section Pos: ${sectionPos.x()}, ${sectionPos.y()}, ${sectionPos.z()}")
            // println("Shorts:")
            // shorts.forEach {
            //     val x = it.toInt() ushr 8 and 15
            //     val y = it.toInt() ushr 0 and 15
            //     val z = it.toInt() ushr 4 and 15
            //     // println("$it $x, $y, $z")
            // }

            val changedBlocks = sectionBlocksUpdatePacket.states

            // If there isn't any note blocks in this packet, let's ignore it
            if (changedBlocks.all { VanillaBlockStateRemapper.stateToBlockState[it] == null && CustomBlocks.getCustomBlockOfNMSState(it) == null }) {
                return
            }

            val newChangedBlockStatesWithPosition = Short2ObjectArrayMap<BlockState>(shorts.size)

            for ((index, originalBlockState) in changedBlocks.withIndex()) {
                // TODO: Refactor this, maybe merge with the single block update code?
                val newState = VanillaBlockStateRemapper.stateToBlockState[originalBlockState]
                if (newState != null) {
                    newChangedBlockStatesWithPosition.put(shorts[index], newState)
                } else {
                    val customBlock = CustomBlocks.getCustomBlockOfNMSState(originalBlockState)
                    if (customBlock != null) {
                        // Oh no, NMS!!!
                        newChangedBlockStatesWithPosition.put(
                            shorts[index],
                            if (isBedrockClient) customBlock.fallbackBlockStateNMS else customBlock.targetBlockStateNMS
                        )
                    } else {
                        // add the block state as is to the list
                        newChangedBlockStatesWithPosition.put(
                            shorts[index],
                            originalBlockState
                        )
                    }
                }
            }

            event.packet = ClientboundSectionBlocksUpdatePacket(
                sectionPos,
                newChangedBlockStatesWithPosition
            )
            return
        }

        if (msg is ClientboundLevelParticlesPacket) {
            val particleOptions = msg.particle

            if (particleOptions is BlockParticleOption) {
                val isBedrockClient = player.isBedrockClient
                val state = particleOptions.state
                val newState = VanillaBlockStateRemapper.stateToBlockState[state]
                if (newState != null) {
                    event.packet = ClientboundLevelParticlesPacket(
                        BlockParticleOption(
                            particleOptions.type,
                            newState
                        ),
                        msg.isOverrideLimiter,
                        msg.alwaysShow(),
                        msg.x,
                        msg.y,
                        msg.z,
                        msg.xDist,
                        msg.yDist,
                        msg.zDist,
                        msg.maxSpeed,
                        msg.count
                    )
                    return
                } else {
                    val customBlock = CustomBlocks.getCustomBlockOfNMSState(state)
                    if (customBlock != null) {
                        event.packet = ClientboundLevelParticlesPacket(
                            BlockParticleOption(
                                particleOptions.type,
                                if (isBedrockClient) customBlock.fallbackBlockStateNMS else customBlock.targetBlockStateNMS
                            ),
                            msg.isOverrideLimiter,
                            msg.alwaysShow(),
                            msg.x,
                            msg.y,
                            msg.z,
                            msg.xDist,
                            msg.yDist,
                            msg.zDist,
                            msg.maxSpeed,
                            msg.count
                        )
                    }
                    return
                }
            } else return // Doesn't have a BlockState, so just skip!
        }
    }
}