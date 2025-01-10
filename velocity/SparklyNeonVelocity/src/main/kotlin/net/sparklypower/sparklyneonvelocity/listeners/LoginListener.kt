package net.sparklypower.sparklyneonvelocity.listeners

import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.velocitypowered.api.event.Continuation
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.player.GameProfileRequestEvent
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.proxy.InboundConnection
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.proxy.connection.client.LoginInboundConnection
import com.velocitypowered.proxy.connection.util.VelocityInboundConnection
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.sparklypower.common.utils.DateUtils
import net.sparklypower.common.utils.fromLegacySectionToTextComponent
import net.sparklypower.sparklyneonvelocity.SparklyNeonVelocity
import net.sparklypower.sparklyneonvelocity.dao.Ban
import net.sparklypower.sparklyneonvelocity.dao.GeoLocalization
import net.sparklypower.sparklyneonvelocity.dao.IpBan
import net.sparklypower.sparklyneonvelocity.dao.User
import net.sparklypower.sparklyneonvelocity.tables.*
import net.sparklypower.sparklyneonvelocity.utils.ASNManager
import net.sparklypower.sparklyneonvelocity.utils.DreamNetwork
import net.sparklypower.sparklyneonvelocity.utils.GeoUtils
import net.sparklypower.sparklyneonvelocity.utils.LoginConnectionStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.reflect.Field
import java.util.*
import java.util.logging.Level
import java.util.regex.Pattern
import kotlin.collections.HashMap
import kotlin.jvm.optionals.getOrNull

class LoginListener(val m: SparklyNeonVelocity, val server: ProxyServer) {
    private val isOnlineModeSet = HashMap<String, Boolean>()
    private val nameToPremiumData = HashMap<String, ResultRow>()

    @Subscribe
    fun onPreLogin(event: PreLoginEvent, continuation: Continuation) {
        val isGeyser = m.isGeyser(event.connection)

        val playerName = event.username
        m.logger.info { "User $playerName (IP: ${event.connection.remoteAddress.hostString}) is pre logging in... Is Geyser? $isGeyser" }

        val currentPlayerUsernameAsLowercase = playerName.lowercase()
        val alreadyConnectedToProxy = server.allPlayers.any { it.username.lowercase() == currentPlayerUsernameAsLowercase }
        if (alreadyConnectedToProxy) {
            event.result = PreLoginEvent.PreLoginComponentResult.denied(
                "§cJá há um player com o nome §e${playerName}§c conectado no servidor!".fromLegacySectionToTextComponent()
            )
            continuation.resume()
            return
        }

        val onlinePlayersWithSpecificIp = server.allPlayers.count {
            event.connection.remoteAddress.address.hostAddress == it.remoteAddress.address.hostAddress
        }

        m.logger.info { "Count of players with IP ${event.connection.remoteAddress.address.hostAddress} connected to the server: $onlinePlayersWithSpecificIp" }
        if (onlinePlayersWithSpecificIp >= 5) { // If it is more than five...
            // get dunked!!
            event.result = PreLoginEvent.PreLoginComponentResult.denied(
                "§cJá existem muitas contas logadas com o mesmo IP no servidor!".fromLegacySectionToTextComponent()
            )
            continuation.resume()
            return
        }

        // mcdrop.io
        if (playerName.startsWith("mcdrop_", true)) {
            // get dunked²
            event.result = PreLoginEvent.PreLoginComponentResult.denied(
                "§cJá existem muitas contas logadas com o mesmo IP no servidor!".fromLegacySectionToTextComponent()
            )
            continuation.resume()
            return
        }

        val hasPinged = m.pingedByAddresses.contains(event.connection.remoteAddress.hostString)
        m.logger.info { "Did $playerName (IP: ${event.connection.remoteAddress.hostString}) ping the server list? $hasPinged" }
        // We won't check for Geyser here due to MOTD pings being unreliable
        if (!isGeyser && !m.pingedByAddresses.contains(event.connection.remoteAddress.hostString)) {
            event.result = PreLoginEvent.PreLoginComponentResult.denied(
                "§cAdicione §emc.sparklypower.net§c na sua lista de servidores antes de começar a jogar! ^-^\n\nSim, é muito tosco fazer isso, mas serve como uma verificação ;w;".fromLegacySectionToTextComponent()
            )
            continuation.resume()
            return
        }

        // Don't check if the user is premium if they are using Geyser
        if (m.config.alwaysForceOfflineMode || isGeyser) {
            m.logger.info("Bypassing Premium Check for ${event.connection}")
            event.result = PreLoginEvent.PreLoginComponentResult.forceOfflineMode()
            continuation.resume()
            return
        }

        // TODO: Don't use GlobalScopes
        GlobalScope.launch {
            val playerUniqueIdResult = runBlocking {
                runCatching {
                    m.minecraftMojangApi.getUniqueId(playerName)
                }
            }

            if (playerUniqueIdResult.isFailure) {
                val exception = playerUniqueIdResult.exceptionOrNull()
                // Something went wrong while querying the UUID!
                m.logger.warn(exception) { "Something went wrong while querying ${playerName}'s UUID!" }

                event.result = PreLoginEvent.PreLoginComponentResult.denied(
                    "§cParece que a Mojang está tendo dificuldades no momento, tente mais tarde! :(".fromLegacySectionToTextComponent()
                )
                continuation.resume()
                return@launch
            }

            val playerUniqueId = playerUniqueIdResult.getOrThrow() // Should never throw

            m.logger.info("Player ${event.connection} premium UUID is $playerUniqueId")

            if (playerUniqueId == null) { // Se o UUID for nulo, então é cracked *ou* estamos tomando rate limit
                // But there is a catch! We also need to check if the user matches the cracked UUID!
                val crackedUniqueId = UUID.nameUUIDFromBytes("OfflinePlayer:${playerName}".toByteArray(Charsets.UTF_8))

                val premiumStatus = m.pudding.transaction {
                    PremiumUsers.select {
                        PremiumUsers.crackedUniqueId eq crackedUniqueId
                    }.firstOrNull()
                }

                if (premiumStatus != null) {
                    // Hell no, get out!
                    event.result = PreLoginEvent.PreLoginComponentResult.denied(
                        "§cVocê precisa entrar com a sua conta de Minecraft original!".fromLegacySectionToTextComponent()
                    )
                    continuation.resume()
                    return@launch
                }

                continuation.resume()
                return@launch
            }

            // Parece que o player na Mojang realmente existe!
            // Vamos verificar se é um premium player...
            val premiumStatus = m.pudding.transaction {
                PremiumUsers.select {
                    PremiumUsers.premiumUniqueId eq playerUniqueId
                }.firstOrNull()
            }

            if (premiumStatus != null) {
                m.logger.info("Premium Status is set for ${event.connection}! Online Mode is on!!")
                // Realmente é um player premium, wow!
                // Vamos alterar o player para online mode então! :gesso:
                event.result = PreLoginEvent.PreLoginComponentResult.forceOnlineMode()
                isOnlineModeSet[playerName] = true
                nameToPremiumData[playerName] = premiumStatus
            } else {
                m.logger.info("Premium Status is not set for ${event.connection} so we are going to set it to off...")
            }

            m.logger.info { "Successfully processed PreLoginEvent! Time to continue..." }
            continuation.resume()
        }
    }

