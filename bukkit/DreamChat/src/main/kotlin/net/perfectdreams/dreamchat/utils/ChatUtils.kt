package net.perfectdreams.dreamchat.utils

import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.perfectdreams.dreamchat.DreamChat
import net.perfectdreams.dreamchat.events.PlayerReceivePlayerChatEvent
import net.perfectdreams.dreamcore.network.DreamNetwork
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamvanish.DreamVanishAPI
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.asSequence
import kotlin.collections.filterNot
import kotlin.collections.forEach
import kotlin.collections.joinToString
import kotlin.collections.mutableListOf
import kotlin.collections.plusAssign
import kotlin.collections.set

object ChatUtils {
	fun beautifyMessage(sender: Player, str: String): String {
		var message = str

		for (player in Bukkit.getOnlinePlayers().filterNot { DreamVanishAPI.isQueroTrabalhar(it) }) {
			// User mention RegEx
			// We need to use (\b|@) because \b@? will never match!
			val regex = Regex("(\\b|@)${Regex.escape(player.name)}\\b", RegexOption.IGNORE_CASE)
			if (regex.containsMatchIn(message)) {
				message = message.replace(regex, Regex.escapeReplacement("§3${player.displayName}§f"))

				val isIgnoringTheSender = DreamChat.INSTANCE.userData.getStringList("ignore.${player.uniqueId}").contains(sender.uniqueId.toString())

				if (!isIgnoringTheSender || !DreamChat.mutedUsers.contains(sender.name)) {
					player.playSound(player.location, "perfectdreams.sfx.msn", 1F, 1F)
					player.sendActionBar("§3${sender.displayName}§a te mencionou no chat!")
				}
			}
		}

		DreamChat.INSTANCE.emojis.forEach {
			message = message.replace(it.chatFormat, it.character)
		}

		DreamChat.INSTANCE.replacers.forEach {
			message = message.replace(it.key, it.value)
		}

		if (!message[0].isUpperCase() && !message.startsWith("http")) {
			message = message[0].toUpperCase() + message.substring(1)
		}

		if (message.contains("#")) {
			message = Regex("#\\w+").replace(message, "§9$0§f")
		}

		if (message.contains("/")) {
			message = Regex("(^| )(/\\w+)\\b").replace(message, "§6$0§f")
		}

		return message
	}

