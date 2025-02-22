package net.perfectdreams.pantufa.threads

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.double
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.string
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.perfectdreams.pantufa.PantufaBot
import net.perfectdreams.pantufa.network.Databases
import net.perfectdreams.pantufa.tables.NotifyPlayersOnline
import net.perfectdreams.pantufa.utils.Constants
import net.perfectdreams.pantufa.utils.Server
import net.sparklypower.rpc.proxy.ProxyGetProxyOnlinePlayersRequest
import net.sparklypower.rpc.proxy.ProxyGetProxyOnlinePlayersResponse
import net.sparklypower.rpc.proxy.ProxyRPCResponse
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class UpdatePantufaDiscordActivityTask(val m: PantufaBot, val jda: JDA) : Runnable {
	companion object {
		private val logger = KotlinLogging.logger {}
	}

	var previousPlayers: List<ProxyGetProxyOnlinePlayersResponse.Success.ProxyPlayer>? = null

	override fun run() {
		try {
			val getProxyOnlinePlayersResponse = runBlocking { m.proxyRPC.makeRPCRequest<ProxyGetProxyOnlinePlayersResponse>(ProxyGetProxyOnlinePlayersRequest) }

			when (getProxyOnlinePlayersResponse) {
				is ProxyGetProxyOnlinePlayersResponse.Success -> {
					val players = getProxyOnlinePlayersResponse.players
					val proxyPlayerCount = players.size
					val plural = if (players.size == 1) "" else "s"
					logger.info { "SparklyPower Player Count: $proxyPlayerCount" }

					val oldPreviousPlayers = this.previousPlayers
					val oldPreviousPlayersUniqueIds = oldPreviousPlayers?.map { it.uniqueId }

					val currentPlayers = players
					if (oldPreviousPlayersUniqueIds != null) {
						val joinedPlayers = currentPlayers.filter { it.uniqueId !in oldPreviousPlayersUniqueIds }
						logger.info { "Newly joined players: $joinedPlayers" }

						val currentPlayersUniqueId = currentPlayers.map { it.uniqueId }
						for (joinedPlayer in joinedPlayers) {
							val uniqueId = joinedPlayer.uniqueId

							val trackedEntries = transaction(Databases.sparklyPower) {
								NotifyPlayersOnline.selectAll().where { NotifyPlayersOnline.tracked eq uniqueId }
									.toList()
							}

							logger.info { "Users tracking ${joinedPlayer} ($uniqueId): $trackedEntries" }

							for (trackedEntry in trackedEntries) {
								val minecraftUser = m.getMinecraftUserFromUniqueId(trackedEntry[NotifyPlayersOnline.player])

								if (minecraftUser == null) {
									logger.info { "There is a $trackedEntry, but there isn't a minecraft user!" }
									continue
								}

								if (minecraftUser.id.value in currentPlayersUniqueId) {
									logger.info { "There is a $trackedEntry, but the tracking player is already online!" }
									continue
								}

								val account = m.getDiscordAccountFromUniqueId(minecraftUser.id.value)

								if (account == null) {
									logger.info { "There is a $trackedEntry, but there isn't a Discord account!" }
									continue
								}

								val user = jda.getUserById(account.discordId)

								if (user == null) {
									logger.info { "There is a $trackedEntry, but I wasn't able to find the user!" }
									continue
								}

								logger.info { "Opening a DM with ${user.idLong} to say that $joinedPlayer has joined the server..." }
								user.openPrivateChannel().queue {
									it.sendMessageEmbeds(
										EmbedBuilder()
											.setTitle("<a:lori_pat:706263175892566097> Seu amigx está online no SparklyPower!")
											.setDescription("Seu amigx `${joinedPlayer.name}` acabou de entrar no SparklyPower! Que tal entrar para fazer companhia para elx?")
											.setColor(Constants.LORITTA_AQUA)
											.setThumbnail("https://sparklypower.net/api/v1/render/avatar?name=${joinedPlayer.name}&scale=16")
											.setTimestamp(Instant.now())
											.build()
									).queue()
								}
							}
						}
					}

					this.previousPlayers = players

					val prefix = when (proxyPlayerCount) {
						in 45 until 50 -> "\uD83D\uDE18"
						in 40 until 45 -> "\uD83D\uDE0E"
						in 35 until 40 -> "\uD83D\uDE06"
						in 30 until 35 -> "\uD83D\uDE04"
						in 25 until 30 -> "\uD83D\uDE03"
						in 20 until 25 -> "\uD83D\uDE0B"
						in 15 until 20 -> "\uD83D\uDE09"
						in 10 until 15 -> "\uD83D\uDE43"
						in 5 until 10 -> "\uD83D\uDE0A"
						in 1 until 5 -> "\uD83D\uDE42"
						0 -> "\uD83D\uDE34"
						else -> "\uD83D\uDE0D"
					}

					val payload = Server.PERFECTDREAMS_SURVIVAL.send(
						jsonObject(
							"type" to "getTps"
						)
					)

					println(payload)

					val tps = payload["tps"].array
					val currentTps = tps[0].double

					val status = if (currentTps > 19.2) {
						OnlineStatus.ONLINE
					} else if (currentTps > 17.4) {
						OnlineStatus.IDLE
					} else {
						OnlineStatus.DO_NOT_DISTURB
					}

					jda.presence.setPresence(
						status,
						Activity.customStatus(
							"$prefix $proxyPlayerCount player$plural online no SparklyPower! | \uD83C\uDFAE mc.sparklypower.net | TPS: ${
								"%.2f".format(
									currentTps
								)
							}"
						)
					)
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()

			jda.presence.activity = Activity.customStatus("\uD83D\uDEAB SparklyPower está offline \uD83D\uDE2D | \uD83C\uDFAE mc.sparklypower.net")
		}
	}
}