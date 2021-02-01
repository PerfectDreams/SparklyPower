package net.perfectdreams.dreamchat.listeners

import club.minnced.discord.webhook.send.WebhookMessageBuilder
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.kevinsawicki.http.HttpRequest
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.perfectdreams.dreamcasamentos.DreamCasamentos
import net.perfectdreams.dreamchat.DreamChat
import net.perfectdreams.dreamchat.dao.ChatUser
import net.perfectdreams.dreamchat.dao.DiscordAccount
import net.perfectdreams.dreamchat.events.ApplyPlayerTagsEvent
import net.perfectdreams.dreamchat.tables.ChatUsers
import net.perfectdreams.dreamchat.tables.DiscordAccounts
import net.perfectdreams.dreamchat.tables.PremiumUsers
import net.perfectdreams.dreamchat.utils.*
import net.perfectdreams.dreamclubes.utils.ClubeAPI
import net.perfectdreams.dreamcore.network.DreamNetwork
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.DreamUtils.jsonParser
import net.perfectdreams.dreamcore.utils.discord.DiscordMessage
import net.perfectdreams.dreamcore.utils.extensions.artigo
import net.perfectdreams.dreamcore.utils.extensions.girl
import org.apache.commons.lang3.StringUtils
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.set

class ChatListener(val m: DreamChat) : Listener {
	val chatCooldownCache = Caffeine.newBuilder()
		.expireAfterWrite(1L, TimeUnit.MINUTES)
		.build<Player, Long>()
		.asMap()

	val lastMessageCache = Caffeine.newBuilder()
		.expireAfterAccess(1L, TimeUnit.MINUTES)
		.build<Player, String>()
		.asMap()

	@EventHandler
	fun onJoin(e: PlayerJoinEvent) {
		scheduler().schedule(m, SynchronizationContext.ASYNC) {
			transaction {
				val result = ChatUsers.select { ChatUsers.id eq e.player.uniqueId }.firstOrNull() ?: return@transaction
				val nickname = result[ChatUsers.nickname] ?: return@transaction

				e.player.setDisplayName(nickname)
				e.player.setPlayerListName(nickname)
			}
		}
	}

	@EventHandler
	fun onLeave(e: PlayerQuitEvent) {
		m.lockedTells.remove(e.player)
		chatCooldownCache.remove(e.player)
		lastMessageCache.remove(e.player)
		m.hideTells.remove(e.player)
	}

	@EventHandler
	fun onTag(e: ApplyPlayerTagsEvent) {
		if (e.player.uniqueId == m.eventoChat.lastWinner) {
			e.tags.add(
				PlayerTag(
					"§b§lD",
					"§b§lDatilógraf${player.artigo}",
					listOf(
						"§r§b${e.player.displayName}§r§7 ficou atento no chat e",
						"§7e preparad${e.player.artigo} no teclado para conseguir",
						"§7vencer o Evento Chat em primeiro lugar!"
					),
					null,
					false
				)
			)
		}
	}

