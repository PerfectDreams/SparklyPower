package net.perfectdreams.dreamcore.utils.players

import io.papermc.paper.event.player.PlayerTrackEntityEvent
import net.perfectdreams.dreamcore.DreamCore
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
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

    // This MUST be in MONITOR priority, and other plugins should NOT use MONITOR priority
    // If else, a plugin that is calling show/hide player during quit WILL cause issues when the player relogs
    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(e: PlayerQuitEvent) {
        m.removePlayerVisibilityManager(e.player)

        // Also remove the player from ALL active visibility managers
        // This is how Paper's showPlayer/hidePlayer also works
        for (visibilityManager in m.playerVisibilityManagers) {
            // Technically we don't need to use "show" because the player is quitting anyway
            visibilityManager.value.invertedVisibilityEntities.remove(e.player.uniqueId)
        }
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