package net.perfectdreams.dreamemotes

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.extensions.displaced
import net.perfectdreams.dreamcore.utils.extensions.hidePlayerWithoutRemovingFromPlayerList
import net.perfectdreams.dreamcore.utils.packetevents.ServerboundPacketReceiveEvent
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamemotes.blockbench.BlockbenchModel
import net.perfectdreams.dreamemotes.commands.*
import net.perfectdreams.dreamemotes.config.DreamEmotesConfig
import net.perfectdreams.dreamemotes.gestures.PlayerGesturePlayback
import net.perfectdreams.dreamemotes.gestures.SparklyGesturesRegistry
import net.perfectdreams.dreamemotes.gestures.SparklyGesturesManager
import net.perfectdreams.dreamemotes.tables.CachedGestureSkinHeads
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import javax.imageio.ImageIO

class DreamEmotes : KotlinPlugin(), Listener {
	val activeGesturePlaybacks = mutableMapOf<Player, PlayerGesturePlayback>()

	val gesturesFolder = File(this.dataFolder, "gestures")
	val modelsFolder = File(this.dataFolder, "models")
	val orbitalCameras = mutableMapOf<Player, OrbitalCamera>()
	val gesturesManager = SparklyGesturesManager(this)
	lateinit var config: DreamEmotesConfig
	val defaultSkin = ImageIO.read(File(this.dataFolder, "steve.png"))

	override fun softEnable() {
		reload()

		transaction(Databases.databaseNetwork) {
			SchemaUtils.createMissingTablesAndColumns(CachedGestureSkinHeads)
		}

		registerCommand(GestureCommand(this))
		registerCommand(TestGestureCommand(this))
		registerCommand(DreamEmotesCommand(this))
		// registerCommand(OrbitalCommand(this))

		registerEvents(this)

		launchMainThread {
			while (true) {
				// Create a copy of the map to avoid CME due to gestures stopping themselves during the tick phase
				for (gesture in activeGesturePlaybacks.values.toList()) {
					gesture.tick()
					gesture.ticksLived++
				}

				delayTicks(PlayerGesturePlayback.TARGET_PLAYBACK_SPEED_TICKS)
			}
		}
	}

	override fun softDisable() {
		activeGesturePlaybacks
			.values
			.toList()
			.forEach {
				it.stop()
			}
	}

	fun reload() {
		this.config = Yaml.default.decodeFromString<DreamEmotesConfig>(File(dataFolder, "config.yml").readText())

		SparklyGesturesRegistry.reload(this)
	}

	@EventHandler
	fun onPacket(e: ServerboundPacketReceiveEvent) {
		val packet = e.packet

		if (packet is ServerboundMovePlayerPacket.Rot) {
			val orbitalCamera = orbitalCameras[e.player]
			if (orbitalCamera != null) {
				// e.player.sendMessage("yRot: ${packet.yRot}; xRot: ${packet.xRot}")
				/* if (e.player.name == "MrPowerGamerBR") {
                    Bukkit.broadcastMessage("yRot: ${packet.yRot}; xRot: ${packet.xRot}")
                } */

				// Yes, the pitch is the x, not the y, see Paper's source code
				orbitalCamera.pitch = packet.xRot
				orbitalCamera.yaw = packet.yRot

				launchMainThread {
					orbitalCamera.update()
				}
			}
		}
	}

	@EventHandler
	fun onUnmount(e: PlayerToggleSneakEvent) {
		gesturesManager.stopGesturePlayback(e.player)
	}

	@EventHandler
	fun onQuit(e: PlayerQuitEvent) {
		gesturesManager.stopGesturePlayback(e.player)
	}

	@EventHandler
	fun onTeleport(e: PlayerTeleportEvent) {
		gesturesManager.stopGesturePlayback(e.player)
	}

	@EventHandler
	fun onJoin(e: PlayerJoinEvent) {
		for (activeGesture in activeGesturePlaybacks) {
			if (!e.player.isBedrockClient) {
				e.player.hidePlayerWithoutRemovingFromPlayerList(this, activeGesture.key)
			}
		}
	}

	// This is only here to avoid players doing "/skin", which causes the player to "respawn" but not remount (because the mount is created with packets)
	@EventHandler
	fun onMove(e: PlayerMoveEvent) {
		if (e.displaced) {
			gesturesManager.stopGesturePlayback(e.player)
		}
	}

	@EventHandler
	fun onDamage(e: EntityDamageEvent) {
		val player = e.entity as? Player ?: return

		gesturesManager.stopGesturePlayback(player)
	}

	@EventHandler
	fun onDeath(e: PlayerDeathEvent) {
		gesturesManager.stopGesturePlayback(e.player)
	}
}