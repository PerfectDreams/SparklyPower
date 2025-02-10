package net.perfectdreams.dreamajuda.theatermagic

import kotlinx.coroutines.Job
import net.perfectdreams.dreamajuda.DreamAjuda
import net.perfectdreams.dreamcore.utils.BlockUtils
import net.perfectdreams.dreamcore.utils.ItemUtils
import net.perfectdreams.dreamcore.utils.extensions.isBetween
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import kotlin.time.measureTime

class TheaterMagicRecordingAnimation(
    val m: DreamAjuda,
    val player: Player,
    val world: World,
    val minBlockSnapshot: Location,
    val maxBlockSnapshot: Location,
    val startedAtTick: Int,
    val fileName: String,
) {
    companion object {
        private val PLAYER_EQUIPMENT_SLOTS = setOf(
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND,
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD,
        )
    }

    val originalBlocksSnapshot = mutableMapOf<BlockPosition, StoredBlockData>()
    val currentBlocksSnapshot = mutableMapOf<BlockPosition, StoredBlockData>()
    val keyframes = mutableMapOf<Int, Keyframe>()
    var job: Job? = null
    val trackedActiveEquipments = mutableMapOf<Player, MutableMap<EquipmentSlot, ItemStack?>>()

    fun start() {
        // When starting, the first tick should ALWAYS be present with a PlayerMovement
        // TODO: Remove this, because we now support multiple players
        /* addActionRelativeTick(
            0,
            AnimationAction.PlayerMovement(
                player.uniqueId.toString(),
                AbsoluteLocation.toAbsoluteLocation(player.location),
                player.bodyYaw,
                player.isSneaking,
                player.isSprinting,
                player.pose,
            )
        ) */

        val originalBlocksSnapshot = snapshotRegionBlockStates()
        this.originalBlocksSnapshot.putAll(originalBlocksSnapshot)
        this.currentBlocksSnapshot.putAll(originalBlocksSnapshot)

        this.job = m.launchMainThread {
            while (true) {
                val currentTick = Bukkit.getCurrentTick()

                measureTime {
                    for (entity in world.getEntitiesByClass(Player::class.java)) {
                        val relTick = currentTick - startedAtTick
                        entity.sendActionBar("RECORDING! Tick $relTick")

                        if (entity.location.isBetween(minBlockSnapshot, maxBlockSnapshot)) {
                            addAction(
                                currentTick,
                                AnimationAction.PlayerMovement(
                                    player.uniqueId.toString(),
                                    AbsoluteLocation.toAbsoluteLocation(player.location),
                                    player.bodyYaw,
                                    player.isSneaking,
                                    player.isSprinting,
                                    player.pose,
                                )
                            )

                            val activeEquipments = trackedActiveEquipments.getOrPut(entity) { mutableMapOf() }
                            for (equipmentSlot in PLAYER_EQUIPMENT_SLOTS) {
                                // Should NEVER be null but due to Paper nullability changes it seems that this is borked
                                // https://github.com/PaperMC/Paper/commit/61312fdb592766e3662c50a60ee23f55da6aac3e
                                val itemStack = player.equipment!!.getItem(equipmentSlot)
                                if (activeEquipments[equipmentSlot] != itemStack) {
                                    addAction(
                                        currentTick,
                                        AnimationAction.ActiveEquipment(
                                            player.uniqueId.toString(),
                                            equipmentSlot,
                                            if (itemStack.type == Material.AIR)
                                                null
                                            else
                                                ItemUtils.serializeItemToBase64(player.equipment!!.getItem(equipmentSlot))
                                        )
                                    )

                                    activeEquipments[equipmentSlot] = itemStack
                                }
                            }
                        }
                    }
                }// .let { Bukkit.broadcastMessage("Took $it to process players") }

                val currentBlocksSnapshot = snapshotRegionBlockStates()

                var isDirty = false
                measureTime {
                    for (currentBlockSnapshot in currentBlocksSnapshot) {
                        val og = this@TheaterMagicRecordingAnimation.currentBlocksSnapshot[currentBlockSnapshot.key]!!

                        if (og.blockData != currentBlockSnapshot.value.blockData) {
                            // Bukkit.broadcastMessage("Changed block!")

                            isDirty = true
                            addAction(
                                currentTick,
                                AnimationAction.UpdatedBlock(
                                    AbsoluteLocation(
                                        currentBlockSnapshot.value.x.toDouble(),
                                        currentBlockSnapshot.value.y.toDouble(),
                                        currentBlockSnapshot.value.z.toDouble(),
                                        0.0f,
                                        0.0f
                                    ),
                                    currentBlockSnapshot.value.blockData
                                )
                            )
                        }
                    }

                    if (isDirty) {
                        this@TheaterMagicRecordingAnimation.currentBlocksSnapshot.clear()
                        this@TheaterMagicRecordingAnimation.currentBlocksSnapshot.putAll(currentBlocksSnapshot)
                    }
                }// .let { Bukkit.broadcastMessage("Took $it to process snapshots") }

                delayTicks(1L)
            }
        }
    }

    fun snapshotRegionBlockStates(): Map<BlockPosition, StoredBlockData> {
        return BlockUtils.getBlocksFromTwoLocations(minBlockSnapshot, maxBlockSnapshot)
            .associate {
                val blockData = it.blockData
                BlockPosition(it.x, it.y, it.z) to StoredBlockData(
                    it.x,
                    it.y,
                    it.z,
                    blockData.asString
                )
            }
    }

    fun finish(): TheaterMagicStoredRecordingAnimation {
        job?.cancel()
        m.theaterMagicManager.recordingAnimations.remove(player, this)

        for (block in originalBlocksSnapshot) {
            val currentBlock = this@TheaterMagicRecordingAnimation.currentBlocksSnapshot[block.key]!!

            if (block.value.blockData != currentBlock.blockData) {
                // Bukkit.broadcastMessage("Reverting original blocks (maybe)")
                addActionRelativeTick(
                    0,
                    AnimationAction.UpdatedBlock(
                        AbsoluteLocation(
                            block.value.x.toDouble(),
                            block.value.y.toDouble(),
                            block.value.z.toDouble(),
                            0.0f,
                            0.0f
                        ),
                        block.value.blockData
                    )
                )
            }
        }

        return TheaterMagicStoredRecordingAnimation(keyframes.mapValues { TheaterMagicStoredRecordingAnimation.RecordedKeyframe(it.value.actions) })
    }

    fun addAction(currentTick: Int, action: AnimationAction) {
        addActionRelativeTick(currentTick - startedAtTick, action)
    }

    fun addActionRelativeTick(relativeTick: Int, action: AnimationAction) {
        keyframes.getOrPut(relativeTick) { Keyframe() }.actions.add(action)
    }

    class Keyframe {
        val actions = mutableListOf<AnimationAction>()
    }

    data class BlockPosition(
        val x: Int,
        val y: Int,
        val z: Int
    )
}