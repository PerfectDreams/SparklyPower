package net.perfectdreams.dreamreflections.modules.wurstnofall

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import kotlinx.datetime.Clock
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class WurstNoFallListener(private val m: DreamReflections) : Listener {
    // Blocks Wurst's simple NoFall hack
    @EventHandler
    fun onPacket(e: ServerboundPacketReceiveEvent) {
        val packet = e.packet
        if (packet is ServerboundMovePlayerPacket.StatusOnly) {
            val session = m.getActiveReflectionSession(e.player) ?: return

            if (packet.isOnGround) {
                session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrueOnThisTick = true
                if (session.wurstNoFall.ignoreStatusOnlyPacketsUntil > System.currentTimeMillis()) {
                    e.isCancelled = true
                }
            }
        } else if (packet is ServerboundMovePlayerPacket) {
            val session = m.getActiveReflectionSession(e.player) ?: return

            session.wurstNoFall.receivedMovementPacketOnThisTick = true
        }
    }

    @EventHandler
    fun onTick(e: ServerTickEndEvent) {
        val expiresAfter = System.currentTimeMillis() + 5_000

        for (session in m.activeReflectionSessions.values) {
            if (session.wurstNoFall.receivedMovementPacketOnThisTick && session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrueOnThisTick) {
                session.wurstNoFall.increaseViolationLevel()
                session.wurstNoFall.ignoreStatusOnlyPacketsUntil = expiresAfter
            }

            session.wurstNoFall.receivedMovementPacketOnThisTick = false
            session.wurstNoFall.receivedStatusOnlyPacketWithOnGroundTrueOnThisTick = false
        }
    }
}