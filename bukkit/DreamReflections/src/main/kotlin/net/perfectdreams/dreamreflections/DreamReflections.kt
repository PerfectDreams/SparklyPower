package net.perfectdreams.dreamreflections

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.WebhookClientBuilder
import club.minnced.discord.webhook.send.WebhookEmbed
import club.minnced.discord.webhook.send.WebhookEmbedBuilder
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import com.charleskorn.kaml.Yaml
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.TextUtils
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamreflections.commands.DreamReflectionsCommand
import net.perfectdreams.dreamreflections.config.DreamReflectionsConfig
import net.perfectdreams.dreamreflections.discord.ViolationDiscordNotification
import net.perfectdreams.dreamreflections.modules.autoclick.AutoClickListener
import net.perfectdreams.dreamreflections.modules.autorespawn.AutoRespawnListener
import net.perfectdreams.dreamreflections.modules.boatfly.BoatFlyListener
import net.perfectdreams.dreamreflections.modules.gamestate.GameStateListener
import net.perfectdreams.dreamreflections.modules.killaura.KillAuraListener
import net.perfectdreams.dreamreflections.modules.killaura.KillAuraTester
import net.perfectdreams.dreamreflections.modules.lbnofallforcejump.LBNoFallForceJumpListener
import net.perfectdreams.dreamreflections.modules.lbnofallhoplite.LBNoFallHopliteListener
import net.perfectdreams.dreamreflections.modules.nofall.NoFallListener
import net.perfectdreams.dreamreflections.modules.wurstcreativeflight.WurstCreativeFlightListener
import net.perfectdreams.dreamreflections.modules.wurstkillauralegit.KillAuraLegitTester
import net.perfectdreams.dreamreflections.modules.wurstnofall.WurstNoFallListener
import net.perfectdreams.dreamreflections.sessions.ReflectionSession
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.awt.Color
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DreamReflections : KotlinPlugin(), Listener {
	companion object {
		val REFLECTIONS_COLOR = NamedTextColor.DARK_RED
		val MODULE_NAME_COLOR = TextColor.color(0xFB54A7)

		val PREFIX = textComponent {
			appendTextComponent {
				content("[")
				color(NamedTextColor.DARK_GRAY)
			}
			appendTextComponent {
				color(REFLECTIONS_COLOR)
				decorate(TextDecoration.BOLD)
				content("Reflections")
			}
			appendTextComponent {
				content("]")
				color(NamedTextColor.DARK_GRAY)
			}
		}

		val SHORT_PREFIX = textComponent {
			appendTextComponent {
				content("[")
				color(NamedTextColor.DARK_GRAY)
			}
			appendTextComponent {
				color(REFLECTIONS_COLOR)
				decorate(TextDecoration.BOLD)
				content("R")
			}
			appendTextComponent {
				content("]")
				color(NamedTextColor.DARK_GRAY)
			}
		}
	}

	val killAuraTesters = mutableMapOf<Player, KillAuraTester>()
	val killAuraLegitTesters = mutableMapOf<Player, KillAuraLegitTester>()
	val activeReflectionSessions = ConcurrentHashMap<Player, ReflectionSession>()
	var webhookClient: WebhookClient? = null

	override fun softEnable() {
		val config = Yaml.default.decodeFromString<DreamReflectionsConfig>(File(this.dataFolder, "config.yml").readText())
		val webhookUrl = config.webhookUrl
		if (webhookUrl != null) {
			this.webhookClient = WebhookClientBuilder(config.webhookUrl).build()
		}

		registerEvents(GameStateListener(this))
		registerEvents(BoatFlyListener(this))
		registerEvents(AutoClickListener(this))
		registerEvents(KillAuraListener(this))
		registerEvents(WurstNoFallListener(this))
		registerEvents(AutoRespawnListener(this))
		registerEvents(WurstCreativeFlightListener(this))
		registerEvents(NoFallListener(this))
		registerEvents(LBNoFallHopliteListener(this))
		registerEvents(LBNoFallForceJumpListener(this))
		// registerEvents(FastPlaceListener(this))
		registerEvents(this)
		registerCommand(DreamReflectionsCommand(this))

		// This may happen if the plugin was reloaded during runtime
		// If this happens, we will attempt to create reflection sessions for these players
		for (player in Bukkit.getOnlinePlayers()) {
			createReflectionSession(player)
		}

		launchMainThread {
			while (true) {
				for ((player, session) in activeReflectionSessions) {
					for (module in session.violationCounterModules) {
						module.processDecay()
					}
				}

				delayTicks(1L)
			}
		}

		launchMainThread {
			while (true) {
				for (tester in killAuraTesters.toMap()) {
					tester.value.tick()
				}

				for (tester in killAuraLegitTesters.toMap()) {
					tester.value.tick()
				}

				delayTicks(1L)
			}
		}

		/* launchMainThread {
			while (true) {
				activeReflectionSessions.forEach { t, u ->
					val pastTime2 = Clock.System.now().minus(60_000, DateTimeUnit.MILLISECOND)
					val recentClicks = u.data.clicks.filter { it.clickedAt >= pastTime2 }

					val cps = recentClicks.size / 60.0
					if (cps >= 20.0) {
						val notify = Bukkit.getOnlinePlayers().filter { it.hasPermission("dreamreflections.notify") }
						notify.forEach {
							it.sendMessage(
								textComponent {
									append(SHORT_PREFIX)
									append(" ")
									append("${t.name} parece estar usando AutoClick! $cps/s")
								}
							)
						}
					}
				}

				delayTicks(300) // Every 15s
			}
		} */
	}

	override fun softDisable() {
		// Attempt to save any reflection sessions here, this may happen when the plugin is reloaded
		for (killAura in killAuraTesters) {
			killAura.value.removeFakePlayers()
		}
		this.killAuraTesters.clear()

		for (killAura in killAuraLegitTesters) {
			killAura.value.removeFakePlayers()
		}
		this.killAuraLegitTesters.clear()

		webhookClient?.close()
	}

	@EventHandler
	fun onJoin(e: PlayerJoinEvent) {
		val player = e.player

		createReflectionSession(player)
	}

	@EventHandler
	fun onQuit(e: PlayerQuitEvent) {
		val activeReflectionSession = activeReflectionSessions.remove(e.player)

		if (activeReflectionSession != null) {
			// TODO: Save the session to the database!
		}
	}

	/**
	 * Creates a reflection session
	 */
	private fun createReflectionSession(player: Player): ReflectionSession {
		val viaVersion = Via.getAPI()
		val playerVersion = ProtocolVersion.getProtocol(viaVersion.getPlayerVersion(player)).name

		val session = ReflectionSession(
			this,
			player,
			"Minecraft: Java Edition $playerVersion",
			player.isBedrockClient,
			Clock.System.now()
		)

		activeReflectionSessions[player] = session

		return session
	}

	/**
	 * Gets the current active reflection session
	 */
	fun getActiveReflectionSession(player: Player): ReflectionSession? {
		return this.activeReflectionSessions[player]
	}

	fun notifyStaff(component: TextComponent) {
		val notify = Bukkit.getOnlinePlayers().filter { it.hasPermission("dreamreflections.notify") }
		notify.forEach {
			it.sendMessage(
				textComponent {
					append(SHORT_PREFIX)
					append(" ")
					append(component)
				}
			)
		}

		this.componentLogger.info(component)
	}

	fun notifyDiscord(notification: ViolationDiscordNotification) {
		val client = webhookClient
		if (client != null) {
			when (notification) {
				is ViolationDiscordNotification.ViolationCounterDiscordNotification -> {
					val player = notification.module.session.player
					val tps = Bukkit.getTPS()
					val tpsNow = "%.2f".format(tps[0])
					val warpTarget = player.location
					val worldXyz = "${player.world.name} ${TextUtils.ROUND_TO_2_DECIMAL.format(warpTarget.x)}, ${TextUtils.ROUND_TO_2_DECIMAL.format(warpTarget.y)}, ${TextUtils.ROUND_TO_2_DECIMAL.format(warpTarget.z)}"

					val viaVersion = Via.getAPI()
					val playerVersion = ProtocolVersion.getProtocol(viaVersion.getPlayerVersion(player)).name

					val version = if (player.isBedrockClient) { "Minecraft: Bedrock Edition (emulando $playerVersion)" } else { "Minecraft: Java Edition $playerVersion" }

					val hash = notification.module.moduleName.hashCode()
					val red = (hash shr 16 and 0xFF) % 256
					val green = (hash shr 8 and 0xFF) % 256
					val blue = (hash and 0xFF) % 256
					val color = Color(red, green, blue)

					client.send(
						WebhookMessageBuilder()
							.addEmbeds(
								WebhookEmbedBuilder()
									.setAuthor(WebhookEmbed.EmbedAuthor(player.name, null, null))
									.setTitle(WebhookEmbed.EmbedTitle("${notification.module.moduleName} (${notification.module.violations}x)", null))
									.setThumbnailUrl("https://sparklypower.net/api/v1/render/avatar?name=${player.name}&scale=16")
									.addField(WebhookEmbed.EmbedField(false, "Localização", worldXyz))
									.addField(WebhookEmbed.EmbedField(false, "Client", "`$version` (`${player.clientBrandName}`)"))
									.addField(WebhookEmbed.EmbedField(false, "TPS", tpsNow))
									.addField(WebhookEmbed.EmbedField(false, "Ping do Player", "${player.ping}ms"))
									.setFooter(WebhookEmbed.EmbedFooter("Alguém por favor sabe me informar se é assim que a banda toca mesmo", null))
									.setColor(color.rgb)
									.build()
							)
							.build()
					)
				}
			}

		}
	}

	fun spawnKillAuraTester(player: Player): Boolean {
		if (killAuraTesters.containsKey(player))
			return false

		notifyStaff(
			textComponent {
				appendTextComponent {
					content("Executando teste de KillAura em ")
				}

				appendTextComponent {
					color(NamedTextColor.AQUA)
					content(player.name)
				}

				appendTextComponent {
					content("...")
				}
			}
		)

		val session = getActiveReflectionSession(player) ?: return false

		val killAuraTester = KillAuraTester(session)
		killAuraTesters[player] = killAuraTester
		killAuraTester.spawnFakePlayers()

		launchMainThread {
			delayTicks(100L)

			killAuraTester.removeFakePlayers()
			killAuraTesters.remove(player)
		}

		return true
	}

	fun spawnKillAuraLegitTester(player: Player): Boolean {
		if (killAuraLegitTesters.containsKey(player))
			return false

		notifyStaff(
			textComponent {
				appendTextComponent {
					content("Executando teste de KillAura Legit em ")
				}

				appendTextComponent {
					color(NamedTextColor.AQUA)
					content(player.name)
				}

				appendTextComponent {
					content("...")
				}
			}
		)

		val session = getActiveReflectionSession(player) ?: return false

		val killAuraTester = KillAuraLegitTester(session)
		killAuraLegitTesters[player] = killAuraTester
		killAuraTester.spawnFakePlayers()

		launchMainThread {
			delayTicks(100L)

			killAuraTester.removeFakePlayers()
			killAuraLegitTesters.remove(player)

			if (killAuraTester.rotatedTowardsFakePlayer > 50) {
				session.killAuraRotation.increaseViolationLevel(killAuraTester.rotatedTowardsFakePlayer - 50)
			}
		}

		return true
	}
}