package net.perfectdreams.dreamreflections.modules.wurstnofall

import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class WurstNoFallListener(private val m: DreamReflections) : Listener {
    // Detects Wurst's simple NoFall hack
    // We don't attempt to mitigate here, just detection, because we already have a better, more "generic", NoFall detection mechanism
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
            } else {
                session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrue = false
            }
        } else if (packet is ServerboundMovePlayerPacket) {
            // Does this contradict what the client said before?
            if (!packet.isOnGround && session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrue) {
                // Uh oh... someone's CHEATING!!!

                // This seems weird... "why don't you add this check before everything?"
                // Well the reason is that, if we don't process the movement packets while awaiting teleport, OTHER false positives arise
                //
                // Ignore any movement packets if we are awaiting a teleport confirmation
                // This is what the vanilla server does, and we NEED to do this to work around some janky suspicious packet from vanilla clients
                // (This happens a lot when teleporting to a new location rapidly, like going up elevators in SparklyPower)
                if (!session.clientGameState.awaitingTeleportConfirmation) {
                    session.wurstNoFall.increaseViolationLevel()
                }
            }

            // Reset state!
            session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrue = false
        } else {
            // If not, then it is business as always...
            session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrue = false
        }
    }
}