    @Subscribe
    fun onGameProfileRequest(event: GameProfileRequestEvent, continuation: Continuation) {
        m.logger.info { "Received a GameProfileRequestEvent" }
        val isGeyser = m.isGeyser(event.connection)
        // Reset to the default offline mode UUID
        event.gameProfile = event.gameProfile.withId(UUID.nameUUIDFromBytes("OfflinePlayer:${event.username}".toByteArray(Charsets.UTF_8)))

        // If the player has spaces in the username, remove them
        // This would only happen on a Geyser connection or a pirated MC connection, but we will only replace if it is Geyser
        if (isGeyser && event.username.contains(" ")) { // Geyser
            val newName = event.username.replace(" ", "_")
            event.gameProfile = event.gameProfile
                .withId(UUID.nameUUIDFromBytes("OfflinePlayer:$newName".toByteArray(Charsets.UTF_8)))
                .withName(newName)
        }

        val premiumData = nameToPremiumData[event.username]
        if (premiumData != null) {
            event.gameProfile = event.gameProfile
                .withId(premiumData[PremiumUsers.crackedUniqueId])

            // We only remove the premium data during the LoginEvent
            // nameToPremiumData.remove(playerName)

            m.logger.info("Player ${event.connection} was successfully logged in as a premium user!")
        }

        m.logger.info { "Successfully processed GameProfileRequestEvent! Time to continue..." }
        continuation.resume()
    }

