package net.perfectdreams.dreamxizum.listeners

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.isWithinRegion
import net.perfectdreams.dreamcore.utils.extensions.worldGuardRegions
import net.perfectdreams.dreamcore.utils.get
import net.perfectdreams.dreamcore.utils.remove
import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.utils.XizumBattleResult
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.craftbukkit.entity.CraftArrow
import org.bukkit.craftbukkit.entity.CraftEnderPearl
import org.bukkit.craftbukkit.entity.CraftTrident
import org.bukkit.craftbukkit.entity.CraftWindCharge
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionType
import java.util.UUID

class BattleListener(val m: DreamXizum) : Listener {
    companion object {
        // <lastused> to <player>
        var enderPearlCooldown = hashMapOf<UUID, Long>()

        val wg = WorldGuard.getInstance()
    }

    @EventHandler
    fun onPlayerDisconnect(e: PlayerQuitEvent) {
        if (e.player.persistentDataContainer.get(DreamXizum.IS_IN_CAMAROTE) == true) {
            e.player.persistentDataContainer.remove(DreamXizum.IS_IN_CAMAROTE)
        }

        if (m.queue.any { it.player == e.player || it.opponent == e.player }) {
            val request = m.queue.first { it.player == e.player }
            m.queue.remove(request)
            return
        }

        if (!m.activeBattles.any { it.player == e.player || it.opponent == e.player })
            return

        val battle = m.activeBattles.first { it.player == e.player || it.opponent == e.player }

        if (battle.started || battle.countdown) {
            battle.end(e.player, XizumBattleResult.DISCONNECTION)
            m.activeBattles.remove(battle)
        }
    }

    @EventHandler
    fun onPlayerDrop(e: PlayerDropItemEvent) {
        // check if the player is in camarote region on world guard
        // let's deprecate the key usage and use the world guard region instead
        val player = e.player

        if (player.location.isWithinRegion("camarote")) {
            if (player.location.worldGuardRegions.any { it.id.contains("arenaxizum") })
                return

            e.isCancelled = true

            player.sendMessage(textComponent {
                append(DreamXizum.prefix())
                appendSpace()
                append("§cVocê não pode dropar itens enquanto estiver no camarote!")
            })

            return
        }
    }

    @EventHandler
    fun onPlayerKick(e: PlayerKickEvent) {
        if (!m.activeBattles.any { it.player == e.player || it.opponent == e.player })
            return

        val battle = m.activeBattles.first { it.player == e.player || it.opponent == e.player }

        if (battle.started || battle.countdown) {
            battle.draw()
            m.activeBattles.remove(battle)
        }
    }

    @EventHandler
    fun onPlayerDeath(e: PlayerDeathEvent) {
        if (m.queue.any { it.player == e.player || it.opponent == e.player }) {
            val request = m.queue.first { it.player == e.player }
            m.queue.remove(request)
            return
        }

        if (e.entity.persistentDataContainer.get(DreamXizum.IS_IN_CAMAROTE) == true) {
            e.entity.persistentDataContainer.remove(DreamXizum.IS_IN_CAMAROTE)
        }

        if (!m.activeBattles.any { it.player == e.player || it.opponent == e.player })
            return

        val battle = m.activeBattles.first { it.player == e.player || it.opponent == e.player }

        if (battle.started || battle.countdown) {
            battle.end(e.player, XizumBattleResult.KILLED)
            m.activeBattles.remove(battle)
        }
    }

