package net.perfectdreams.dreamemotes

import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.perfectdreams.dreamcore.utils.LocationUtils
import net.perfectdreams.dreamcore.utils.extensions.sendPacket
import org.bukkit.Location
import org.bukkit.craftbukkit.entity.CraftArmorStand
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Vector3f

class OrbitalCamera(
    val m: DreamEmotes,
    val player: Player,
    val center: Location,
    val camera: Entity
) {
    companion object {
        const val CAMERA_SPEED = 0.05f
    }

    var pitch = 0.0f
    var yaw = 0.0f
    var alive = true

    fun update() {
        val matrix4f = Matrix4f()
        val vector = Vector3f(3.0f, 0.0f, 0.0f)

        // The * CAMERA_SPEED decreases the velocity of the camera
        val targetPlayerYaw = (360 - ((yaw * CAMERA_SPEED) + 180) % 360) % 360
        val targetPlayerPitch = pitch * CAMERA_SPEED
        // matrix4f.rotateX(Math.toRadians(xRot.toDouble() % 180).toFloat())
        // matrix4f.rotateY(targetPlayerYaw)

        // y is the horizontal rotation (yaw)
        matrix4f.rotateY(targetPlayerYaw)
        // z is the vertical rotation (pitch)

        matrix4f.rotateZ(targetPlayerPitch)

        val newPosition = matrix4f.transformPosition(vector)

        // Bukkit.broadcastMessage("Transformed Vector: ${newPosition.x}; ${newPosition.y}; ${newPosition.z}")
        val l = center.clone().add(newPosition.x.toDouble(), newPosition.y.toDouble(), newPosition.z.toDouble())
        val newLoc = LocationUtils.getLocationLookingAt(l, center)

        val a = newLoc.world.rayTraceBlocks(center, Vector(vector.x, vector.y, vector.z), 5.0)

        if (a != null) {
            val hitBlock = a.hitBlock
            if (hitBlock != null) {
                newLoc.y = hitBlock.y.toDouble() + 1.1
            }
        }

        camera.teleport(newLoc)
    }

    fun stop() {
        alive = false
    }
}