	fun sendResponseAsBot(player: Player, message: String) {
		// Hora de "montar" a mensagem
		val textComponent = TextComponent()

		textComponent += TextComponent(*DreamChat.BOT_PREFIX.toBaseComponent()).apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, "kk eae men".toBaseComponent())
		}

		textComponent += TextComponent(*"§7${DreamChat.BOT_NAME}".translateColorCodes().toBaseComponent()).apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
				"""§6✪ §a§lSobre a §r${DreamChat.BOT_NAME} §6✪
						|
						|§eGênero: §d♀
						|§eGrana: §6Incontáveis Sonecas
						|§eKDR: §6PvP é para os fracos, 2bj :3
						|§eOnline no SparklyPower Survival por §6mais tempo que você
					""".trimMargin().toBaseComponent())
			clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "Pantufa... só para saber, porque você quer me marcar? Eu sou um robô e não um player!")
		}
		textComponent += " §6➤ ".toBaseComponent()

		// Para não encher o chat de mensagens da Pantufa, vamos fazer que só apareça o texto ao passar o mouse em cima da mensagem dela
		val forEveryone = textComponent.duplicate()
		val lore = mutableListOf<String>()
		val currentLoreLine = StringBuilder()
		var currentTextSize = 0

		val split = message.split("(?=\\b[ ])")
		var previous: String? = null

		for (piece in split) {
			if (currentTextSize >= 40) {
				lore += currentLoreLine.toString()
				currentTextSize = 0
			}

			var editedPiece = piece
			if (previous != null) {
				editedPiece = "$previous$editedPiece"
			}
			textComponent += editedPiece.toBaseComponent()
			currentLoreLine.append(piece)
			currentTextSize += piece.length

			previous = ChatColor.getLastColors(piece)
		}
		lore += currentLoreLine.toString()

		forEveryone.addExtra(
			"§6[Passe o Mouse em Cima]".toTextComponent()
				.apply {
					hoverEvent = HoverEvent(
						HoverEvent.Action.SHOW_TEXT,
						lore.joinToString("\n").toBaseComponent()
					)
				}
		)

		val isMainThread = Bukkit.isPrimaryThread()
		Bukkit.getOnlinePlayers().forEach {
			val receivePlayerChatEvent = PlayerReceivePlayerChatEvent(!isMainThread, PlayerReceivePlayerChatEvent.ChatSender.Bot, it)
			val isSuccess = receivePlayerChatEvent.callEvent()

			if (isSuccess) {
				if (it != player)
					it.sendMessage(forEveryone)
				else
					it.sendMessage(textComponent)
			}
		}
	}

	fun sendResponseAsLoritta(player: Player, message: String) {
		// Hora de "montar" a mensagem
		val textComponent = TextComponent()

		textComponent += TextComponent(*DreamChat.LORITTA_PREFIX.toBaseComponent()).apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, "kk eae men".toBaseComponent())
		}

		textComponent += TextComponent(*"§7${DreamChat.LORITTA_NAME}".translateColorCodes().toBaseComponent()).apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
				"""§6✪ §a§lSobre a §r${DreamChat.LORITTA_NAME} §6✪
						|
						|§eGênero: §d♀
						|§eGrana: §6Incontáveis Sonecas
						|§eKDR: §6PvP é para os fracos, 2bj :3
						|§eOnline no SparklyPower Survival por §6mais tempo que você
						|
						|§dO bot mais fofis para o Discord!
					""".trimMargin().toBaseComponent())
			clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "Loritta... só para saber, porque você quer me marcar? Eu sou um robô e não um player!")
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

		val isMainThread = Bukkit.isPrimaryThread()
		Bukkit.getOnlinePlayers().forEach {
			val receivePlayerChatEvent = PlayerReceivePlayerChatEvent(!isMainThread, PlayerReceivePlayerChatEvent.ChatSender.Bot, it)
			val isSuccess = receivePlayerChatEvent.callEvent()

			if (isSuccess) {
				it.sendMessage(textComponent)
			}
		}
	}

	fun sendResponseAsGabriela(player: Player, message: String) {
		// Hora de "montar" a mensagem
		val textComponent = TextComponent()

		textComponent += TextComponent(*DreamChat.LORITTA_PREFIX.toBaseComponent()).apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, "kk eae men".toBaseComponent())
		}

		textComponent += TextComponent(*"§5${DreamChat.GABRIELA_NAME}".translateColorCodes().toBaseComponent()).apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
				"""§6✪ §a§lSobre a §r${DreamChat.GABRIELA_NAME} §6✪
						|
						|§eGênero: §d♀
						|§eGrana: §6Incontáveis Sonecas
						|§eKDR: §6PvP é para os fracos, 2bj :3
						|§eOnline no SparklyPower Survival por §6mais tempo que você
						|
						|§dCuida das coisas de merchandising da PerfectDreams!
					""".trimMargin().toBaseComponent())
			clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "Gabriela... só para saber, porque você quer me marcar? Eu sou um robô e não um player!")
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

		val isMainThread = Bukkit.isPrimaryThread()
		Bukkit.getOnlinePlayers().forEach {
			val receivePlayerChatEvent = PlayerReceivePlayerChatEvent(!isMainThread, PlayerReceivePlayerChatEvent.ChatSender.Bot, it)
			val isSuccess = receivePlayerChatEvent.callEvent()

			if (isSuccess) {
				it.sendMessage(textComponent)
			}
		}
	}

	fun sendResponseAsGessy(player: Player, message: String) {
		// Hora de "montar" a mensagem
		val textComponent = TextComponent()

		textComponent += TextComponent(*DreamChat.GESSY_PREFIX.toBaseComponent()).apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, "kk eae men".toBaseComponent())
		}

		textComponent += TextComponent(*"§9${DreamChat.GESSY_NAME}".translateColorCodes().toBaseComponent()).apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
				"""§6✪ §a§lSobre o §r${DreamChat.GESSY_NAME} §6✪
						|
						|§eGênero: §d♂
						|§eGrana: §6Incontáveis Sonecas
						|§eKDR: §6PvP é para os fracos, 2bj :3
						|§eOnline no SparklyPower Survival por §6mais tempo que você
						|
						|§dMascote nos tempos vagos
					""".trimMargin().toBaseComponent())
			clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "Gessy... só para saber, porque você quer me marcar? Eu sou um robô e não um player!")
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

		val isMainThread = Bukkit.isPrimaryThread()
		Bukkit.getOnlinePlayers().forEach {
			val receivePlayerChatEvent = PlayerReceivePlayerChatEvent(!isMainThread, PlayerReceivePlayerChatEvent.ChatSender.Bot, it)
			val isSuccess = receivePlayerChatEvent.callEvent()

			if (isSuccess) {
				it.sendMessage(textComponent)
			}
		}
	}

	fun sendResponseAsLula(player: Player, message: String) {
		// Hora de "montar" a mensagem
		val textComponent = TextComponent()

		textComponent += TextComponent(*"§8[§4§lPolvo§8] ".toBaseComponent()).apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, "kk eae men".toBaseComponent())
		}

		textComponent += TextComponent(*"§4Lula".translateColorCodes().toBaseComponent()).apply {
			hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
				"""§6✪ §a§lSobre o §cLula §6✪
						|
						|§eGênero: §d♂
						|§eGrana: §6Incontáveis Sonecas
						|§eKDR: §6PvP é para os fracos, 2bj :3
						|§eOnline no SparklyPower Survival por §6mais tempo que você
					""".trimMargin().toBaseComponent())
			clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "Lula... só para saber, porque você quer me marcar? Eu sou um robô e não um player!")
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

		val isMainThread = Bukkit.isPrimaryThread()
		Bukkit.getOnlinePlayers().forEach {
			val receivePlayerChatEvent = PlayerReceivePlayerChatEvent(!isMainThread, PlayerReceivePlayerChatEvent.ChatSender.Bot, it)
			val isSuccess = receivePlayerChatEvent.callEvent()

			if (isSuccess) {
				it.sendMessage(textComponent)
			}
		}
	}

	fun isMensagemPolemica(message: String): Boolean {
		val message = message.toLowerCase()

		return message.matches(Regex(".*(cheat).*")) || message.matches(Regex(".*(x( |-)?ray).*")) || message.matches(Regex(".*(kill( )?aura).*")) || message.matches(Regex(".*(auto( )click).*")) || message.matches(Regex(".*(force( )field).*")) || message.matches(Regex(".*(anti( )(knock|kb)).*")) || message.matches(Regex(".*(raio( |-)?x).*")) || message.matches(Regex(".*(hack|hazuni|huzuni|nodus|weep( |-|_)?craft|flare|resilience|kryptonite|wurst|reflex).*")) || message.matches(Regex(".*(dup)")) || message.matches(Regex(".*(bug)")) || message.matches(Regex(".*(bugado).*"))
	}

	fun sendTell(sender: Player, receiver: Player, message: String) {
		val fromCanBeSeen = sender.displayName.stripColors().contains(sender.name)
		val toCanBeSeen = receiver.displayName.stripColors().contains(receiver.name)
		val isIgnoringTheSender = DreamChat.INSTANCE.userData.getStringList("ignore.${receiver.uniqueId}").contains(sender.uniqueId.toString())

		val fromUserMessage = textComponent {
			color(TextColor.color(227, 75, 227))
			append("De ")

			append(
				textComponent {
					color(NamedTextColor.AQUA)
					append(sender.displayName())
				}
			)

			if (!fromCanBeSeen)
				append(" (${sender.name})")

			append(": ")

			append(message) {
				color(NamedTextColor.LIGHT_PURPLE)
			}
		}

		val toUserMessage = textComponent {
			color(TextColor.color(227, 75, 227))
			append("Para ")

			append(
				textComponent {
					color(NamedTextColor.AQUA)
					append(receiver.displayName())
				}
			)

			if (!toCanBeSeen)
				append(" (${receiver.name})")

			append(": ")

			append(message) {
				color(NamedTextColor.LIGHT_PURPLE)
			}
		}

		if (!isIgnoringTheSender)
			receiver.sendMessage(fromUserMessage)

		sender.sendMessage(toUserMessage)

		DreamChat.INSTANCE.quickReply[receiver] = sender

		scheduler().schedule(DreamChat.INSTANCE, SynchronizationContext.ASYNC) {
			val calendar = LocalDateTime.now(TimeUtils.TIME_ZONE)
			val date = "${String.format("%02d", calendar.dayOfMonth)}/${String.format("%02d", calendar.monthValue)}/${String.format("%02d", calendar.year)} ${String.format("%02d", calendar.hour)}:${String.format("%02d", calendar.minute)}"
			DreamChat.INSTANCE.pmLog.appendText("[$date] ${sender.name} -> ${receiver.name}: $message\n")

			DreamChat.INSTANCE.tellMessagesQueue
				.add("`[$date]` \uD83D\uDD75 **`${sender.name}`** » **`${receiver.name}`**: $message")
		}

		for (staff in Bukkit.getOnlinePlayers().asSequence().filter { it.hasPermission("dreamchat.snoop") }.filter { it !in DreamChat.INSTANCE.hideTells }) {
			staff.sendMessage("§7[${sender.name} » ${receiver.name}] $message")
		}

		if (ChatUtils.isMensagemPolemica(message)) {
			DreamNetwork.PANTUFA.sendMessageAsync(
				"387632163106848769",
				"**`" + sender.name.replace("_", "\\_") + "` escreveu uma mensagem potencialmente polêmica para `${receiver.name.replace("_", "\\_")}`!**\n```" + message + "```\n"
			)
		}

		// bossbar
		if (!isIgnoringTheSender) {
			receiver.playSound(receiver.location, "perfectdreams.sfx.msn", 1F, 1F)
			val bossBar = Bukkit.createBossBar("§b${sender.displayName}§r§d: §d$message", BarColor.PINK, BarStyle.SOLID)

			bossBar.addPlayer(receiver)

			scheduler().schedule(DreamChat.INSTANCE) {
				while (bossBar.progress > 0) {
					val newProgress = bossBar.progress - 0.01
					if (0 >= newProgress) {
						break
					}
					bossBar.progress = newProgress
					waitFor(1)
				}

				bossBar.removeAll()
			}
		}
	}
}
