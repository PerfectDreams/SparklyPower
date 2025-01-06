package net.perfectdreams.dreamreflections.modules.wurstnofall

import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class WurstNoFallListener(private val m: DreamReflections) : Listener {
    // Blocks Wurst's simple NoFall hack
    @EventHandler
    fun onPacket(e: ServerboundPacketReceiveEvent) {
        val packet = e.packet

        // We ignore this because this causes the nofallchecker to bork out, but that's due to the order of when the player is teleported and stuff
        if (packet is ServerboundClientTickEndPacket)
            return

        val session = m.getActiveReflectionSession(e.player) ?: return

        // Wurst always sends the StatusOnly move packet first with the "onGround=true"
        if (packet is ServerboundMovePlayerPacket.StatusOnly) {
            if (packet.isOnGround) {
                session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrue = true
                if (session.wurstNoFall.ignoreStatusOnlyPacketsUntil > System.currentTimeMillis()) {
                    e.isCancelled = true
                }
            } else {
                session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrue = false
            }
        } else if (packet is ServerboundMovePlayerPacket) {
            // Does this contradict what the client said before?
            if (!packet.isOnGround && session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrue) {
                // Uh oh... someone's CHEATING!!!
                session.wurstNoFall.ignoreStatusOnlyPacketsUntil = System.currentTimeMillis() + 5_000
                session.wurstNoFall.increaseViolationLevel()
            }

            // Reset state!
            session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrue = false
        } else {
            // If not, then it is business as always...
            session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrue = false
        }
    }
}