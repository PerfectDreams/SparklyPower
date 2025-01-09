package net.perfectdreams.dreamesponjas

import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.registerEvents
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

class DreamEsponjas : KotlinPlugin(), Listener {
	private val lastJump = WeakHashMap<Player, Long>()

	override fun softEnable() {
		super.softEnable()
		registerEvents(this)
	}

	override fun softDisable() {
		super.softDisable()
	}

	@EventHandler(ignoreCancelled = true)
	fun onMove(e: PlayerMoveEvent) {
		if (e.player.isSneaking)
			return

		// Vamos ignorar se o player não moveu
		if (e.from.x == e.to.x && e.from.y == e.to.y && e.from.z == e.to.z) {
			return
		}

		val to = e.to

		val below = to.block.getRelative(BlockFace.DOWN)

		if (below.type == Material.MAGENTA_GLAZED_TERRACOTTA) {
			val blockBelowTheTarget = below.getRelative(BlockFace.DOWN)

			if (blockBelowTheTarget.type == Material.SPONGE) {
				val directional = below.blockData as Directional

				directional.facing
				println(below.blockData as Directional)

				val diff = System.currentTimeMillis() - lastJump.getOrDefault(e.player, 0L)
				if (2000 > diff) {
					return
				}

				// Vamos salvar o último pulo do player
				lastJump[e.player] = System.currentTimeMillis()
				// woosh!
				e.player.sendMessage("§6(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧ §eWoosh! §6✧ﾟ･: *ヽ(◕ヮ◕ヽ)")

				// Alterar velocidade do player
				val velocity = e.player.velocity
				velocity.y = 2.0
				// Reset fall distance damage
				// While we don't need to do this in a "vanilla" enviornment, SparklyPaper (actually, DreamReflections) has code changes to change fall distance behavior to fix
				// NoFall hacks, this does affect sponges because the fall distance is not reset when the player is launched upwards
				// So we will reset it here
				e.player.fallDistance = 0f

				when (directional.facing) {
					BlockFace.SOUTH -> velocity.z = -4.0
					BlockFace.NORTH -> velocity.z = 4.0
					BlockFace.EAST -> velocity.x = -4.0
					BlockFace.WEST -> velocity.x = 4.0
					else -> {}
				}

				e.player.velocity = velocity

				// Tocar som de fogos de artifício
				e.player.world.playSound(e.player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1F, 1F)

				// Soltar partículas
				e.player.world.spawnParticle(Particle.CLOUD, e.player.location, 30, 0.0, 0.0, 0.0, 0.1)
			}
		}

		if (below.type == Material.SPONGE) {
			val diff = System.currentTimeMillis() - lastJump.getOrDefault(e.player, 0L)
			if (2000 > diff) {
				return
			}

			// Vamos salvar o último pulo do player
			lastJump[e.player] = System.currentTimeMillis()
			// woosh!
			e.player.sendMessage("§6(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧ §eWoosh! §6✧ﾟ･: *ヽ(◕ヮ◕ヽ)")

			// Alterar velocidade do player
			val velocity = e.player.velocity
			velocity.y = 2.0
			e.player.velocity = velocity
			// Reset fall distance damage
			// While we don't need to do this in a "vanilla" enviornment, SparklyPaper (actually, DreamReflections) has code changes to change fall distance behavior to fix
			// NoFall hacks, this does affect sponges because the fall distance is not reset when the player is launched upwards
			// So we will reset it here
			e.player.fallDistance = 0f

			// Tocar som de fogos de artifício
			e.player.world.playSound(e.player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1F, 1F)

			// Soltar partículas
			e.player.world.spawnParticle(Particle.CLOUD, e.player.location, 30, 0.0, 0.0, 0.0, 0.1)
		}
	}

	@EventHandler
	fun onDamage(e: EntityDamageEvent) {
		if (e.cause == EntityDamageEvent.DamageCause.FALL) {
			if (e.entity is Player) {
				// Remover dano de queda caso o bloco seja uma esponja
				if (e.entity.location.block.getRelative(BlockFace.DOWN)?.type == Material.SPONGE) {
					e.isCancelled = true
					return
				}

				// Remover dano de queda caso seja após um pulo
				val diff = System.currentTimeMillis() - lastJump.getOrDefault(e.entity as Player, 0L)
				if (10000 > diff) {
					e.isCancelled = true
					return
				}
			}
		}
	}

	@EventHandler
	fun onQuit(e: PlayerQuitEvent) {
		lastJump.remove(e.player)
	}
}