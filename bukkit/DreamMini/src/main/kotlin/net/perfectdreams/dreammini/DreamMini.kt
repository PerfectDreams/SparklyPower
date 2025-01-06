package net.perfectdreams.dreammini

import com.okkero.skedule.CoroutineTask
import com.okkero.skedule.schedule
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.commands.AbstractCommand
import net.perfectdreams.dreamcore.utils.commands.annotation.Subcommand
import net.perfectdreams.dreamcore.utils.commands.annotation.SubcommandPermission
import net.perfectdreams.dreamcore.utils.preferences.BroadcastType
import net.perfectdreams.dreamcore.utils.preferences.broadcastMessage
import net.perfectdreams.dreamcorreios.DreamCorreios
import net.perfectdreams.dreammini.commands.*
import net.perfectdreams.dreammini.utils.*
import net.perfectdreams.dreamvanish.DreamVanishAPI
import net.sparklypower.sparklypaper.event.inventory.CraftItemRecipeEvent
import org.bukkit.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.Inventory
import java.io.File
import java.util.*

class DreamMini : KotlinPlugin(), Listener {
	companion object {
		lateinit var INSTANCE: DreamMini

		val DISALLOW_CRAFTING_KEY = SparklyNamespacedBooleanKey("disallow_crafting")
	}

	val joined = mutableSetOf<Player>()
	val left = mutableSetOf<Player>()
	var currentJoinSchedule: CoroutineTask? = null
	var currentLeftSchedule: CoroutineTask? = null
	var tpaManager = TpaManager()

	val dropsBlacklist = mutableMapOf<Player, Inventory>()
	val phantomWhitelist = mutableSetOf<UUID>()
	val weatherBlacklist = mutableSetOf<UUID>()

	val phantomFilterFile by lazy {
		File(dataFolder, "phantom-filter.txt")
	}
	val weatherBlacklistFile by lazy {
		File(dataFolder, "weather-filter.txt")
	}

