package net.perfectdreams.dreamreflections.modules.gamestate

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreamcore.utils.packetevents.ClientboundPacketSendEvent
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Updates our cached client-side game state
 */
class GameStateListener(private val m: DreamReflections) : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPacketSend(e: ClientboundPacketSendEvent) {
        val packet = e.packet

        if (packet is ClientboundPlayerPositionPacket) {
            val session = m.getActiveReflectionSession(e.player) ?: return
            session.clientGameState.awaitingTeleportConfirmation = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPacketReceive(e: ServerboundPacketReceiveEvent) {
        val packet = e.packet

        val session = m.getActiveReflectionSession(e.player) ?: return

        if (packet is ServerboundAcceptTeleportationPacket) {
            session.clientGameState.awaitingTeleportConfirmation = false
            return
        }

        if (packet is ServerboundMovePlayerPacket) {
            session.clientGameState.isOnGround = packet.isOnGround
            return
        }
    }
}