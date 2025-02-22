package net.perfectdreams.pantufa.utils

import com.github.salomonbrys.kotson.jsonObject
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.perfectdreams.pantufa.PantufaBot
import net.perfectdreams.pantufa.pantufa
import net.perfectdreams.pantufa.rpc.*
import net.perfectdreams.pantufa.tables.Users
import net.perfectdreams.pantufa.utils.extensions.await
import net.sparklypower.rpc.proxy.ProxyExecuteCommandRequest
import net.sparklypower.rpc.proxy.ProxyExecuteCommandResponse

class APIServer(private val m: PantufaBot) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        logger.info { "Starting HTTP Server..." }
        val server = embeddedServer(Netty, port = m.config.rpcPort) {
            routing {
                get("/") {
                    call.respondText("SparklyPower API Web Server")
                }

                get("/guilds/{guildId}/cached-members") {
                    val guild = m.jda.getGuildById(call.parameters.getOrFail("guildId").toLong())!!
                    call.respondText(
                        buildString {
                            for (member in guild.members) {
                                appendLine("${member.idLong} (${member.user.name})")
                            }
                        }
                    )
                }

                post("/rpc") {
                    val jsonPayload = call.receiveText()
                    logger.info { "${call.request.userAgent()} sent a RPC request: $jsonPayload" }
                    val response = when (val request = Json.decodeFromString<PantufaRPCRequest>(jsonPayload)) {
                        is GetDiscordUserRequest -> {
                            val user = try { m.jda.retrieveUserById(request.userId).await() } catch (e: ErrorResponseException) { null }

                            if (user != null) {
                                GetDiscordUserResponse.Success(
                                    user.idLong,
                                    user.name,
                                    user.discriminator,
                                    user.avatarId,
                                    user.isBot,
                                    user.isSystem,
                                    user.flagsRaw
                                )
                            } else {
                                GetDiscordUserResponse.NotFound
                            }
                        }

                        is BanSparklyPowerPlayerLorittaBannedRequest -> {
                            val userId = request.userId
                            logger.info { "Received Loritta Ban for $userId!" }

                            val discordAccount = m.retrieveDiscordAccountFromUser(userId)

                            suspend fun getResponse(): BanSparklyPowerPlayerLorittaBannedResponse {
                                if (discordAccount != null && discordAccount.isConnected) {
                                    val userInfo = pantufa.getMinecraftUserFromUniqueId(discordAccount.minecraftId)

                                    if (userInfo != null) {
                                        logger.info { "Banning ${discordAccount.minecraftId} because their Discord account ${discordAccount.discordId} is banned" }

                                        m.proxyRPC.makeRPCRequest<ProxyExecuteCommandResponse>(
                                            ProxyExecuteCommandRequest(
                                                null,
                                                "ban ${userInfo.username} Banido da Loritta | ID da Conta no Discord: ${discordAccount.discordId} - ${request.reason}"
                                            )
                                        )

                                        return BanSparklyPowerPlayerLorittaBannedResponse.Success(
                                            userInfo.id.value.toString(),
                                            userInfo.username
                                        )
                                    } else {
                                        logger.info { "Ignoring Loritta Ban notification because the user $userId doesn't have an associated user info data... Minecraft ID: ${discordAccount.minecraftId}" }
                                    }
                                } else {
                                    logger.info { "Ignoring Loritta Ban notification because the user $userId didn't connect an account..." }
                                }
                                return BanSparklyPowerPlayerLorittaBannedResponse.NotFound
                            }
                            getResponse()
                        }
                    }

                    call.respondText(
                        Json.encodeToString<PantufaRPCResponse>(response),
                        ContentType.Application.Json
                    )
                }
            }
        }

        // If set to "wait = true", the server hangs
        this.server = server.start(wait = false)
        logger.info { "Successfully started HTTP Server!" }
    }
}