	@EventHandler
	fun onJoin(e: PlayerCommandPreprocessEvent) {
		if (!m.eventoChat.running)
			return

		val cmd = e.message
			.split(" ")[0]
			.substring(1)
			.toLowerCase()

		if (cmd == "calc" || cmd == "calculadora")
			e.isCancelled = true
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	fun onChat(e: AsyncPlayerChatEvent) {
		e.isCancelled = true

		val lockedTellPlayer = m.lockedTells[e.player]
		if (lockedTellPlayer != null) {
			if (Bukkit.getPlayerExact(lockedTellPlayer) != null) {
				scheduler().schedule(m) {
					e.player.performCommand("tell $lockedTellPlayer ${e.message}")
				}
				return
			} else {
				e.player.sendMessage("§cO seu chat travado foi desativado devido á saida do player §b${lockedTellPlayer}§c")
				e.player.sendMessage("§cPor segurança, nós não enviamos a sua última mensagem, já que ela iria para o chat normal e não para a sua conversa privada")
				e.isCancelled = true
				m.lockedTells.remove(e.player)
				return
			}
		}

		val player = e.player
		val rawMessage = e.message
		var message = rawMessage

		if (m.eventoChat.running && m.eventoChat.event.process(e.player, message))
			m.eventoChat.finish(player)

		val lastMessageSentAt = chatCooldownCache.getOrDefault(player, 0)
		val diff = System.currentTimeMillis() - lastMessageSentAt

		if (500 >= diff) {
			player.sendMessage("§cEspere um pouco para enviar outra mensagem no chat!")
			return
		}

		if (3500 >= diff) {
			val lastMessageContent = lastMessageCache[player]
			if (lastMessageContent != null) {
				if (5 > StringUtils.getLevenshteinDistance(lastMessageContent, message) && !m.eventoChat.running) {
					player.sendMessage("§cNão mande mensagens iguais ou similares a última que você mandou!")
					return
				}
			}
		}

		val upperCaseChars = e.message.toCharArray().filter { it.isUpperCase() }
		val upperCasePercent = (e.message.length * upperCaseChars.size) / 100

		if (e.message.length >= 20 && upperCasePercent > 55) {
			player.sendMessage("§cEvite usar tanto CAPS LOCK em suas mensagens! Isso polui o chat!")
			e.message = e.message.toLowerCase()
		}

		chatCooldownCache[player] = System.currentTimeMillis()
		lastMessageCache[player] = e.message.toLowerCase()

		// Vamos verificar se o cara só está falando o nome do cara da Staff
		for (onlinePlayers in Bukkit.getOnlinePlayers()) {
			if (onlinePlayers.hasPermission("sparklypower.soustaff")) {
				if (message.equals(onlinePlayers.name, true)) {
					player.sendMessage("§cSe você quiser chamar alguém da Staff, por favor, coloque a pergunta JUNTO com a mensagem, obrigado! ^-^")
					return
				}
			}
		}

		message = message.translateColorCodes()

		if (!player.hasPermission("dreamchat.chatcolors")) {
			message = message.replace(DreamChat.CHAT_REGEX, "")
		}

		if (!player.hasPermission("dreamchat.chatformatting")) {
			message = message.replace(DreamChat.FORMATTING_REGEX, "")
		}

		if (ChatUtils.isMensagemPolemica(message)) {
			DreamNetwork.PANTUFA.sendMessageAsync(
				"387632163106848769",
				"**`" + player.name.replace("_", "\\_") + "` escreveu uma mensagem potencialmente polêmica no chat!**\n```" + message + "```\n"
			)
		}

		if (message.startsWith("./") || message.startsWith("-/")) { // Caso o player esteja dando um exemplo de um comando, por exemplo, "./survival"
			message = message.removePrefix(".")
		}

		message = ChatUtils.beautifyMessage(player, message)

		// Hora de "montar" a mensagem
		val textComponent = TextComponent()

		val playOneMinute = player.getStatistic(Statistic.PLAY_ONE_MINUTE)

		var prefix = VaultUtils.chat.getPlayerPrefix(player)

		val api = LuckPermsProvider.get()

		val luckyUser = api.userManager.getUser(e.player.uniqueId)

		val chatUser = transaction(Databases.databaseNetwork) {
			ChatUser.find {
				ChatUsers.id eq e.player.uniqueId
			}.firstOrNull()
		}

		if ((luckyUser?.primaryGroup ?: "default") == "default" && (7200 * 20) > playOneMinute) {
			prefix = if (e.player.girl) {
				"§eNovata"
			} else {
				"§eNovato"
			}
		}

		if ((luckyUser?.primaryGroup ?: "default") == "default" && m.partners.contains(e.player.uniqueId)) {
			prefix = if (e.player.girl) {
				"§5§lParceira"
			} else {
				"§5§lParceiro"
			}
		}

		if ((luckyUser?.primaryGroup ?: "default") == "default" && m.artists.contains(e.player.uniqueId)) {
			prefix = "§5§lDesenhista"
		}

		if (chatUser != null) {
			if (chatUser.nickname != null && !e.player.hasPermission("dreamchat.nick")) {
				transaction(Databases.databaseNetwork) {
					chatUser.nickname = null
				}
				e.player.setDisplayName(null)
				e.player.setPlayerListName(null)
			}

			if (chatUser.tag != null && !e.player.hasPermission("dreamchat.querotag")) {
				transaction(Databases.databaseNetwork) {
					chatUser.tag = null
				}
			}

			if (chatUser.tag != null) {
				prefix = chatUser.tag
			}
		}

		textComponent += "§8[${prefix.translateColorCodes()}§8] ".toTextComponent().apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, "kk eae men".toBaseComponent())
		}

		val event = ApplyPlayerTagsEvent(player, mutableListOf())
		Bukkit.getPluginManager().callEvent(event)

