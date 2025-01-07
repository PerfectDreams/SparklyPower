package net.perfectdreams.dreamreflections.modules.wurstcreativeflight

import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket
import net.perfectdreams.dreamcore.utils.extensions.sendPacket
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class WurstCreativeFlightListener(private val m: DreamReflections) : Listener {
    // Blocks Wurst's simple CreativeFlight hack
    @EventHandler
    fun onPacket(e: ServerboundPacketReceiveEvent) {
        val packet = e.packet

        if (packet is ServerboundPlayerAbilitiesPacket) {
            // Yes... I don't know why they don't filter the abilities packet when CreativeFlight is enabled...
            if (packet.isFlying && !e.player.allowFlight) {
                val session = m.getActiveReflectionSession(e.player) ?: return
                session.wurstCreativeFlight.increaseViolationLevel()

                val craftPlayer = (e.player as CraftPlayer)
                val nmsPlayer = craftPlayer.handle

                // Run it back - resync!
                e.player.sendPacket(ClientboundPlayerAbilitiesPacket(nmsPlayer.abilities))
            }
            return
        }
    }
}