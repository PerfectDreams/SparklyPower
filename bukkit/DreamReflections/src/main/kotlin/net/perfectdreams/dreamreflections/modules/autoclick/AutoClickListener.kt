package net.perfectdreams.dreamreflections.modules.autoclick

import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class AutoClickListener(val m: DreamReflections) : Listener {
    @EventHandler
    fun onSwing(e: ServerboundPacketReceiveEvent) {
        if (e.packet is ServerboundSwingPacket) {
            val session = m.getActiveReflectionSession(e.player) ?: return
            session.swingsPerSecond.addNewSwing()
        }
    }
}