	override fun softEnable() {
		super.softEnable()

		INSTANCE = this

		registerEvents(RandomLogsListener(this))

		if (config.getBoolean("fancy-unknown-command")) {
			registerEvents(UnknownCommandListener(this))
		}

		if (config.getBoolean("disable-phantoms")) {
			registerEvents(DisablePhantomsListener(this))
		}

		if (config.getBoolean("filter-phantoms")) {
			if (phantomFilterFile.exists()) {
				phantomWhitelist.addAll(
						phantomFilterFile.readLines().map { UUID.fromString(it) }
				)
			}

			registerEvents(FilterPhantomsListener(this))
			registerCommand(PhantomCommand(this))
		}

		if (config.getBoolean("filter-weather")) {
			if (weatherBlacklistFile.exists()) {
				weatherBlacklist.addAll(
						weatherBlacklistFile.readLines().map { UUID.fromString(it) }
				)
			}

			registerEvents(WeatherListener(this))
			registerCommand(ChuvaCommand(this))
		}

		if (config.getBoolean("nether-teleport")) {
			registerEvents(NetherTeleportListener(this))
		}

		if (config.getBoolean("back-to-spawn-on-unsafe-join")) {
			registerEvents(BackToSpawnListener(this))
		}

		registerEvents(this)
		registerEvents(DoNotPickupDropsListener(this))
		registerEvents(DontDropItemsInSpawnListener(this))

		registerCommand(BroadcastCommand(this))
		registerCommand(FacebookCommand(this))
		registerCommand(SignEditCommand(this))
		registerCommand(SudoCommand(this))
		registerCommand(LixeiraCommand(this))
		registerCommand(DreamMiniCommand(this))

		registerCommand(TopCommand(this))
		registerCommand(SpeedCommand(this))
		registerCommand(FlyCommand(this))
		registerCommand(HealCommand(this))
		registerCommand(FeedCommand(this))
		registerCommand(SkullCommand(this))

		registerCommand(MemoryCommand(this))
		registerCommand(RenameCommand(this))
		registerCommand(LoreCommand(this))
		registerCommand(GameModeCommand(this))
		registerCommand(TpAllCommand(this))
		registerCommand(TpaCommand(this))
		registerCommand(TpaAquiCommand(this))
		registerCommand(TpaNegarCommand(this))
		registerCommand(TpaAceitarCommand(this))
		registerCommand(SpawnCommand(this))
		registerCommand(CalculatorCommand(this))
		registerCommand(OpenInvCommand(this))
		registerCommand(OpenEcCommand(this))
		registerCommand(DoNotPickupCommand(this))
		registerCommand(CraftCommand(this))

		if (DreamCore.dreamConfig.bungeeName == "sparklypower_survival") {
			registerCommand(object : AbstractCommand("survival") {
				@Subcommand
				fun root(player: Player) {
					Bukkit.dispatchCommand(player, "warp survival")
				}
			})

			registerCommand(object : AbstractCommand("baltop") {
				@Subcommand
				fun root(player: Player) {
					Bukkit.dispatchCommand(player, "money top")
				}

				@Subcommand
				fun root(player: Player, idx: String) {
					Bukkit.dispatchCommand(player, "money top Sonho $idx")
				}
			})

			registerCommand(object : AbstractCommand("givehelditemall") {
				@Subcommand
				@SubcommandPermission("dreammini.givehelditemall")
				fun root(player: Player) {
					val itemInMainHand = player.inventory.itemInMainHand.clone()
					Bukkit.getOnlinePlayers().forEach {
						if (player != it)
							DreamCorreios.getInstance().addItem(it, itemInMainHand)
					}

					player.sendMessage("§aProntinho!")
				}
			})

			registerCommand(object : AbstractCommand("onlinestatedit") {
				@Subcommand
				@SubcommandPermission("onlinestatedit.plz")
				fun root(player: Player) {
					player.sendMessage("Você está a ${player.getStatistic(Statistic.PLAY_ONE_MINUTE)} ticks online!")
				}

				@Subcommand
				@SubcommandPermission("onlinestatedit.plz")
				fun root(sender: CommandSender, playerName: String) {
					val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
					sender.sendMessage("$playerName está ${offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE)} ticks online!")
					sender.sendMessage("${offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20} segundos")
					sender.sendMessage("${offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60} minutos")
					sender.sendMessage("${offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60 / 24} horas")
					sender.sendMessage("${offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60 / 24 / 30} dias")
				}

				@Subcommand(["stat"])
				@SubcommandPermission("onlinestatedit.blocktype")
				fun stat(player: CommandSender, playerName: String, type: String) {
					val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
					player.sendMessage(
						"Stat: ${
							offlinePlayer.getStatistic(
								Statistic.MINE_BLOCK,
								Material.valueOf(type)
							)
						}"
					)
				}

				@Subcommand(["set"])
				@SubcommandPermission("onlinestatedit.plz")
				fun uuid2name(player: Player, number: Int) {
					player.setStatistic(Statistic.PLAY_ONE_MINUTE, number)
					player.sendMessage("Tempo alterado!")
				}

				@Subcommand(["set_player"])
				@SubcommandPermission("onlinestatedit.plz")
				fun uuid2name2(player: CommandSender, playerName: String, number: Int) {
					val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
					OfflinePlayer::class.java.getDeclaredMethod("setStatistic", Statistic::class.java, Integer.TYPE)
						.invoke(offlinePlayer, Statistic.PLAY_ONE_MINUTE, number)
					player.sendMessage("Tempo alterado!")
				}
			})
		}
	}

	override fun softDisable() {
		super.softDisable()

		if (config.getBoolean("filter-phantoms")) {
			phantomFilterFile.writeText(
					phantomWhitelist.joinToString("\n", transform = { it.toString() })
			)
		}
		if (config.getBoolean("filter-weather")) {
			weatherBlacklistFile.writeText(
					weatherBlacklist.joinToString("\n", transform = { it.toString() })
			)
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	fun onTeleport(e: PlayerTeleportEvent) =
		e.player.let {
			if (!it.hasPermission("sparklypower.soustaff")) return@let
			if (e.to.world == e.from.world) return@let
			if (!it.allowFlight) return@let

			schedule {
				waitFor(10L)
				it.allowFlight = true
				if (!it.isOnGround) it.isFlying = true
			}
		}

	@EventHandler(ignoreCancelled = true)
	fun onTeleportToAnywhere(e: PlayerTeleportEvent) {
		// TPA
		run {
			val currentRequestRequestedByThePlayer = tpaManager.requests.firstOrNull { it.playerThatRequestedTheTeleport == e.player }
			val currentRequestThatHasThePlayerAsTheReceiver = tpaManager.requests.firstOrNull { it.playerThatWillBeTeleported == e.player }

			if (currentRequestRequestedByThePlayer != null) {
				// If there is any pending request that was requested by the player, remove it!
				tpaManager.requests.remove(currentRequestRequestedByThePlayer)
				currentRequestRequestedByThePlayer.playerThatRequestedTheTeleport.sendMessage("§cSeu pedido de teletransporte foi cancelado pois você se teletransportou!")
				currentRequestRequestedByThePlayer.playerThatWillBeTeleported.sendMessage("§cO pedido de teletransporte de §b${e.player.name}§c foi cancelado pois ele se teletransportou!")
			}

			if (currentRequestThatHasThePlayerAsTheReceiver != null) {
				// If there is any pending request that was requested by the player, remove it!
				tpaManager.requests.remove(currentRequestThatHasThePlayerAsTheReceiver)
				currentRequestThatHasThePlayerAsTheReceiver.playerThatRequestedTheTeleport.sendMessage("§cO pedido de teletransporte de §b${e.player.name}§c foi cancelado pois você se teletransportou!")
				currentRequestThatHasThePlayerAsTheReceiver.playerThatWillBeTeleported.sendMessage("§cSeu pedido de teletransporte foi cancelado pois você se teletransportou!")
			}
		}

		// TPA Here
		run {
			val currentRequestRequestedByThePlayer = tpaManager.hereRequests.firstOrNull { it.playerThatRequestedTheTeleport == e.player }
			val currentRequestThatHasThePlayerAsTheReceiver = tpaManager.hereRequests.firstOrNull { it.playerThatWillBeTeleported == e.player }

			if (currentRequestRequestedByThePlayer != null) {
				// If there is any pending request that was requested by the player, remove it!
				tpaManager.hereRequests.remove(currentRequestRequestedByThePlayer)
				currentRequestRequestedByThePlayer.playerThatRequestedTheTeleport.sendMessage("§cSeu pedido de teletransporte foi cancelado pois você se teletransportou!")
				currentRequestRequestedByThePlayer.playerThatWillBeTeleported.sendMessage("§cO pedido de teletransporte de §b${e.player.name}§c foi cancelado pois ele se teletransportou!")
			}

			if (currentRequestThatHasThePlayerAsTheReceiver != null) {
				// If there is any pending request that was requested by the player, remove it!
				tpaManager.hereRequests.remove(currentRequestThatHasThePlayerAsTheReceiver)
				currentRequestThatHasThePlayerAsTheReceiver.playerThatRequestedTheTeleport.sendMessage("§cO pedido de teletransporte de §b${e.player.name}§c foi cancelado pois você se teletransportou!")
				currentRequestThatHasThePlayerAsTheReceiver.playerThatWillBeTeleported.sendMessage("§cSeu pedido de teletransporte foi cancelado pois você se teletransportou!")
			}
		}
	}

	// Needs to be veery low to avoid users using "&rSparklyShop" to bypass the Admin Shop check
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	fun onEdit(e: SignChangeEvent) {
		if (e.player.hasPermission("dreammini.colorize")) {
			for (idx in 0..3) {
				val line = e.getLine(idx)
				if (line != null)
					e.setLine(idx, line.translateColorCodes())
			}
		}
	}

	@EventHandler
	fun onJoin(e: PlayerJoinEvent) {
		if (e.player.hasPermission("dreammini.keepfly") && !e.player.isOnGround) { // Se o usuário deslogar enquanto está no ar, ative o fly
			scheduler().schedule(this) {
				waitFor(20)
				e.player.allowFlight = true
				e.player.isFlying = true
				e.player.sendMessage("§3Modo de vôo foi ativado automaticamente já que você não estava no chão ao sair, para desativar, use §6/fly")
			}
		}

		if (config.getBoolean("fancy-join", true)) {
			// Remover mensagem de entrada/saída
			e.joinMessage = null
			joined.add(e.player)
			left.remove(e.player)

			currentJoinSchedule?.cancel()

			val schedule = scheduler().schedule(this) {
				waitFor(20 * 7) // Esperar sete segundos
				val joinedPlayers = joined.filter { it.isOnline && !DreamVanishAPI.isQueroTrabalhar(it) }

				if (joinedPlayers.isEmpty())
					return@schedule

				val aux = if (joinedPlayers.size == 1) {
					"entrou"
				} else {
					"entraram"
				}

				broadcastMessage(BroadcastType.LOGIN_ANNOUNCEMENT) {
					"§8[§a+§8] §b${joinedPlayers.joinToString("§a, §b", transform = { it.name })}§a $aux no jogo!"
				}

				joined.clear()
			}

			currentJoinSchedule = schedule

			// Spawnar fireworks com cores aleatórias quando o player entrar no servidor
			val r = DreamUtils.random.nextInt(0, 256)
			val g = DreamUtils.random.nextInt(0, 256)
			val b = DreamUtils.random.nextInt(0, 256)

			val fadeR = Math.max(0, r - 60)
			val fadeG = Math.max(0, g - 60)
			val fadeB = Math.max(0, b - 60)

			val fireworkEffect = FireworkEffect.builder()
					.withTrail()
					.withColor(Color.fromRGB(r, g, b))
					.withFade(Color.fromRGB(fadeR, fadeG, fadeB))
					.with(FireworkEffect.Type.values()[DreamUtils.random.nextInt(0, FireworkEffect.Type.values().size)])
					.build()

			val firework = e.player.world.spawnEntity(e.player.location, EntityType.FIREWORK_ROCKET) as Firework
			val fireworkMeta = firework.fireworkMeta

			fireworkMeta.power = 1
			fireworkMeta.addEffect(fireworkEffect)

			firework.fireworkMeta = fireworkMeta
		}
	}

	@EventHandler
	fun onQuit(e: PlayerQuitEvent) {
		tpaManager.requests = tpaManager.requests.asSequence().filter { it.playerThatWillBeTeleported != e.player || it.playerThatRequestedTheTeleport != e.player }.toMutableList()
		dropsBlacklist.remove(e.player)

		if (getConfig().getBoolean("fancy-quit", true)) {
			// Remover mensagem de entrada/saída
			e.quitMessage = null
			left.add(e.player)
			joined.remove(e.player)

			currentLeftSchedule?.cancel()

			val schedule = scheduler().schedule(this) {
				waitFor(20 * 7) // Esperar sete segundos
				val leftPlayers = left.filter { !it.isOnline }

				if (leftPlayers.isEmpty())
					return@schedule

				val aux = if (leftPlayers.size == 1) {
					"saiu"
				} else {
					"sairam"
				}

				broadcastMessage(BroadcastType.LOGIN_ANNOUNCEMENT) {
					"§8[§c-§8] §b${leftPlayers.joinToString("§c, §b", transform = { it.name })}§c $aux do jogo..."
				}

				left.clear()
			}

			currentLeftSchedule = schedule
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	fun onItemDrop(e: PlayerDropItemEvent) {
		if (config.getBoolean("special-lucky-drop-item", true)) {
			val type = e.itemDrop?.itemStack?.type
			val player = e.player

			if (type == Material.GOLD_NUGGET || type == Material.GOLD_INGOT || type == Material.GOLD_BLOCK) {
				scheduler().schedule(this) {
					waitFor(1)
					for (idx in 0 until 10) {
						if (!e.itemDrop.isValid)
							return@schedule

						val location = e.itemDrop.location

						if (location.block.type == Material.WATER) {
							if (WorldGuardUtils.isWithinRegion(location, "loja-sorte")) {
								var rewarded = false
								for (amount in 0 until e.itemDrop.itemStack.amount) {
									var chance = DreamUtils.random.nextInt(0, 101)

									// TODO: Prêmios
									if (chance == 100) {
										player.deposit(1750.00, TransactionContext(extra = "ser sortudo"))
										player.sendMessage("§aHoje é o seu dia de sorte!")
										rewarded = true
									}
								}
								if (rewarded) {
									e.itemDrop.location.world.spawnParticle(Particle.HAPPY_VILLAGER, e.itemDrop.location, 5, 0.5, 0.5, 0.5)
								} else {
									e.player.sendMessage("§cQue pena, pelo visto você não ganhou nada...")
									e.itemDrop.location.world.spawnParticle(Particle.ANGRY_VILLAGER, e.itemDrop.location, 5, 0.5, 0.5, 0.5)
								}
								e.itemDrop.remove()
								return@schedule
							}
						}
						waitFor(10)
					}
					return@schedule
				}
			}
		}
	}

	@EventHandler
	fun onCraft(e: CraftItemRecipeEvent) {
		for (item in e.craftingMatrix) {
			if (item != null && item.hasItemMeta() && item.itemMeta.persistentDataContainer.get(DISALLOW_CRAFTING_KEY))
				e.isCancelled = true
		}
	}

	@EventHandler
	fun onDeath(e: PlayerDeathEvent) {
		if (config.getBoolean("disable-death-messages", true)) {
			e.deathMessage = null
		}
	}

	@EventHandler
	fun onSetFire (e: BlockIgniteEvent){
		val entity : Entity? = e.ignitingEntity
		if(e.cause == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && entity is Player) {
			entity.playSound(entity.location,"perfectdreams.sfx.fogobicho", 1F, 1F)
		}
	}
	@EventHandler
	fun FaleceuSfx (e: PlayerDeathEvent){
		val entity : Entity? = e.entity
		if (entity is Player) {
			entity.playSound(entity.location, "perfectdreams.sfx.faleceu", 1F, 1F)
		}
	}
}
