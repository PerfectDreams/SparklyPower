package net.perfectdreams.dreamscoreboard

import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreambedrockintegrations.DreamBedrockIntegrations
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcash.utils.Cash
import net.perfectdreams.dreamclubes.dao.Clube
import net.perfectdreams.dreamclubes.tables.ClubeMembers
import net.perfectdreams.dreamclubes.utils.ClubeAPI
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.event.PlayerScoreboardCreatedEvent
import net.perfectdreams.dreamcore.event.PlayerScoreboardRemovedEvent
import net.perfectdreams.dreamcore.tables.EventVictories
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamscoreboard.commands.*
import net.perfectdreams.dreamscoreboard.listeners.TagListener
import net.perfectdreams.dreamscoreboard.utils.PlayerScoreboard
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Statistic
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.scoreboard.CraftScoreboard
import org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager
import org.bukkit.craftbukkit.util.WeakCollection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DreamScoreboard : KotlinPlugin(), Listener {
	companion object {
		var CURRENT_TICK = 0

		val EMOTES = listOf(
			"锃", // gesso
			"锅", // eu te moido
			"锇", // grand cat
			"锈", // wave
			"锍", // emojo
			"镞", // lori popcorn
			"镝", // lori temmie
			"镾", // tavares
			"閍", // vieirinha
			"閌", // loritta
			"開", // lori feliz
			"閊"  // pepo feliz
		)
		val FORMATTING_REGEX = Regex("§[k-or]")
		val GLOW_COLOR_KEY = SparklyNamespacedKeyWithType(SparklyNamespacedKey("glow_color"), PersistentDataType.STRING)
	}
	val lastOnlineTimeCheck = mutableMapOf<Player, Int>()

	val scoreboards = ConcurrentHashMap<Player, PlayerScoreboard>()
	var cachedClubesPrefixes = WeakHashMap<Player, String?>()

	override fun softEnable() {
		super.softEnable()

		Bukkit.getServer().maxPlayers = 250

		registerEvents(this)
		registerEvents(TagListener(this))

		registerCommand(EventosCommand)
		registerCommand(EventosTopCommand)
		// registerCommand(EventosTopClubesCommand)
		// registerCommand(EventosTopMeuClubeCommand)
		registerCommand(GlowingCommand)
		registerCommand(GlowingColorCommand)

		scheduler().schedule(this, SynchronizationContext.SYNC) {
			while (true) {
				// Carregar tag de clubes
				switchContext(SynchronizationContext.ASYNC)
				val prefixes = WeakHashMap<Player, String?>()
				Bukkit.getOnlinePlayers().forEach {
					prefixes[it] = ClubeAPI.getPlayerClube(it)?.shortName
				}
				switchContext(SynchronizationContext.SYNC)
				cachedClubesPrefixes = prefixes

				val noCollisionTeam = Bukkit.getScoreboardManager().mainScoreboard.getTeam("nocoll") ?: Bukkit.getScoreboardManager().mainScoreboard.registerNewTeam("nocoll")
				noCollisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)

				Bukkit.getOnlinePlayers().forEach {
					if (!noCollisionTeam.hasEntry(it.name))
						noCollisionTeam.addEntry(it.name)

					// This should NEVER happen, except if the plugin is reloaded...
					if (scoreboards[it] == null) {
						// This is sort of duplicated work, since the getOrCreateScoreboard call will invoke an event if the scoreboard is created
						val phoenixScoreboard = DreamCore.INSTANCE.scoreboardManager.getOrCreateScoreboard(it)
						val playerScoreboard = PlayerScoreboard(this@DreamScoreboard, it, phoenixScoreboard)
						playerScoreboard.updateScoreboard()
						scoreboards[it] = playerScoreboard
					}
				}

				setupTabDisplayNames()

				waitFor(20 * 15)
			}
		}

		launchMainThread {
			while (true) {
				CURRENT_TICK++

				// We have four different states:
				// 0 = players online, clock, money and active events
				// 1 = players online and upcoming events
				// 2 = players online and staff
				// 3 = players online, last voter, facebook, twitter and discord
				if (CURRENT_TICK > 3) {
					CURRENT_TICK = 0
				}

				logger.info("Updating scoreboard... Current tick: $CURRENT_TICK; Active scoreboards: ${scoreboards.size}")

				scoreboards.values.forEach {
					try {
						it.updateScoreboard()
					} catch (e: Exception) {
						e.printStackTrace()
					}
				}
				delayTicks(20 * 4) // 4 seconds each update
			}
		}

		schedule {
			while (true) {
				switchContext(SynchronizationContext.SYNC)

				val totalOneHourPlayers = Bukkit.getOnlinePlayers()
					.filter {
						val lastTimeCheck = lastOnlineTimeCheck[it]

						if (lastTimeCheck != null) {
							val modBefore = (((lastOnlineTimeCheck[it]!!) / 20) / 60) % 60
							val modNow = (((it.getStatistic(Statistic.PLAY_ONE_MINUTE)) / 20) / 60) % 120

							if (modBefore > modNow) {
								logger.info("${it.name} matches! Mod before is $modBefore, Mod now is $modNow")
								return@filter true
							}
						}
						return@filter false
					}

				logger.info("Total one hour player right now: ${totalOneHourPlayers.joinToString { it.name }}")

				switchContext(SynchronizationContext.ASYNC)
				val userCount = EventVictories.user.count()

				val start = getMonthStartInMillis()

				val end = Instant.now()
					.atZone(ZoneId.of("America/Sao_Paulo"))
					.toEpochSecond() * 1000

				val results = transaction(Databases.databaseNetwork) {
					EventVictories.slice(EventVictories.user, userCount).select {
						EventVictories.wonAt greaterEq start and (EventVictories.event neq "Chat")
					}.groupBy(EventVictories.user)
						.orderBy(userCount to SortOrder.DESC)
						.limit(3)
						.toList()
				}

				val averageWins = getTopClubeVictoriesOnAverageOnThisMonth()

				val bestClube = averageWins.firstOrNull()

				val top1 = results.getOrNull(0)
				val top2 = results.getOrNull(1)
				val top3 = results.getOrNull(2)

				switchContext(SynchronizationContext.SYNC)

				totalOneHourPlayers
					.forEach {
						if (top1 != null && top1[EventVictories.user] == it.uniqueId) {
							switchContext(SynchronizationContext.ASYNC)
							Cash.giveCash(it, 4, TransactionContext(type = TransactionType.EVENTS, extra = "por ser top 1 em `vitórias de eventos`"))
							logger.info("Giving 4 pesadelos to ${it.name} because they are top 1 in event victories!")
							it.sendMessage("§aVocê ganhou quatro pesadelos por ser top 1 em vitórias de eventos! Parabéns! §eʕ•ᴥ•ʔ")
							switchContext(SynchronizationContext.SYNC)
						}
						if (top2 != null && top2[EventVictories.user] == it.uniqueId) {
							switchContext(SynchronizationContext.ASYNC)
							Cash.giveCash(it, 2, TransactionContext(type = TransactionType.EVENTS, extra = "por ser top 2 em `vitórias de eventos`"))
							logger.info("Giving 2 pesadelos to ${it.name} because they are top 2 in event victories!")
							it.sendMessage("§aVocê ganhou dois pesadelos por ser top 2 em vitórias de eventos! Parabéns! §eʕ•ᴥ•ʔ")
							switchContext(SynchronizationContext.SYNC)
						}
						if (top3 != null && top3[EventVictories.user] == it.uniqueId) {
							switchContext(SynchronizationContext.ASYNC)
							Cash.giveCash(it, 1, TransactionContext(type = TransactionType.EVENTS, extra = "por ser top 3 em `vitórias de eventos`"))
							logger.info("Giving 1 pesadelos to ${it.name} because they are top 3 in event victories!")
							it.sendMessage("§aVocê ganhou um pesadelo por ser top 3 em vitórias de eventos! Parabéns! §eʕ•ᴥ•ʔ")
							switchContext(SynchronizationContext.SYNC)
						}
					}

				Bukkit.getOnlinePlayers().forEach {
					lastOnlineTimeCheck[it] = it.getStatistic(Statistic.PLAY_ONE_MINUTE)
				}

				waitFor(20 * 60)
			}
		}
	}

	private fun setupTabDisplayNames() {
		for (player in Bukkit.getOnlinePlayers()) {
			val playerGlowColor = if (player.hasPermission("dreamscoreboard.glowing")) {
				player.persistentDataContainer.get(GLOW_COLOR_KEY)
					?.let { ChatColor.valueOf(it) }
			} else
				null

			val tabPrefixColor = when {
				playerGlowColor != null -> playerGlowColor
				player.hasPermission("group.dono") -> ChatColor.GREEN
				player.hasPermission("group.admin") -> ChatColor.RED
				player.hasPermission("group.moderador") -> ChatColor.DARK_AQUA
				player.hasPermission("group.suporte") -> ChatColor.GOLD
				player.hasPermission("group.developer") -> ChatColor.DARK_GREEN
				player.hasPermission("group.vip++") -> ChatColor.AQUA
				player.hasPermission("group.vip+") -> ChatColor.AQUA
				player.hasPermission("group.vip") -> ChatColor.AQUA
				else -> ChatColor.WHITE
			}

			val rolePrefix = when {
				player.hasPermission("group.dono") -> "§a§l"
				player.hasPermission("group.admin") -> "§4§l"
				player.hasPermission("group.moderador") -> "§9§l"
				player.hasPermission("group.suporte") -> "§6§l"
				player.hasPermission("group.developer") -> "§2§l"
				player.hasPermission("group.vip++") -> "§b"
				player.hasPermission("group.vip+") -> "§b"
				player.hasPermission("group.vip") -> "§b"
				else -> "§f"
			}

			val barrinhaColor = playerGlowColor?.toString() ?: rolePrefix

			var prefix = when {
				player.hasPermission("group.dono") -> "$barrinhaColor[${rolePrefix}Dono$barrinhaColor] "
				player.hasPermission("group.admin") -> "$barrinhaColor[${rolePrefix}Admin$barrinhaColor] "
				player.hasPermission("group.moderador") -> "$barrinhaColor[${rolePrefix}Moderador$barrinhaColor] "
				player.hasPermission("group.suporte") -> "$barrinhaColor[${rolePrefix}Suporte$barrinhaColor] "
				player.hasPermission("group.developer") -> "$barrinhaColor[${rolePrefix}Developer$barrinhaColor] "
				player.hasPermission("group.vip++") -> "$barrinhaColor[${rolePrefix}VIP§6++$barrinhaColor] "
				player.hasPermission("group.vip+") -> "$barrinhaColor[${rolePrefix}VIP§6+$barrinhaColor] "
				player.hasPermission("group.vip") -> "$barrinhaColor[${rolePrefix}VIP$barrinhaColor] "
				else -> "§f"
			}

			val clubePrefix = cachedClubesPrefixes[player]

			if (clubePrefix != null)
				prefix = "$tabPrefixColor[$clubePrefix$tabPrefixColor] "
			val suffix = if (player.isBedrockClient) "§f 鈷" else ""

			val prefixWithoutChanges = prefix

			if (player.playerListName != prefixWithoutChanges + player.displayName + suffix)
				player.setPlayerListName(prefixWithoutChanges + player.displayName + suffix)
		}
	}

	override fun softDisable() {
		super.softDisable()
	}

	@EventHandler
	fun onScoreboardCreated(e: PlayerScoreboardCreatedEvent) {
		val playerScoreboard = PlayerScoreboard(this, e.player, e.phoenixScoreboard)
		playerScoreboard.updateScoreboard()
		scoreboards[e.player] = playerScoreboard
	}

	@EventHandler
	fun onScoreboardRemoved(e: PlayerScoreboardRemovedEvent) {
		scoreboards.remove(e.player)
	}

	fun getMonthStartInMillis(): Long {
		return Instant.now()
			.atZone(ZoneId.of("America/Sao_Paulo"))
			.withDayOfMonth(1)
			.withHour(0)
			.withMinute(0)
			.withSecond(0)
			.toEpochSecond() * 1000
	}

	fun getClubeVictoriesOnThisMonth(clube: Clube): MutableMap<UUID, Int> {
		val start = getMonthStartInMillis()

		val clubeMembersWithVictories = transaction(Databases.databaseNetwork) {
			ClubeMembers.join(EventVictories, JoinType.INNER, ClubeMembers.id, EventVictories.user).select {
				EventVictories.wonAt greaterEq start and (ClubeMembers.clube eq clube.id) and (EventVictories.event neq "Chat")
			}.toList()
		}

		val clubeVictoriesByPlayer = mutableMapOf<UUID, Int>()

		val members = clube.retrieveMembers()

		members.forEach { clubeMember ->
			// We need to retrieve the member list because we need to make an average of EVERYONE
			// If we only used the "list", we would ignore members that do not have any victories
			clubeVictoriesByPlayer[clubeMember.id.value] = clubeMembersWithVictories.count { it[ClubeMembers.id].value == clubeMember.id.value }
		}

		return clubeVictoriesByPlayer
	}

	fun getTopClubeVictoriesOnAverageOnThisMonth(): List<Pair<Clube, Double>> {
		val start = getMonthStartInMillis()

		val clubeMembersWithVictories = transaction(Databases.databaseNetwork) {
			ClubeMembers.join(EventVictories, JoinType.INNER, ClubeMembers.id, EventVictories.user).select {
				EventVictories.wonAt greaterEq start and (EventVictories.event neq "Chat")
			}.toList()
		}

		val groupedByClubes = clubeMembersWithVictories.groupBy { it[ClubeMembers.clube] }

		val clubeVictoriesByPlayer = mutableMapOf<Clube, MutableList<Int>>()

		groupedByClubes.forEach { clubeId, list ->
			val clube = transaction(Databases.databaseNetwork) { Clube.findById(clubeId)!! }
			val members = clube.retrieveMembers()

			// Only clubes with more than 5 members yay
			if (members.size >= 5) {
				val times = mutableSetOf<Long>()
				val entry = clubeVictoriesByPlayer.getOrPut(clube) { mutableListOf() }

				members.forEach { clubeMember ->
					// We need to retrieve the member list because we need to make an average of EVERYONE
					// If we only used the "list", we would ignore members that do not have any victories
					entry.add(list.filter { it[EventVictories.wonAt] !in times }.count { it[ClubeMembers.id].value == clubeMember.id.value })

					list.filter { it[ClubeMembers.id].value == clubeMember.id.value }.forEach {
						times.add(it[EventVictories.wonAt])
					}
				}
			}
		}

		return clubeVictoriesByPlayer.map {
			it.key to it.value.average()
		}.sortedByDescending { it.second }
	}
}