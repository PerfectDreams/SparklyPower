package net.perfectdreams.dreamsinuca

import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.SparklyNamespacedBooleanKey
import net.perfectdreams.dreamcore.utils.extensions.rightClick
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamsinuca.commands.DreamSinucaCommand
import net.perfectdreams.dreamsinuca.commands.SinucaCommand
import net.perfectdreams.dreamsinuca.listeners.SinucaEntityListener
import net.perfectdreams.dreamsinuca.sinuca.PoolTable
import net.perfectdreams.dreamsinuca.sinuca.SparklySinuca
import net.sparklypower.sparklypaper.event.entity.PreEntityShootBowEvent
import org.bukkit.Bukkit
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent

class DreamSinuca : KotlinPlugin(), Listener {
	companion object {
		val POOL_TABLE_ENTITY = SparklyNamespacedBooleanKey("pool_table_entity")
	}

	val poolTables = mutableMapOf<ItemDisplay, PoolTable>()

	val sinucas = mutableListOf<SparklySinuca>()

	override fun softEnable() {
		registerCommand(SinucaCommand(this))
		registerCommand(DreamSinucaCommand(this))
		registerEvents(this)
		registerEvents(SinucaEntityListener(this))
	}

	override fun softDisable() {
		for (sinuca in sinucas) {
			// sinuca.cueBallHitbox?.remove()
			for (entity in sinuca.ballToDisplayEntity) {
				entity.value.remove()
			}

			for (entity in sinuca.ballTrajectoryEntities) {
				entity.remove()
			}

			sinuca.gameStatusDisplay.remove()
		}
	}

	@EventHandler
	fun onShoot(e: PreEntityShootBowEvent) {
		val player = e.entity as? Player ?: return

		e.isCancelled = true

		for (sinuca in sinucas) {
			if (sinuca.player1 == player || sinuca.player2 == player) {
				if (sinuca.playerTurn == player && !sinuca.isWaitingForPhysicsToEnd && !sinuca.cueballInHand) {
					val direction = player.location.clone().apply {
						this.pitch = 0f
					}.direction.normalize()

					// in 8 ball pool, max power causes the ball to bounce ~5 times

					val velocity = direction.multiply(e.force).multiply(11f)
					Bukkit.broadcastMessage("Force: ${e.force} - Velocity: $velocity")

					sinuca.cueball.body.isAtRest = false
					sinuca.cueball.body.setLinearVelocity(velocity.x, velocity.z)
					sinuca.processPhysics()
				}
			}
		}
	}

	@EventHandler
	fun onMove(e: PlayerMoveEvent) {
		for (sinuca in sinucas) {
			if (sinuca.playerTurn == e.player && sinuca.cueballInHand) {
				val raytraceResult = e.player.rayTraceBlocks(7.0) ?: return // Couldn't ray trace, abort!
				val position = raytraceResult.hitPosition

				val cueballX = position.x - sinuca.topX
				val cueballY = position.z - sinuca.topY

				if (!sinuca.checkIfCoordinateIsInsidePlayfield(cueballX.toFloat(), cueballY.toFloat())) {
					// If outside of playfield, just ignore it and don't attempt to translate
					return
				}

				// Unpocket that thang
				// We need to calculate where are we looking by using *raytracing* (omg)
				sinuca.cueball.body.translateToOrigin()

				sinuca.cueball.body.translate(cueballX, cueballY)
				sinuca.updateGameObjects()
			}
		}
	}

	@EventHandler
	fun onRightClick(e: PlayerInteractEvent) {
		if (!e.rightClick)
			return

		for (sinuca in sinucas) {
			if (sinuca.playerTurn == e.player && sinuca.cueballInHand) {
				// We cancel the event to avoid the player mistakenly shooting the bow with zero force
				e.isCancelled = true

				val raytraceResult = e.player.rayTraceBlocks(7.0) ?: return // Couldn't ray trace, abort!
				val position = raytraceResult.hitPosition

				val cueballX = position.x - sinuca.topX
				val cueballY = position.z - sinuca.topY

				if (!sinuca.checkIfCoordinateIsInsidePlayfield(cueballX.toFloat(), cueballY.toFloat())) {
					// If outside of playfield, just ignore it and don't attempt to translate
					e.player.sendMessage("Você não pode colocar a bola branca aí!")
					return
				}

				// Unpocket that thang
				// We need to calculate where are we looking by using *raytracing* (omg)
				sinuca.cueball.body.translateToOrigin()

				sinuca.cueball.body.translate(cueballX, cueballY)
				val cueballAABB = sinuca.cueball.body.createAABB()

				for (body in sinuca.playfield.bodies) {
					if (body == sinuca.cueball.body)
						continue

					val bodyAABB = body.createAABB()

					if (cueballAABB.overlaps(bodyAABB)) {
						e.player.sendMessage("Você não pode colocar a bola branca aí!")
						return
					}
				}

				sinuca.playfield.addBody(sinuca.cueball.body)
				sinuca.cueball.pocketed = false
				sinuca.cueballInHand = false
				sinuca.updateGameObjects()
				sinuca.previewBallTrajectory = true
				e.player.sendMessage("Agora você pode jogar! ${sinuca.cueball.body.worldCenter}")
			}
		}
	}
}