		if (m.topEntries[0].equals(e.player.name, true)) {
			event.tags.add(
				PlayerTag(
					"§2§lM",
					"§2§lMagnata",
					listOf(
						"§r§b${player.displayName}§r§7 é a pessoa mais rica do §4§lSparkly§b§lPower§r§7!",
						"",
						"§7Eu duvido você conseguir passar del${if (player.girl) "a" else "e"}, será que você tem as habilidades para conseguir? ;)"
					),
					"/money top",
					true
				)
			)
		}

		if (m.topEntries[1].equals(e.player.name, true)) {
			event.tags.add(
				PlayerTag(
					"§2§lL",
					"§2§lLuxuos${player.artigo}",
					listOf(
						"§r§b${player.displayName}§r§7 é a segunda pessoa mais rica do §4§lSparkly§b§lPower§r§7!",
						"",
						"§7Eu duvido você conseguir passar del${if (player.girl) "a" else "e"}, será que você tem as habilidades para conseguir? ;)"
					),
					"/money top",
					true
				)
			)
		}

		if (m.topEntries[2].equals(e.player.name, true)) {
			event.tags.add(
				PlayerTag(
					"§2§lB",
					"§2§l${if (!player.girl) { "Burguês" } else { "Burguesa" }}",
					listOf(
						"§r§b${player.displayName}§r§7 é a terceira pessoa mais rica do §4§lSparkly§b§lPower§r§7!",
						"",
						"§7Eu duvido você conseguir passar del${if (player.girl) "a" else "e"}, será que você tem as habilidades para conseguir? ;)"
					),
					"/money top",
					true
				)
			)
		}

		McMMOTagsUtils.addTags(e, event)

		if (m.oldestPlayers.getOrNull(0)?.first == e.player.uniqueId) {
			event.tags.add(
				PlayerTag(
					"§4§lV",
					"§4§lViciad${player.artigo}",
					listOf(
						"§r§b${player.displayName}§r§7 é a pessoa com mais tempo online no §4§lSparkly§b§lPower§r§7!",
						"",
						"§7Eu duvido você conseguir passar del${if (player.girl) "a" else "e"}, será que você tem as habilidades para conseguir? ;)"
					),
					"/online",
					true
				)
			)
		}

		if (m.oldestPlayers.getOrNull(1)?.first == e.player.uniqueId) {
			event.tags.add(
				PlayerTag(
					"§4§lD",
					"§4§lDevotad${player.artigo}",
					listOf(
						"§r§b${player.displayName}§r§7 é a segunda pessoa com mais tempo online no §4§lSparkly§b§lPower§r§7!",
						"",
						"§7Eu duvido você conseguir passar del${if (player.girl) "a" else "e"}, será que você tem as habilidades para conseguir? ;)"
					),
					"/online",
					true
				)
			)
		}

		if (m.oldestPlayers.getOrNull(2)?.first == e.player.uniqueId) {
			event.tags.add(
				PlayerTag(
					"§4§lF",
					"§4§lFanátic${player.artigo}",
					listOf(
						"§r§b${player.displayName}§r§7 é a terceira pessoa com mais tempo online no §4§lSparkly§b§lPower§r§7!",
						"",
						"§7Eu duvido você conseguir passar del${if (player.girl) "a" else "e"}, será que você tem as habilidades para conseguir? ;)"
					),
					"/online",
					true
				)
			)
		}

