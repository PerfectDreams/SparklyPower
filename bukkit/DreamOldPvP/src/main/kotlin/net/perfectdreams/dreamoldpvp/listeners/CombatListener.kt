package net.perfectdreams.dreamoldpvp.listeners

import net.perfectdreams.dreamoldpvp.DreamOldPvP
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent

class CombatListener(val m: DreamOldPvP) : Listener {
    companion object {
        var newPvPAttackSpeed = 4.0
        var oldPvPAttackSpeed = 21.0
    }

    @EventHandler
    fun onPlayerRespawn(e: PlayerRespawnEvent) {
        adjustAttackSpeed(e.player)
    }

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        adjustAttackSpeed(e.player)
    }

    @EventHandler
    fun onPlayerTeleport(e: PlayerTeleportEvent) {
        adjustAttackSpeed(e.player)
    }

    @EventHandler
    fun onPlayerInteract(e: PlayerInteractEvent) {
        adjustAttackSpeed(e.player)
    }

    @EventHandler
    fun onWorldChange(e: PlayerChangedWorldEvent) {
        adjustAttackSpeed(e.player)
    }

    private fun adjustAttackSpeed(player: Player) {
        val attackSpeed = getAttackSpeed(player)

        if (attackSpeed == newPvPAttackSpeed) {
            m.logger.info { "Player ${player.name} doesn't have the correct attack speed, adjusting to ${oldPvPAttackSpeed}!" }

            player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = oldPvPAttackSpeed
            player.saveData()
        }
    }

    private fun getAttackSpeed(player: Player): Double? {
        return player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue
    }
}