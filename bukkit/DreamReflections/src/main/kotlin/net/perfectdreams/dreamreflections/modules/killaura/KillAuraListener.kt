package net.perfectdreams.dreamreflections.modules.killaura

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent
import net.perfectdreams.dreamreflections.DreamReflections
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

class KillAuraListener(private val m: DreamReflections) : Listener {
    @EventHandler
    fun onDamageFakePlayer(e: PlayerUseUnknownEntityEvent) {
        if (e.isAttack) {
            val killAuraTester = m.killAuraTesters[e.player] ?: return
            val fakePlayer = killAuraTester.fakePlayersAlive.firstOrNull { it.networkId == e.entityId }

            if (fakePlayer != null) {
                // Bukkit.broadcastMessage("Killed $fakePlayer")
                killAuraTester.removeFakePlayerHitByPlayer(fakePlayer)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamagePlayer(e: EntityDamageByEntityEvent) {
        val damager = e.damager
        val entity = e.entity

        if (damager is Player && entity is LivingEntity) {
            val session = m.getActiveReflectionSession(damager) ?: return

            val diff = System.currentTimeMillis() - session.killAura.lastAutomaticKillAuraCheck

            if (diff >= 300_000) {
                val success = m.spawnKillAuraTester(damager)
                if (success) {
                    session.killAura.lastAutomaticKillAuraCheck = System.currentTimeMillis()
                }
            }
        }
    }
}