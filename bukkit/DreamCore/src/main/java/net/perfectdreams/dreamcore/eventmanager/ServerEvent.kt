package net.perfectdreams.dreamcore.eventmanager

import com.github.salomonbrys.kotson.jsonObject
import com.okkero.skedule.schedule
import net.md_5.bungee.api.chat.BaseComponent
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.network.DreamNetwork
import net.perfectdreams.dreamcore.utils.preferences.BroadcastType
import net.perfectdreams.dreamcore.utils.preferences.broadcastMessage
import net.perfectdreams.dreamcore.utils.scheduler
import org.bukkit.Bukkit

open class ServerEvent(val eventName: String, val prefix: String) {
	var lastTime: Long = System.currentTimeMillis()
	var running = false
	var countingDown = false
	var command = "/loritta"
	open var delayBetween: Long = 0L
	var requiredPlayers: Int = 0
	var discordAnnouncementRole: Long? = null
	var discordChannelId: String? = null

	open fun preStart() {
		countdown()
	}

	/**
	 * Broadcasts the event in the [discordChannelId] (or [DreamCore.dreamConfig.discord.eventAnnouncementChannelId] if [discordChannelId] is not set)
	 *
	 * Also only broadcasts if [discordAnnouncementRole] is not null.
	 */
	open fun broadcastEventAnnouncement() {
		val broadcastEventInChannelId = discordChannelId ?: DreamCore.dreamConfig.discord?.eventAnnouncementChannelId

		if (discordAnnouncementRole != null && broadcastEventInChannelId != null) {
			DreamNetwork.PANTUFA.sendAsync(
				jsonObject(
					"type" to "sendEventStart",
					"eventName" to eventName,
					"roleId" to discordAnnouncementRole,
					"channelId" to broadcastEventInChannelId
				)
			)
		}
	}

	open fun countdown() {
		scheduler().schedule(DreamCore.INSTANCE) {
			broadcastEventAnnouncement()

			for (i in 60 downTo 1) {
				val announce = (i in 15..60 && i % 15 == 0) || (i in 0..14 && i % 5 == 0)

				if (announce) {
					val announcement = getWarmUpAnnouncementMessage(i)
					when (announcement) {
						is BaseComponent -> broadcastMessage(BroadcastType.EVENT_ANNOUNCEMENT, announcement)
						is String -> broadcastMessage(BroadcastType.EVENT_ANNOUNCEMENT) { "$announcement" }
						else -> throw RuntimeException("Warm up announcement message is ${announcement::class.java.simpleName}, not a String or BaseComponent!")
					}
				}
				waitFor(20)
			}
			start()
		}
	}

	open fun start() {}

	open fun getWarmUpAnnouncementMessage(idx: Int): Any {
		return "${prefix} §eEvento ${eventName} irá iniciar em $idx segundos! §6$command"
	}

	open fun startNow(): Boolean {
		val diff = System.currentTimeMillis() - (lastTime + delayBetween)

		return diff >= 0 && Bukkit.getOnlinePlayers().size >= requiredPlayers
	}
}