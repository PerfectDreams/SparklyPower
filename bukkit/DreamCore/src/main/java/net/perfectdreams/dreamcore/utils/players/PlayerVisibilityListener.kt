package net.perfectdreams.dreamcore.utils.players

import io.papermc.paper.event.player.PlayerTrackEntityEvent
import net.perfectdreams.dreamcore.DreamCore
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent

class PlayerVisibilityListener(val m: DreamCore) : Listener {
    @EventHandler
    fun onTrack(e: PlayerTrackEntityEvent) {
        val otherPlayer = e.entity as? Player ?: return

        val manager = m.getPlayerVisibilityManager(e.player)
        if (manager?.isHidden(otherPlayer) == true) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        m.removePlayerVisibilityManager(e.player)
    }

    @EventHandler
    fun onPluginDisable(e: PluginDisableEvent) {
        // When the plugin is disabled, clean up any players that are hidden by them
        for ((player, manager) in m.playerVisibilityManagers) {
            if (manager.isHiddenByPlugin(e.plugin, player)) {
                manager.showPlayer(e.plugin, player)
            }
        }
    }
}