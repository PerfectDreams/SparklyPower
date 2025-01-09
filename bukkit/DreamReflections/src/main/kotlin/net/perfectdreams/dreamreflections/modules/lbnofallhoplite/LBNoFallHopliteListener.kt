package net.perfectdreams.dreamreflections.modules.lbnofallhoplite

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class LBNoFallHopliteListener(private val m: DreamReflections) : Listener {
    // Detects LiquidBounce's "Hoplite"/Grim bypass hack
    // We don't attempt to mitigate here, just detection, because we already have a better, more "generic", NoFall detection mechanism
    @EventHandler
    fun onPacket(e: ServerboundPacketReceiveEvent) {
        val packet = e.packet

        if (packet is ServerboundMovePlayerPacket) {
            val session = m.getActiveReflectionSession(e.player) ?: return

            val previousMovePacket = session.lbNoFallHoplite.previousMovePacket
            if (previousMovePacket != null) {
                val diff = packet.y - previousMovePacket.y

                if (diff == 1.0000036354540498E-9) {
                    session.lbNoFallHoplite.increaseViolationLevel()
                }
            }
            session.lbNoFallHoplite.previousMovePacket = packet
        }
    }
}