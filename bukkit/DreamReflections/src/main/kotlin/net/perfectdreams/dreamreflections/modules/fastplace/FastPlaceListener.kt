package net.perfectdreams.dreamreflections.modules.fastplace

import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent

class FastPlaceListener(val m: DreamReflections) : Listener {
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    fun onPacket(e: ServerboundPacketReceiveEvent) {
        val packet = e.packet
        if (packet is ServerboundUseItemOnPacket) {
            val session = m.getActiveReflectionSession(e.player) ?: return

            val now = System.currentTimeMillis()
            val diff = now - session.fastPlace.lastPlacedBlockTime
            if (diff >= 50) {
                // e.isCancelled = true
                // session.fastPlace.increaseViolationLevel()
            }
            session.fastPlace.lastPlacedBlockTime = now
        }
    }
}