    @EventHandler
    fun onPlayerTeleport(e: PlayerTeleportEvent) {
        val player = e.player

        if (player.persistentDataContainer.get(DreamXizum.IS_IN_CAMAROTE) == true) {
            if (e.to.world.name != m.arenas.first().data.worldName) {
                player.persistentDataContainer.remove(DreamXizum.IS_IN_CAMAROTE)
            }
        }

        if (DreamCore.dreamConfig.blacklistedWorldsTeleport.contains(e.to.world.name)) {
            if (m.queue.any { it.player == e.player || it.opponent == e.player }) {
                val request = m.queue.first { it.player == e.player }
                m.queue.remove(request)

                player.sendMessage(textComponent {
                    append(DreamXizum.prefix())
                    appendSpace()
                    append("Você foi removido da fila do x1!")
                })
                return
            }

            return
        }

        if (!m.activeBattles.any { it.player == e.player || it.opponent == e.player })
            return

        if (e.cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            // remove half block from the position cuz the ender pearl is bugged and can teleport the player above a roof or beyond a wall
            e.to.y -= 0.5
            e.to.x -= 0.5
            e.to.z -= 0.5

            return
        }

        val battle = m.activeBattles.first { it.player == e.player || it.opponent == e.player }

        if (battle.started || battle.countdown) {
            battle.end(e.player, XizumBattleResult.RAN)
            m.activeBattles.remove(battle)
        }
    }

    @EventHandler
    fun onPotionThrow(e: PotionSplashEvent) {
        if (e.potion.shooter !is Player)
            return

        if (!m.activeBattles.any { it.player == e.potion.shooter || it.opponent == e.potion.shooter })
            return

        if (e.potion.potionMeta.basePotionType == PotionType.INFESTED || e.potion.potionMeta.basePotionType == PotionType.OOZING) {
            e.isCancelled = true
            return
        }
    }

    @EventHandler
    fun onPlayerMovement(e: PlayerMoveEvent) {
        if (!m.activeBattles.any { it.player == e.player || it.opponent == e.player })
            return

        val battle = m.activeBattles.first { it.player == e.player || it.opponent == e.player }

        if (!battle.started && battle.countdown) {
            e.isCancelled = true
            return
        }
    }

    @EventHandler
    fun onPlayerJump(e: PlayerJumpEvent) {
        if (!m.activeBattles.any { it.player == e.player || it.opponent == e.player })
            return

        val battle = m.activeBattles.first { it.player == e.player || it.opponent == e.player }

        if (!battle.started && battle.countdown) {
            e.isCancelled = true
            return
        }
    }

    @EventHandler
    fun onCommand(e: PlayerCommandPreprocessEvent) {
        if (!m.activeBattles.any { it.player == e.player || it.opponent == e.player })
            return

        e.isCancelled = true
    }

    @EventHandler
    fun onProjectileShoot(e: ProjectileLaunchEvent) {
        val projectile = e.entity as? CraftArrow ?: e.entity as? CraftTrident ?: e.entity as? CraftEnderPearl ?: e.entity as? CraftWindCharge ?: return

        if (projectile.shooter !is Player)
            return

        if (projectile is CraftArrow) {
            if (projectile.hasCustomEffects()) {
                projectile.clearCustomEffects()
            }
        }

        val player = projectile.shooter as Player

        if (!m.activeBattles.any { it.player == player || it.opponent == player })
            return

        val battle = m.activeBattles.first { it.player == player || it.opponent == player }

        if (!battle.started && battle.countdown) {
            e.isCancelled = true

            when (projectile) {
                is CraftArrow -> player.inventory.addItem(ItemStack(Material.ARROW, 1))
                is CraftEnderPearl -> player.inventory.addItem(ItemStack(Material.ENDER_PEARL, 1))
            }

            return
        }

        // add a 10 second cooldown for ender pearls
        if (projectile is CraftEnderPearl) {
            val lastUsed = enderPearlCooldown[player.uniqueId] ?: 0L
            if (System.currentTimeMillis() - lastUsed >= 10_000) {
                enderPearlCooldown[player.uniqueId] = System.currentTimeMillis()
            } else {
                e.isCancelled = true
                player.sendMessage(textComponent {
                    append("§cVocê não pode usar ender pearls tão rapidamente!")
                })
            }
        }
    }

    @EventHandler
    fun onEndermiteSpawn(e: CreatureSpawnEvent) {
        if (m.arenas.any { it.data.worldName == e.location.world.name } && e.entityType == EntityType.ENDERMITE)
            e.isCancelled = true
    }
}