    @Subscribe
    fun onLogin(event: LoginEvent, continuation: Continuation) {
        val isGeyser = m.isGeyser(event.player)
        val playerName = event.player.username
        val uniqueId = event.player.uniqueId
        val premiumData = nameToPremiumData[playerName]
        // No need to store that anymore, thank you xoxo
        nameToPremiumData.remove(playerName)

        // TODO: Don't use GlobalScopes
        GlobalScope.launch {
            val pattern = Pattern.compile("[a-zA-Z0-9_]{3,16}")
            val matcher = pattern.matcher(playerName)

            if (!matcher.matches()) {
                event.result = ResultedEvent.ComponentResult.denied(
                    """
				§cSeu nickname não atende aos critérios necessários!

				§cProvavelmente:
				§c - Seu nickname contém caracteres especiais (como $, #, @, %, etc)
				§c - Seu nickname contém espaços
				§c - Seu nickname contém mais de 16 caracteres
				§c - Seu nickname contém menos de 3 caracteres
			""".trimIndent().fromLegacySectionToTextComponent()
                )
                addToConnectionLog(event, LoginConnectionStatus.INVALID_NAME)
                continuation.resume()
                return@launch
            }

            // Player logou, então vamos causar:tm:
            if (isOnlineModeSet.containsKey(playerName)) { // O player entrou e está marcado como online mode!
                isOnlineModeSet.remove(playerName)

                if (premiumData != null) {
                    // Time to notify the lobby that we don't need to authenticate the current user because he is a premium user!
                    m.logger.info("Player $playerName is going to be notified as a premium user...")

                    // E avisar para o auth server que é um player premium
                    DreamNetwork.PERFECTDREAMS_LOBBY.send(
                        jsonObject(
                            "type" to "addToPremiumPlayerList",
                            "uniqueId" to premiumData[PremiumUsers.crackedUniqueId].toString()
                        )
                    )
                } else {
                    m.logger.info("Player ${event.player.remoteAddress} logged in as a premium user, but doesn't have premium status...?")

                    event.result = ResultedEvent.ComponentResult.denied(
                        """§cConta logou em Online Mode, mas premium status não existe. (bug?)""".trimMargin().fromLegacySectionToTextComponent()
                    )

                    continuation.resume()
                    return@launch
                }
            } else {
                m.logger.info("Player ${event.player.remoteAddress} logged in as a cracked user!")
            }

            if (isGeyser) {
                m.logger.info { "Notifying ${DreamNetwork.PERFECTDREAMS_SURVIVAL.host}:${DreamNetwork.PERFECTDREAMS_SURVIVAL.socketPort} that ${playerName} is a Geyser player..." }
                DreamNetwork.PERFECTDREAMS_SURVIVAL.send(
                    jsonObject(
                        "type" to "addToGeyserPlayerList",
                        "uniqueId" to uniqueId.toString()
                    )
                )
            }

            // We need to update it on the DreamNetworkBans plugin because BungeeCord does *not* respect event priority.
            // This sucks, but sadly that's what we need to do :(
            m.pudding.transaction {
                val user = User.findById(uniqueId) ?: User.new(uniqueId) {
                    m.logger.info { "Creating new user data for ($playerName / $uniqueId)" }
                    this.username = playerName
                }

                m.logger.info { "Updated (${user.username} / ${user.id}) with ($playerName / $uniqueId) data" }

                user.username = playerName
            }

            val playerIp = event.player.remoteAddress.hostString

            // Check if the ASN is blacklisted
            val result = m.asnManager.isAsnBlacklisted(playerIp)
            m.logger.info { "ASN check result for $playerIp ($playerName): ${result.blocked} (${result.asnId} / ${result.asn?.name}); Quantity of triggered ASNs: ${m.asnManager.triggeredAsns.size}/${m.asnManager.asns.size}" }

            if (result.blocked) {
                event.result = ResultedEvent.ComponentResult.denied(
                    """§cSeu IP está bloqueado, desative VPNs ou proxies ativos para poder jogar!"""".trimMargin().fromLegacySectionToTextComponent()
                )
                continuation.resume()
                return@launch
            }

            val geoLocalization = m.pudding.transaction {
                GeoLocalization.find { GeoLocalizations.ip eq playerIp }.firstOrNull()
            }

            if (geoLocalization == null || System.currentTimeMillis() >= geoLocalization.retrievedAt + (1_000 * 60 * 60 * 24)) {
                // Apenas iremos verificar o GeoLocation se já se passou mais de um dia desde a última verificação ou se a geolocalização é nula
                // Vamos executar isto em uma thread externa, para evitar problemas
                // TODO: Don't use GlobalScopes
                GlobalScope.launch {
                    val loc = GeoUtils.getGeolocalization(playerIp)

                    m.pudding.transaction {
                        GeoLocalization.new {
                            this.ip = playerIp

                            this.country = loc.country
                            this.region = loc.regionName
                            this.retrievedAt = System.currentTimeMillis()
                        }
                    }
                }
            }

            val ban = m.pudding.transaction {
                Ban.find { Bans.player eq event.player.uniqueId }
                    .sortedByDescending { it.punishedAt }
                    .firstOrNull()
            }

            if (ban != null) {
                if (!ban.temporary || ban.expiresAt!! >= System.currentTimeMillis()) {
                    event.result = ResultedEvent.ComponentResult.denied(
                        """
							§cVocê foi ${if (ban.temporary) "temporariamente " else ""}banido!
							§cMotivo:
							
							§a${ban.reason}
							§cPor: ${m.punishmentManager.getPunisherName(ban.punishedBy)}
							${if (ban.temporary) "§c Expira em: §e${DateUtils.formatDateDiff(ban.expiresAt!!)}" else
                            "§3Quer tentar uma segunda chance? Acesse o link, leia e preencha com atenção: §6bit.ly/sparklypower-unban"}
						""".trimIndent().fromLegacySectionToTextComponent()
                    )

                    val message = buildString {
                        appendLine("<:pantufa_megaphone:997669904633299014> **|** **Tentou entrar estando banido!**")
                        appendLine()
                        appendLine("<:pantufa_reading:853048447169986590> **|** `${event.player.username}`/`${playerIp}` (`${event.player.uniqueId}`)")
                    }

                    m.survivalLogInWebhook.send(message)

                    addToConnectionLog(event, LoginConnectionStatus.BANNED)
                    continuation.resume()
                    return@launch
                }
            }

            val ipBan = m.pudding.transaction {
                IpBan.find { IpBans.ip eq event.player.remoteAddress.hostString }
                    .sortedByDescending { it.punishedAt }
                    .firstOrNull()
            }

            // Because MCPE connections via Geyser uses "127.0.0.1" (for now), we will just ignore IP bans if they are bound to "127.0.0.1"
            if (ipBan != null && ipBan.ip != "127.0.0.1") {
                if (!ipBan.temporary || ipBan.expiresAt!! >= System.currentTimeMillis()) {
                    event.result = ResultedEvent.ComponentResult.denied(
                        """
							§cVocê foi ${if (ipBan.temporary) "temporariamente " else ""}banido!
							§cMotivo:
							
							§a${ipBan.reason}
							§cPor: ${m.punishmentManager.getPunisherName(ipBan.punishedBy)}
							${if (ipBan.temporary) "§c Expira em: §e${DateUtils.formatDateDiff(ipBan.expiresAt!!)}" else
                            "§3Quer tentar uma segunda chance? Acesse o link, leia e preencha com atenção: §6bit.ly/sparklypower-unban"}
						""".trimIndent().trimIndent().fromLegacySectionToTextComponent()
                    )

                    val message = buildString {
                        appendLine("<:pantufa_megaphone:997669904633299014> **|** **Tentou entrar estando com o IP banido!**")
                        appendLine()
                        appendLine("<:pantufa_reading:853048447169986590> **|** `${event.player.username}`/`${event.player.remoteAddress.hostString}` (`${event.player.uniqueId}`)")
                    }

                    m.survivalLogInWebhook.send(message)

                    addToConnectionLog(event, LoginConnectionStatus.IP_BANNED)
                    continuation.resume()
                    return@launch
                }
            }

            // THIS IS FOR DEBUGGING/DEV!!
            if (!m.config.requireDreamAuthLogin)
                m.loggedInPlayers.add(event.player.uniqueId)

            addToConnectionLog(event, LoginConnectionStatus.OK)

            m.logger.info { "Successfully processed LoginEvent for $playerName! Time to continue..." }
            continuation.resume()
        }
    }

    @Subscribe
    fun onDisconnect(event: KickedFromServerEvent) {
        if (!event.kickedDuringServerConnect() && event.server.serverInfo.name != "sparklypower_lobby") {
            event.result = KickedFromServerEvent.RedirectPlayer.create(server.getServer("sparklypower_lobby").get())
        }
    }

    @Subscribe
    fun onQuit(event: DisconnectEvent) {
        DreamNetwork.PERFECTDREAMS_LOBBY.send(
            jsonObject(
                "type" to "removeFromPremiumPlayerList",
                "uniqueId" to event.player.uniqueId.toString()
            )
        )

        DreamNetwork.PERFECTDREAMS_SURVIVAL.send(
            jsonObject(
                "type" to "removeFromGeyserPlayerList",
                "uniqueId" to event.player.uniqueId.toString()
            )
        )
    }

    private suspend fun addToConnectionLog(event: LoginEvent, state: LoginConnectionStatus) {
        m.pudding.transaction {
            ConnectionLogEntries.insert {
                it[player] = event.player.uniqueId
                it[ip] = event.player.remoteAddress.hostString
                it[connectedAt] = System.currentTimeMillis()
                it[connectionStatus] = state
            }
        }
    }
}