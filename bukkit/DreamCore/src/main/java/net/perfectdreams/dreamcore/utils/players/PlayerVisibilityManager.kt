package net.perfectdreams.dreamcore.utils.players

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.server.level.ServerLevel
import net.perfectdreams.dreamcore.utils.extensions.sendPacket
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.set

/**
 * Hides a player from the world, but does not hide them from the player list
 */
class PlayerVisibilityManager(val selfPlayer: Player) {
    companion object {
        private val pluginWeakReferences: WeakHashMap<Plugin, WeakReference<Plugin>> = WeakHashMap()

        private fun getPluginWeakReference(plugin: Plugin): WeakReference<Plugin> {
            return pluginWeakReferences.computeIfAbsent(plugin) { referent: Plugin -> WeakReference(referent) }
        }
    }

    private val invertedVisibilityEntities: MutableMap<UUID, MutableSet<WeakReference<Plugin>>> = Object2ObjectOpenHashMap()

    fun isHidden(player: Player): Boolean {
        return invertedVisibilityEntities.contains(player.uniqueId)
    }

    fun isHiddenByPlugin(plugin: Plugin, player: Player): Boolean {
        return invertedVisibilityEntities[player.uniqueId]?.contains(PlayerVisibilityManager.getPluginWeakReference(plugin)) == true
    }

    fun hidePlayer(plugin: Plugin, other: Player) {
        // Don't attempt to hide yourself, silly!
        if (other == this.selfPlayer)
            return

        val shouldHide = addInvertedVisibility(plugin, other)

        if (shouldHide) {
            this.selfPlayer.sendPacket(ClientboundRemoveEntitiesPacket(other.entityId))
        }
    }

    fun showPlayer(plugin: Plugin, other: Player) {
        // Don't attempt to show yourself, silly!
        if (other == this.selfPlayer)
            return

        val shouldShow = removeInvertedVisibility(plugin, other)
        if (shouldShow) {
            val selfCraftPlayer = (this.selfPlayer as CraftPlayer)

            // This is very confusing but that's how CraftPlayer#showEntity works, we need to copy the code because, by default, it doesn't attempt to "retrack" entities that are already tracked
            // But for our use case, we NEED to retrack because we aren't untracking on the server
            val tracker = (selfCraftPlayer.handle.level() as ServerLevel).getChunkSource().chunkMap

            val entry = tracker.entityMap.get(other.entityId)

            // The "!entry.seenBy.contains(this.getHandle().connection)" check is removed
            if (entry != null) {
                // We need to do this because the "onPlayerAdd" function is not called if we are already "seeing" the entity
                // The reason we don't remove it on the "hidePlayer" call, is because the "track" event is only called AFTER the entity is added to the seenBy list
                entry.seenBy.remove(selfCraftPlayer.handle.connection)

                // Attempt to retrack the tracked entity
                entry.updatePlayer(selfCraftPlayer.handle)
            }
        }
    }

    private fun addInvertedVisibility(plugin: Plugin, entity: Entity): Boolean {
        var invertedPlugins: MutableSet<WeakReference<Plugin>>? = invertedVisibilityEntities[entity.uniqueId]
        if (invertedPlugins != null) {
            // Some plugins are already inverting the entity. Just mark that this
            // plugin wants the entity inverted too and end.
            invertedPlugins.add(PlayerVisibilityManager.getPluginWeakReference(plugin))
            return false
        }
        invertedPlugins = HashSet()
        invertedPlugins.add(PlayerVisibilityManager.getPluginWeakReference(plugin))
        invertedVisibilityEntities[entity.uniqueId] = invertedPlugins

        return true
    }

    private fun removeInvertedVisibility(plugin: Plugin, entity: Entity): Boolean {
        val invertedPlugins = invertedVisibilityEntities[entity.uniqueId] ?: return false // Entity isn't inverted
        invertedPlugins.remove(PlayerVisibilityManager.getPluginWeakReference(plugin))
        if (!invertedPlugins.isEmpty()) {
            return false // Some other plugins still want the entity inverted
        }
        invertedVisibilityEntities.remove(entity.uniqueId)

        return true
    }
}