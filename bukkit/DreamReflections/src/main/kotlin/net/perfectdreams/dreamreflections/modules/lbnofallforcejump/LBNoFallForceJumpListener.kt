package net.perfectdreams.dreamreflections.modules.lbnofallforcejump

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class LBNoFallForceJumpListener(private val m: DreamReflections) : Listener {
    // Detects LiquidBounce's "Force Jump"
    @EventHandler
    fun onJump(e: PlayerJumpEvent) {
        if (e.player.fallDistance >= e.player.getAttribute(Attribute.SAFE_FALL_DISTANCE)!!.value) {
            val session = m.getActiveReflectionSessionIfNotBedrockClient(e.player) ?: return
            session.lbNoFallForceJump.increaseViolationLevel()
            e.isCancelled = true
        }
    }
}