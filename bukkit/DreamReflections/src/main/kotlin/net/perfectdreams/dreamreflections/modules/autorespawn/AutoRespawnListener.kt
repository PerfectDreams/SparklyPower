package net.perfectdreams.dreamreflections.modules.autorespawn

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket
import net.perfectdreams.dreamcore.utils.packetevents.ClientboundPacketSendEvent
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class AutoRespawnListener(val m: DreamReflections) : Listener {
    private val respawnTime = mutableMapOf<Player, Long>()

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        respawnTime.remove(e.player)
    }

    @EventHandler
    fun onClientbound(e: ClientboundPacketSendEvent) {
        val packet = e.packet
        if (packet is ClientboundPlayerCombatKillPacket) {
            respawnTime[e.player] = System.currentTimeMillis()
        }
    }

    @EventHandler
    fun onServerbound(e: ServerboundPacketReceiveEvent) {
        val packet = e.packet
        if (packet is ServerboundClientCommandPacket) {
            if (packet.action == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN) {
                val whenWasKilled = respawnTime[e.player] ?: return

                // If the player respawned in less than >200ms, then it means that the player is using AutoRespawn!
                m.getActiveReflectionSession(e.player)?.autoRespawn?.trackTime(
                    System.currentTimeMillis() - whenWasKilled
                )
            }
        }
    }
}