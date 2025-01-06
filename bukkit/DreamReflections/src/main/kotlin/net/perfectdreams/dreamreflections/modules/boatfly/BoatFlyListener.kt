package net.perfectdreams.dreamreflections.modules.boatfly

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import kotlinx.datetime.Clock
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamreflections.DreamReflections
import net.perfectdreams.dreamreflections.tables.PlayerViolations
import net.sparklypower.sparklypaper.event.player.PlayerMoveControllableVehicleEvent
import org.bukkit.block.BlockFace
import org.bukkit.craftbukkit.entity.CraftAbstractHorse
import org.bukkit.entity.AbstractHorse
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.util.Vector
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class BoatFlyListener(val m: DreamReflections) : Listener {
    companion object {
        private val ZERO = Vector(0.0, 0.0, 0.0)
    }

    private val accumulatedVerticalHeightEntities = mutableMapOf<Entity, Double>()

    @EventHandler
    fun onMoveControllableVehicle(e: PlayerMoveControllableVehicleEvent) {
        val vehicle = e.vehicle

        // Only specific vehicles are affected by the VehicleMoveEvent/ServerboundMoveVehiclePacket
        // - Boats
        // - Horses
        // - Pigs
        // - Striders
        // To see which mobs are affected, see implementations of NMS' getControllingPassenger in LivingEntity
        // Minecarts are NOT affected by this event
        if (!vehicle.isInWaterOrBubbleColumn && !vehicle.isInLava && !vehicle.isOnGround && vehicle.velocity == ZERO && e.to.y > e.from.y) {
            // The wonky block check is here to avoid horses and other vehicles borking when walking up stairs/slabs
            val isOnTopOfWonkyBlock = !vehicle.location.block.getRelative(BlockFace.DOWN).type.isSolid || !vehicle.location.block.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN).type.isSolid
            if (!isOnTopOfWonkyBlock)
                return

            val yDiff = e.to.y - e.from.y
            val accumulatedVerticalHeight = accumulatedVerticalHeightEntities.getOrDefault(vehicle, 0.0) + yDiff

            if (accumulatedVerticalHeight > 1.0) {
                if (vehicle is AbstractHorse) {
                    // TODO: Add API for this
                    val c = (vehicle as CraftAbstractHorse).handle
                    val f = net.minecraft.world.entity.animal.horse.AbstractHorse::class.java.getDeclaredField("allowStandSliding")
                    f.isAccessible = true
                    val value = f.getBoolean(c)
                    if (value) {
                        // Bukkit.broadcastMessage("Ignoring cancellation because the horse was jumping (stand sliding)...")
                        accumulatedVerticalHeightEntities.remove(e.vehicle)
                        return
                    } else {
                        // Bukkit.broadcastMessage("Cancelled! ${vehicle.isJumping} - ${vehicle.velocity} - yDiff: ${yDiff} - Accumulated: $accumulatedVerticalHeight")
                    }
                } else {
                    // Bukkit.broadcastMessage("Cancelled! - ${vehicle.velocity} - yDiff: ${yDiff} - Accumulated: $accumulatedVerticalHeight")
                }

                e.isCancelled = true

                val session = m.getActiveReflectionSession(e.player) ?: return
                session.boatFly.increaseViolationLevel()
            } else {
                accumulatedVerticalHeightEntities[e.vehicle] = accumulatedVerticalHeight
            }
        } else {
            if (vehicle.isOnGround) {
                // Reset ONLY if it is on ground, this avoids a bug that it is constantly reset after being cancelled
                accumulatedVerticalHeightEntities.remove(vehicle)
            }
        }
    }

    @EventHandler
    fun onRemove(e: EntityRemoveFromWorldEvent) {
        accumulatedVerticalHeightEntities.remove(e.entity)
    }
}