		if (event.tags.isNotEmpty()) {
			// omg tags!
			// Exemplos:
			// Apenas uma tag
			// [Último Votador]
			// Duas tags
			// [DS]
			//
			// Para não encher o chat de tags
			// Todas as tags são EXTENDIDAS por padrão
			// Mas, se o cara tiver mais de uma tag, todas ficam ENCURTADAS
			val textTags = "§8[".toTextComponent()

			val tags = event.tags

			if (tags.size == 1) {
				for ((index, tag) in tags.withIndex()) {
					val textTag = tag.tagName.toTextComponent().apply {
						if (tag.description != null) {
							hoverEvent = HoverEvent(
								HoverEvent.Action.SHOW_TEXT,
								"§6✪ §f${tag.tagName} §6✪\n§7${tag.description.joinToString("\n§7")}".toBaseComponent()
							)
						}
						if (tag.suggestCommand != null) {
							clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tag.suggestCommand)
						}
					}
					textTags += textTag
				}
			} else {
				// We have multiple tags that can be used, we will select the one that has the most tags assigned to it
				val wordTags = listOf(
					WordTagFitter("SPARKLYPOWER"),
					WordTagFitter("SPARKLY"),
					WordTagFitter("POWER"),
					WordTagFitter("LORITTA"),
					WordTagFitter("PANTUFA"),
					WordTagFitter("FELIZ"),
					WordTagFitter("DILMA"),
					WordTagFitter("CRAFT"),
					WordTagFitter("LORI"),
					WordTagFitter("MINE"),
					WordTagFitter("DIMA")
				)

				wordTags.forEach { wordTag ->
					tags.forEach { tag ->
						wordTag.tryFittingInto(tag)
					}
				}

				val whatTagShouldBeUsed = wordTags.maxBy {
					it.tagPositions.filterNotNull().distinct().size
				}

				// Add the tags from the whatTagShouldBeUsed tag fitter to a "displayInOrderTags"
				// Then we append the player's tags and do a .distinct() over it
				// So technically the "word tag fitter" will form a entire word, woo!
				val displayInOrderTags = if (whatTagShouldBeUsed == null)
					tags
				else (
						whatTagShouldBeUsed.tagPositions
							.filterNotNull()
								+ tags
						).distinct()

				for (tag in displayInOrderTags) {
					textTags += tag.small.toTextComponent().apply {
						if (tag.description != null) {
							hoverEvent = HoverEvent(
								HoverEvent.Action.SHOW_TEXT,
								"§6✪ §f${tag.tagName} §6✪\n§7${tag.description.joinToString("\n§7")}".toBaseComponent()
							)
						}
						if (tag.suggestCommand != null) {
							clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tag.suggestCommand)
						}
					}
				}
			}

			textTags.addExtra("§8] ")
			textComponent += textTags
		}

		/* val panela = DreamPanelinha.INSTANCE.getPanelaByMember(player)
		if (panela != null) {
			val tag = "§8«§3${panela.tag}§8» ".toTextComponent()
			tag.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
					"""${panela.name}
						|§eDono: §b${panela.owner}
						|§eMembros: §6${panela.members.size}
						|§eKDR: §6${panela.calculateKDR()}
					""".trimMargin().toBaseComponent())
			textComponent += tag
		} */

		val clube = ClubeAPI.getPlayerClube(player)

		if (clube != null) {
			val clubeTag = "§8«§7${clube.shortName}§8» "

			textComponent += clubeTag.toTextComponent().apply {
				this.hoverEvent = HoverEvent(
					HoverEvent.Action.SHOW_TEXT,
					"§b${clube.name}".toBaseComponent()
				)
			}
		}

		val casal = DreamCasamentos.INSTANCE.getMarriageFor(player)
		if (casal != null) {
			val heart = "§4❤ ".toTextComponent()
			val offlinePlayer1 = Bukkit.getOfflinePlayer(casal.player1)
			val offlinePlayer2 = Bukkit.getOfflinePlayer(casal.player2)

			heart.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, "§4❤ §d§l${DreamCasamentos.INSTANCE.getShipName(offlinePlayer1?.name ?: "???", offlinePlayer2?.name ?: "???")} §4❤\n\n§6Casad${MeninaAPI.getArtigo(player)} com: §b${Bukkit.getOfflinePlayer(casal.getPartnerOf(player))?.name ?: "???"}".toBaseComponent())
			textComponent += heart
		}

		textComponent += TextComponent(*"§7${player.displayName}".translateColorCodes().toBaseComponent()).apply {
			clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "${player.name} ")
			var toDisplay = player.displayName

			if (!player.displayName.stripColors()!!.contains(player.name)) {
				toDisplay = player.displayName + " §a(§b${player.name}§a)§r"
			}

			val input = playOneMinute / 20
			val numberOfDays = input / 86400
			val numberOfHours = input % 86400 / 3600
			val numberOfMinutes = input % 86400 % 3600 / 60

			val rpStatus = if (player.resourcePackStatus == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
				"§a✔"
			} else {
				"§c✗"
			}

			val isMinecraftPremium = transaction(Databases.databaseNetwork) {
				PremiumUsers.select { PremiumUsers.crackedUniqueId eq player.uniqueId }
					.count() != 0L
			}

			val mcPremiumStatus = if (isMinecraftPremium) {
				"§a✔"
			} else {
				"§c✗"
			}

			val aboutLines = mutableListOf(
				"§6✪ §a§lSobre ${player.artigo} §r§b${toDisplay}§r §6✪",
				"",
				"§eGênero: §d${if (!player.girl) { "§3♂" } else { "§d♀" }}",
				"§eGrana: §6${player.balance} Sonhos",
				"§eKDR: §6PvP é para os fracos, 2bj :3",
				"§eOnline no SparklyPower Survival por §6$numberOfDays dias§e, §6$numberOfHours horas §ee §6$numberOfMinutes minutos§e!",
				"§eUsando a Resource Pack? $rpStatus",
				"§eMinecraft Original? $mcPremiumStatus"
			)

			val discordAccount = transaction(Databases.databaseNetwork) {
				DiscordAccount.find { DiscordAccounts.minecraftId eq player.uniqueId }.firstOrNull()
			}

			if (discordAccount != null) {
				val cachedDiscordAccount = m.cachedDiscordAccounts.getOrPut(discordAccount.discordId, {
					val request = HttpRequest.get("https://discordapp.com/api/v6/users/${discordAccount.discordId}")
						.userAgent("SparklyPower DreamChat")
						.header("Authorization", "Bot ${m.config.getString("pantufa-token")}")

					val statusCode = request.code()
					if (statusCode != 200)
						Optional.empty()
					else {
						val json = jsonParser.parse(request.body())

						Optional.of(
							DiscordAccountInfo(
								json["username"].string,
								json["discriminator"].string
							)
						)
					}
				})

				if (cachedDiscordAccount.isPresent) {
					val info = cachedDiscordAccount.get()
					aboutLines.add("§eDiscord: §6${info.name}§8#§6${info.discriminator} §8(§7${discordAccount.discordId}§8)")
				}
			}

			val adoption = DreamCasamentos.INSTANCE.getParentsOf(player)

			if (adoption != null) {
				aboutLines.add("")
				aboutLines.add("§eParentes: §b${Bukkit.getOfflinePlayer(adoption.player1)?.name ?: "???"} §b${Bukkit.getOfflinePlayer(adoption.player2)?.name ?: "???"}")
			}
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
				aboutLines.joinToString("\n").toBaseComponent()
			)
		}

		textComponent += " §6➤ ".toBaseComponent()

		val split = message.split("(?=\\b[ ])")
		var previous: String? = null
		for (piece in split) {
			var editedPiece = piece
			if (previous != null) {
				editedPiece = "$previous$editedPiece"
			}
			textComponent += editedPiece.toBaseComponent()
			previous = ChatColor.getLastColors(piece)
		}

		if (DreamChat.mutedUsers.contains(player.name)) { // Usuário está silenciado
			player.spigot().sendMessage(textComponent)

			for (staff in Bukkit.getOnlinePlayers().filter { it.hasPermission("pocketdreams.soustaff")}) {
				staff.sendMessage("§8[§cSILENCIADO§8] §b${player.name}§c: $message")
			}
			return
		}

		for (onlinePlayer in Bukkit.getOnlinePlayers()) {
			// Verificar se o player está ignorando o player que enviou a mensagem
			val isIgnoringTheSender = m.userData.getStringList("ignore.${onlinePlayer.uniqueId}").contains(player.uniqueId.toString())

			if (!isIgnoringTheSender)
				onlinePlayer.spigot().sendMessage(textComponent)
		}

		val calendar = Calendar.getInstance()
		m.chatLog.appendText("[${String.format("%02d", calendar[Calendar.DAY_OF_MONTH])}/${String.format("%02d", calendar[Calendar.MONTH] + 1)}/${String.format("%02d", calendar[Calendar.YEAR])} ${String.format("%02d", calendar[Calendar.HOUR_OF_DAY])}:${String.format("%02d", calendar[Calendar.MINUTE])}] ${player.name}: $message\n")

		// Tudo OK? Então vamos verificar se a mensagem tem algo de importante para nós respondermos
		for (response in DreamChat.botResponses) {
			if (response.handleResponse(message, e)) {
				val response = response.getResponse(message, e) ?: return
				ChatUtils.sendResponseAsBot(player, response)
				return
			}
		}

		// Vamos mandar no Biscord!
		if (m.config.getBoolean("enable-chat-relay", false)) {
			val currentWebhook = DreamChat.chatWebhooks[DreamChat.currentWebhookIdx % DreamChat.chatWebhooks.size]!!
			currentWebhook.send(
				WebhookMessageBuilder()
					.setUsername(player.name)
					.setAvatarUrl("https://sparklypower.net/api/v1/render/avatar?name=${player.name}&scale=16")
					.setContent(message.stripColors()!!.replace(Regex("\\\\+@"), "@").replace("@", "@\u200B"))
					.build()
			)
			DreamChat.currentWebhookIdx++
		}
	}
}
