package net.perfectdreams.dreamscoreboard.utils

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamscoreboard.DreamScoreboard
import net.perfectdreams.dreamscoreboard.events.PlayerScoreboardRefreshEvent
import net.perfectdreams.dreamvanish.DreamVanishAPI
import net.perfectdreams.dreamvote.DreamVote
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.RenderType
import org.bukkit.scoreboard.Team
import java.text.DecimalFormat
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class PlayerScoreboard(val m: DreamScoreboard, val player: Player, val phoenix: PhoenixScoreboard) {
	companion object {
		private val PANTUFA_ICON = "\uE238"
	}

	var lastIndex = 15
	var randomEmote = "?"

	init {
		phoenix.setTitle("§4§lSparkly§b§lPower")
		player.scoreboard = phoenix.scoreboard
	}

	fun updateScoreboard() {
		// m.logger.info { "Updating scoreboards for ${player}..." }
		if (DreamScoreboard.CURRENT_TICK == 0)
			randomEmote = DreamScoreboard.EMOTES.random()

		// Just a note: How to offset a image in the title with resource packs
		// phoenix.setTitle("\uF809\uF822\uE23B§6✪ §r$randomEmote §4§lSparkly§b§lPower §r$randomEmote §6✪")
		if (player.isBedrockClient) {
			phoenix.setTitle("§6✪ §r§f$randomEmote §4§lSparkly§b§lPower §r§f$randomEmote §6✪")
		} else {
			phoenix.setTitle("§6✪ §r§f$randomEmote §f\ue260 §r§f$randomEmote §6✪")
		}

		// This doesn't need to be here? Please test!
		// Adding the player to a new team breaks glow
		/* val noCollisionTeam = phoenix.scoreboard.getTeam("nocoll") ?: phoenix.scoreboard.registerNewTeam("nocoll")
		noCollisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)

		if (!noCollisionTeam.hasEntry(player.name))
			noCollisionTeam.addEntry(player.name) */

		// We don't show the player's health below their name anymore because, when a scoreboard uses a criteria that isn't a DUMMY criteria, it is registered in vanilla's scoreboard system
		// And the issue with this is that the scoreboard is looped when the "awardStat" is called, and that's bad because it can impact performance
		// with a lot of players ("getScoreboardScores" is using 0.50% of the server main thread)
		// If we really want to, we can manually track the health below their name later
		// if (phoenix.scoreboard.getObjective("healthBelowName") == null) {
		// 	 val healthObj = phoenix.scoreboard.registerNewObjective("healthBelowName", Criteria.HEALTH, "§c♥")
		// 	 healthObj.displaySlot = DisplaySlot.BELOW_NAME
		// 	 healthObj.renderType = RenderType.HEARTS
		// }

		// Displays the player's ping on the TAB screen
		// Before we used the player's health, but it looked kinda bad tbh
		if (phoenix.scoreboard.getObjective("pingPlayerList") == null) {
			val healthObj = phoenix.scoreboard.registerNewObjective("pingPlayerList", Criteria.DUMMY, "ms")
			healthObj.displaySlot = DisplaySlot.PLAYER_LIST
			healthObj.renderType = RenderType.INTEGER
		}

		val tps = Bukkit.getTPS()
		val tpsNow = "%.2f".format(tps[0])

		val fancyWorldName = when (player.world.name) {
			"world" -> "Survival"
			else -> player.world.name
		}

		player.sendPlayerListHeaderAndFooter(
			LegacyComponentSerializer.legacySection().deserialize(
				"""§4§k||§c§k|§f§k|§b§k|§3§k|| §6»»§e»»§f»» §8§l[ §4§lSparkly§b§lPower §8§l] §f««§e««§6«« §4§k||§c§k|§f§k|§b§k|§3§k||
	|§6Modéstia à parte... esse servidor é incrível!
	|§x§f§6§1§7§0§0§om§x§e§8§1§f§0§b§oc§x§d§b§2§7§1§6§o.§x§c§d§2§f§2§1§os§x§b§f§3§7§2§c§op§x§b§2§4§0§3§8§oa§x§a§4§4§8§4§3§or§x§9§6§5§0§4§e§ok§x§8§9§5§8§5§9§ol§x§7§b§6§0§6§4§oy§x§6§d§6§8§6§f§op§x§6§0§7§0§7§a§oo§x§5§2§7§8§8§5§ow§x§4§4§8§0§9§0§oe§x§3§7§8§9§9§c§or§x§2§9§9§1§a§7§o.§x§1§b§9§9§b§2§on§x§0§e§a§1§b§d§oe§x§0§0§a§9§c§8§ot
    |§3§m✦-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-✦
	|§aMundo: §x§3§0§e§3§3§0$fancyWorldName §7• §aTPS: §x§3§0§e§3§3§0${tpsNow} §7• §aPing: §x§3§0§e§3§3§0${player.spigot().ping}ms
    |§8§m-§3§m-§b§m-§f§m-§b§m-§3§m-§8§m-
    |§6§lPrecisa de ajuda? §e/ajuda
    |§6§lAlguma dúvida? §6§oPergunte no chat!
    |§8§m-§3§m-§b§m-§f§m-§b§m-§3§m-§8§m-
""".trimMargin()
			),
			LegacyComponentSerializer.legacySection().deserialize(
				"""§8§m-§3§m-§b§m-§f§m-§b§m-§3§m-§8§m
    |§f
    |§f
	|§f
	|§f$PANTUFA_ICON
	|§f
	|§f
    |§f锈 §bQuer ajudar o servidor? Então compre VIP! §f锈
    |§3https://sparklypower.net/loja
    |
    |§f閍 §bVote no servidor para receber recompensas incríveis! §f閍
    |§3https://sparklypower.net/votar
    |
    |§f閌 §7SparklyPower é o servidor oficial da Loritta Morenitta! • https://loritta.website/ §f閌
    |§3§m✦-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-✦
""".trimMargin()
			)
		)

		if (DreamScoreboard.CURRENT_TICK == 0) {
			// Teams are only updated once every 16 seconds because:
			// * It has a lot of team requests, which can cause lag
			// * It doesn't really need to be updated every single task, since most of the times the teams
			// doesn't really change that fast
			//
			// In the future we could set up a way for plugins to register for clubes changes, then we would be able to
			// request an update without causing performance issues
			setupTeams()
		}

		//    |§e§lSeja bem-vind${player.artigo} ${player.displayName}§e§l!
		//    |§6Modéstia à parte... esse servidor é incrível!
		//    |§7Lembre-se... você é incrível, continue sendo uma pessoa maravilhosa e ajude a
		//    |§7transformar o mundo em um lugar melhor!
		val playerScoreboardRefreshEvent = PlayerScoreboardRefreshEvent(
			player,
			phoenix
		) {
			var idx = 15

			idx = setupPlayersOnline(idx)
			phoenix.setText("§c", idx--)

			if (DreamScoreboard.CURRENT_TICK == 0) {
				idx = setupClock(idx)
				phoenix.setText("§c", idx--)

				idx = setupMoney(idx)

				phoenix.setText("§c", idx--)
				idx = setupActiveEvents(idx)
			}

			if (DreamScoreboard.CURRENT_TICK == 1) {
				idx = setupUpcomingEvents(idx)
			}

			if (DreamScoreboard.CURRENT_TICK == 2) {
				idx = setupStaff(idx)
			}

			if (DreamScoreboard.CURRENT_TICK == 3) {
				idx = setupLastVoter(idx)
				phoenix.setText("§c", idx--)
				idx = setupFacebook(idx)
				phoenix.setText("§c", idx--)
				idx = setupTwitter(idx)
				phoenix.setText("§c", idx--)
				idx = setupDiscord(idx)
			}

			idx
		}
		playerScoreboardRefreshEvent.callEvent()
		val usedLines = playerScoreboardRefreshEvent.block.invoke(this)

		val lastIndex = this.lastIndex

		for (idx in usedLines downTo (Math.max(1, lastIndex))) {
			phoenix.removeLine(idx)
		}

		this.lastIndex = usedLines
	}

	private fun setupPlayersOnline(_idx: Int): Int {
		var idx = _idx
		phoenix.setText("§3➦ §b§lPlayers Online", idx--)
		val playerCount = Bukkit.getOnlinePlayers().size
		val specialText = when (playerCount) {
			in 109..Integer.MAX_VALUE -> " ✧ﾟ･: *ヽ(◕ヮ◕ヽ)"
			in 90..99 -> " ヾ(⌐■_■)ノ♪"
			in 80..89 -> "(づ￣ ³￣)づ"
			in 70..79 -> "(｡◕‿‿◕｡)"
			in 60..69 -> "(◕‿◕✿)"
			in 50..59 -> "ʕᵔᴥᵔʔ"
			in 40..49 -> "ʕ•ᴥ•ʔ"
			in 30..39 -> "\\ (•◡•) /"
			in 20..29 -> ":D"
			in 10..19 -> ":)"
			else -> ""
		}

		// We keep it as 120 for nostalgic purposes
		phoenix.setText("§b${playerCount}§3/§b120 §d${specialText}", idx--)
		return idx
	}

	private fun setupClock(_idx: Int): Int {
		var idx = _idx
		phoenix.setText("§4➦ §c§lHorário Atual", idx--)
		val localTime = LocalTime.now(ZoneId.of("America/Sao_Paulo"))
		phoenix.setText("§c${String.format("%02d", localTime.hour)}§4:§c${String.format("%02d", localTime.minute)}", idx--)
		return idx
	}

	private fun setupStaff(_idx: Int): Int {
		var idx = _idx
		val staffs = onlinePlayers().filter { it.hasPermission("pocketdreams.soustaff") && !DreamVanishAPI.isQueroTrabalhar(it) }

		phoenix.setText("§6➦ §e§lStaff Online", idx--)

		if (staffs.isNotEmpty()) {
			staffs.forEach {
				phoenix.setText("§e${it.name}", idx--)
			}
		} else {
			phoenix.setText("§eNinguém... :(", idx--)
		}
		return idx
	}

	private fun setupMoney(_idx: Int): Int {
		var idx = _idx
		phoenix.setText("§2➦ §a§lSonecas", idx--)
		val df = DecimalFormat("#")
		df.maximumFractionDigits = 8
		phoenix.setText("§a${df.format(player.balance)}$", idx--)
		return idx
	}

	private fun setupActiveEvents(_idx: Int): Int {
		var idx = _idx
		phoenix.setText("§6➦ §e§lEventos Ativos", idx--)
		val events = DreamCore.INSTANCE.dreamEventManager.getRunningEvents()
		if (events.isEmpty()) {
			phoenix.setText("§eNenhum... :(", idx--)
		} else {
			for (event in events) {
				phoenix.setText("§e${event.eventName}", idx--)
			}
		}
		return idx
	}

	private fun setupUpcomingEvents(_idx: Int): Int {
		var idx = _idx
		phoenix.setText("§5➦ §d§lA seguir...", idx--)
		val events = DreamCore.INSTANCE.dreamEventManager.getUpcomingEvents()

		if (events.isEmpty()) {
			phoenix.setText("§dNenhum... :(", idx--)
		} else {
			val hasPlayers = events.filter { Bukkit.getOnlinePlayers().size >= it.requiredPlayers }
			val notEnoughPlayers = events.filter { it.requiredPlayers > Bukkit.getOnlinePlayers().size }
			for (ev in hasPlayers.sortedBy { (it.delayBetween + it.lastTime) - System.currentTimeMillis() }) {
				val diff = (ev.delayBetween + ev.lastTime) - System.currentTimeMillis()

				if (diff >= 0) {
					var fancy = ""
					if (diff >= (60000 * 60)) {
						val minutes = ((diff / (1000 * 60)) % 60)
						val hours = ((diff / (1000 * 60 * 60)) % 24)
						fancy = String.format(
							"%dh%dm",
							hours,
							minutes
						)
					} else if (diff >= 60000) {
						fancy = String.format(
							"%dm",
							TimeUnit.MILLISECONDS.toMinutes(diff),
							TimeUnit.MILLISECONDS.toSeconds(diff) -
									TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(diff))
						)
					} else {
						fancy = String.format(
							"%ds",
							TimeUnit.MILLISECONDS.toSeconds(diff) -
									TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(diff))
						)
					}
					phoenix.setText("§d" + ev.eventName + " (" + fancy + ")", idx--)
				} else {
					// It will start soon!!
					phoenix.setText("§d" + ev.eventName, idx--)
				}
			}
			for (ev in notEnoughPlayers.sortedBy { it.requiredPlayers }) {
				val requiredCount = ev.requiredPlayers - Bukkit.getOnlinePlayers().size
				val str = if (requiredCount == 1) "player" else "players"
				phoenix.setText("§d" + ev.eventName + " (+$requiredCount $str)", idx--)
			}
		}

		return idx
	}

	private fun setupLastVoter(_idx: Int): Int {
		var idx = _idx
		phoenix.setText("§5➦ §d§lÚltimo Votador", idx--)
		if (DreamVote.INSTANCE.lastVoter == null) {
			phoenix.setText("§dNinguém... §6/votar", idx--)
		} else {
			phoenix.setText("§d${DreamVote.INSTANCE.lastVoter}", idx--)
		}
		return idx
	}

	private fun setupFacebook(_idx: Int): Int {
		var idx = _idx
		phoenix.setText("§1➦ §9§lLoritta", idx--)
		phoenix.setText("§9loritta.website", idx--)
		return idx
	}

	private fun setupTwitter(_idx: Int): Int {
		var idx = _idx
		phoenix.setText("§1➦ §9§lTwitter", idx--)
		phoenix.setText("§9@SparklyPower", idx--)
		return idx
	}

	private fun setupDiscord(_idx: Int): Int {
		var idx = _idx
		phoenix.setText("§1➦ §9§lDiscord", idx--)
		phoenix.setText("§6/discord", idx--)
		return idx
	}

	private fun setupTeams() {
		val scoreboard = phoenix.scoreboard
		if (phoenix.scoreboard != player.scoreboard)
			player.scoreboard = scoreboard

		// m.logger.info { "Setting up scoreboard teams for ${player}..." }

		for (player in Bukkit.getOnlinePlayers()) {
			val playerGlowColor = if (player.hasPermission("dreamscoreboard.glowing")) {
				player.persistentDataContainer.get(DreamScoreboard.GLOW_COLOR_KEY)
					?.let { ChatColor.valueOf(it) }
			} else
				null

			val tabPrefixColor = when {
				playerGlowColor != null -> playerGlowColor
				player.hasPermission("group.dono") -> ChatColor.GREEN
				player.hasPermission("group.admin") -> ChatColor.RED
				player.hasPermission("group.coordenador") -> ChatColor.DARK_PURPLE
				player.hasPermission("group.moderador") -> ChatColor.DARK_AQUA
				player.hasPermission("group.suporte") -> ChatColor.GOLD
				player.hasPermission("group.construtor") -> ChatColor.WHITE
				player.hasPermission("group.vip++") -> ChatColor.AQUA
				player.hasPermission("group.vip+") -> ChatColor.AQUA
				player.hasPermission("group.vip") -> ChatColor.AQUA
				else -> ChatColor.WHITE
			}

			var prefix = when {
				player.hasPermission("group.dono") -> "§a§l[Dono] "
				player.hasPermission("group.admin") -> "§4§l[Admin] "
				player.hasPermission("group.coordenador") -> "§5[Construtor] "
				player.hasPermission("group.moderador") -> "§9§l[Moderador] "
				player.hasPermission("group.suporte") -> "§6§l[Suporte] "
				player.hasPermission("group.construtor") -> "§l[Construtor] "
				player.hasPermission("group.vip++") -> "§b[VIP§6++§b] "
				player.hasPermission("group.vip+") -> "§b[VIP§6+§b] "
				player.hasPermission("group.vip") -> "§b[VIP§b] "
				else -> "§f"
			}

			val clubePrefix = m.cachedClubesPrefixes[player]

			if (clubePrefix != null) {
				prefix = "$tabPrefixColor[$clubePrefix$tabPrefixColor] "
			}

			if (prefix.length > 16) {
				prefix = prefix.replace(DreamScoreboard.FORMATTING_REGEX, "")
			}

			if (prefix.length > 16) {
				prefix = prefix.stripColors()
			}

			val teamPrefix = when {
				player.hasPermission("group.dono") -> "0"
				player.hasPermission("group.admin") -> "1"
				player.hasPermission("group.moderador") -> "2"
				player.hasPermission("group.suporte") -> "3"
				player.hasPermission("group.construtor") -> "4"
				player.hasPermission("group.vip++") -> "5"
				player.hasPermission("group.vip+") -> "6"
				player.hasPermission("group.vip") -> "7"
				clubePrefix != null -> "8"
				else -> "9"
			}
			val suffix = when {
				player.hasPermission("group.dono") -> " §f閌"
				player.hasPermission("group.admin") -> " §f閌"
				player.hasPermission("group.moderador") -> " §f閌"
				player.hasPermission("group.suporte") -> " §f閌"
				player.hasPermission("group.construtor") -> " §f閌"
				player.hasPermission("group.vip++") -> " §f娀"
				player.hasPermission("group.vip+") -> " §f閍"
				player.hasPermission("group.vip") -> " §f锈"
				else -> "§f"
			}

			var teamName = player.name
			// Group users in the same clube together
			if (clubePrefix != null)
				teamName += clubePrefix.stripColors().hashCode().toString().toCharArray().take(3).joinToString("")

			if (teamName.length > 15) {
				teamName = teamName.substring(0, 14)
			}

			teamName = teamPrefix + teamName
			val t = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)
			t.prefix = prefix
			t.suffix = suffix
			// Disable collision
			t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)

			if (player.hasPermission("dreamscoreboard.glowing")) {
				t.color = when {
					playerGlowColor != null -> playerGlowColor
					player.hasPermission("group.dono") -> ChatColor.GREEN
					player.hasPermission("group.admin") -> ChatColor.RED
					player.hasPermission("group.coordenador") -> ChatColor.DARK_PURPLE
					player.hasPermission("group.moderador") -> ChatColor.DARK_AQUA
					player.hasPermission("group.suporte") -> ChatColor.GOLD
					player.hasPermission("group.construtor") -> ChatColor.WHITE
					player.hasPermission("group.vip++") -> ChatColor.AQUA
					player.hasPermission("group.vip+") -> ChatColor.AQUA
					player.hasPermission("group.vip") -> ChatColor.AQUA
					else -> ChatColor.WHITE
				}
			} else {
				player.isGlowing = false
			}

			if (!t.hasEntry(player.name))
				t.addEntry(player.name)

			val p = phoenix.scoreboard.getObjective("pingPlayerList")
			if (p != null) {
				val score = p.getScore(player.name)
				score.score = player.spigot().ping
			}
		}
	}
}
