package net.perfectdreams.dreamreflections.modules.nofall

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.perfectdreams.dreamreflections.DreamReflections
import net.sparklypower.sparklypaper.event.player.PlayerPreMoveEvent
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import kotlin.jvm.optionals.getOrNull

class NoFallListener(private val m: DreamReflections) : Listener {
    // Blocks NoFall by checking if we are being supported by a block or not
    // Requires SparklyPaper for the PlayerPreMoveEvent!
    // We do this using events because it is easier for us, we have a "source of truth" (the server world) and this way it is WAY simpler compared to NoCheatPlus' gigantic NoFall checks
    // This works for simple NoFall hacks (like Wurst's NoFall) and even with complex NoFall hacks (like LiquidBounce's ground spoofer)

    @EventHandler
    fun onPreMove(e: PlayerPreMoveEvent) {
        val cplayer = (e.player as CraftPlayer)
        val nmsPlayer = cplayer.handle

        // Get the NEW player position and the OLD position to avoid any false positives
        val playerPastBoundingBox = makeCheckBelowBoundingBox(nmsPlayer.getBoundingBoxAt(e.from.x, e.from.y, e.from.z))
        val playerFutureBoundingBox = makeCheckBelowBoundingBox(nmsPlayer.getBoundingBoxAt(e.to.x, e.to.y, e.to.z))

        // Supporting Me, heh https://youtu.be/VXeIRsqqFbA
        val positionOfBlockThatIsSupportingMe = getBlockPosThatIsSupportingMeWithoutTransformingAABB(nmsPlayer, playerFutureBoundingBox) ?: getBlockPosThatIsSupportingMeWithoutTransformingAABB(nmsPlayer, playerPastBoundingBox)

        val clientSideIsOnGround = e.isOnGround
        // val original2 = e.isResetFallDistance
        // val yDiff = e.to.y - e.from.y
        // val isJumping = 0 >= yDiff
        val isBeingSupported: Boolean

        if (positionOfBlockThatIsSupportingMe != null) {
            // We are supported!
            isBeingSupported = true
        } else {
            // If we aren't supported by any block...

            // Maybe we on top of an entity?
            var supportedByEntities = nmsPlayer.level().getEntities(
                nmsPlayer,
                playerFutureBoundingBox
            ).isNotEmpty()

            if (!supportedByEntities) {
                // Maybe we were supported in the past?
                supportedByEntities = nmsPlayer.level().getEntities(
                    nmsPlayer,
                    playerPastBoundingBox
                ).isNotEmpty()
            }

            if (!supportedByEntities) {
                // We aren't supported by anything then, set to false!
                isBeingSupported = false
            } else {
                // We are supported by entities!
                isBeingSupported = true
            }
        }

        if (isBeingSupported) {
            e.isOnGround = true
            e.isResetFallDistance = true
        } else {
            e.isOnGround = false
            e.isResetFallDistance = false
        }

        // Check mismatched onGround
        // We only care about mismatched if the client said that they WERE on ground, but we calculated and found out that we AREN'T on ground
        // We don't need to check the fall distance, but if the value is wrong AND they were going to take fall damage, then it means that the client is probably lying!
        if (clientSideIsOnGround && !isBeingSupported && e.player.fallDistance >= e.player.getAttribute(Attribute.SAFE_FALL_DISTANCE)!!.value) {
            // Uuhh, we got mismatched onGround input! This may be NoFall!
            val session = m.getActiveReflectionSession(e.player) ?: return
            session.noFall.increaseViolationLevel()
        }
    }

    private fun makeCheckBelowBoundingBox(axisalignedbb: AABB): AABB {
        return AABB(
            axisalignedbb.minX,
            axisalignedbb.minY - 1.0E-6,
            axisalignedbb.minZ,
            axisalignedbb.maxX,
            axisalignedbb.minY,
            axisalignedbb.maxZ
        )
    }

    private fun getBlockPosThatIsSupportingMeWithoutTransformingAABB(player: Player, axisalignedbb: AABB): BlockPos? {
        return player.level().findSupportingBlock(player, axisalignedbb).getOrNull()
    }
}