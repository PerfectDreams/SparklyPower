package net.perfectdreams.dreamsinuca

import com.Acrobot.ChestShop.Listeners.Player.PlayerInteract
import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamsinuca.commands.Sinuca2Command
import net.perfectdreams.dreamsinuca.commands.old.SinucaCommand
import net.perfectdreams.dreamsinuca.commands.old.SparklySinuca
import net.sparklypower.sparklypaper.event.entity.PreEntityShootBowEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.joml.Vector2f

class DreamSinuca : KotlinPlugin(), Listener {
	val sinucas = mutableListOf<SparklySinuca>()
	val sinucasNew = mutableListOf<net.perfectdreams.dreamsinuca.commands.SparklySinuca>()

	override fun softEnable() {
		registerCommand(SinucaCommand(this))
		registerCommand(Sinuca2Command(this))
		registerEvents(this)
	}

	override fun softDisable() {
		for (sinuca in sinucas) {
			sinuca.cueBallHitbox?.remove()
			for (entity in sinuca.ballToDisplayEntity) {
				entity.value.remove()
			}
		}

		for (sinuca in sinucasNew) {
			// sinuca.cueBallHitbox?.remove()
			for (entity in sinuca.ballToDisplayEntity) {
				entity.value.remove()
			}
		}
	}

	@EventHandler
	fun onShoot(e: PreEntityShootBowEvent) {
		val player = e.entity as? Player ?: return

		e.isCancelled = true

		if (true) {
			for (sinuca in sinucasNew) {
				val direction = player.location.clone().apply {
					this.pitch = 0f
				}.direction.normalize()
				val velocity = direction.multiply(e.force).multiply(16f)
				Bukkit.broadcastMessage("Force: ${e.force} - Velocity: $velocity")

				sinuca.cueball.isAtRest = false
				sinuca.cueball.setLinearVelocity(velocity.x, velocity.z)
			}
		} else {
			for (sinuca in sinucas) {
				val direction = player.location.clone().apply {
					this.pitch = 0f
				}.direction.normalize()
				val velocity = direction.multiply(e.force)
				Bukkit.broadcastMessage("Force: ${e.force} - Velocity: $velocity")
				sinuca.cueball.velocity = Vector2f(velocity.x.toFloat(), velocity.z.toFloat()).mul(0.15f)
			}
		}
	}
}