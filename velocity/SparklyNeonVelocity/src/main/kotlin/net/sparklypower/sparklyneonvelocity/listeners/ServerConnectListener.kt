package net.sparklypower.sparklyneonvelocity.listeners

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.sparklypower.common.utils.adventure.TextComponent
import net.sparklypower.common.utils.adventure.appendCommand
import net.sparklypower.sparklyneonvelocity.SparklyNeonVelocity
import net.sparklypower.sparklyneonvelocity.dao.ConnectionLogEntry
import net.sparklypower.sparklyneonvelocity.dao.User
import net.sparklypower.sparklyneonvelocity.tables.Bans
import net.sparklypower.sparklyneonvelocity.tables.ConnectionLogEntries
import net.sparklypower.sparklyneonvelocity.tables.DiscordAccounts
import net.sparklypower.sparklyneonvelocity.tables.WhitelistedIps
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

class ServerConnectListener(val m: SparklyNeonVelocity) {
    @Subscribe
    fun onServerConnect(e: ServerPreConnectEvent) {
        // If the user is trying to connect to a server that isn't "sparklypower_lobby" and they aren't logged in, cancel the event!
        if (e.originalServer.serverInfo.name != "sparklypower_lobby" && !m.loggedInPlayers.contains(e.player.uniqueId)) {
            e.result = ServerPreConnectEvent.ServerResult.denied()
        }

        val server = e.result.server ?: return
        val destinationServer = server.get()
        val player = e.player

        when (destinationServer.serverInfo.name) {
            "sparklypower_survival" -> {
                val playerIp = player.remoteAddress.hostString

                val isIpWhitelisted = m.pudding.transactionBlocking {
                    WhitelistedIps.selectAll()
                        .where { WhitelistedIps.ip eq playerIp }
                        .count() > 0
                }

                if (!isIpWhitelisted) {
                    val playerLogEntries = m.pudding.transactionBlocking {
                        ConnectionLogEntry.find { ConnectionLogEntries.ip eq playerIp and (ConnectionLogEntries.player neq player.uniqueId) }
                            .map { it.player }
                            .distinct()
                            .mapNotNull { User.findById(it) }
                            .toList()
                    }

                    if (playerLogEntries.isNotEmpty()) {
                        // get all banned accounts in the same ip, but don't repeat any username
                        val suspectAccounts = m.pudding.transactionBlocking {
                            Bans.selectAll().where {
                                Bans.player inList playerLogEntries.map { it.id.value }
                            }.map { it[Bans.player] }
                                .mapNotNull { User.findById(it) }
                                .map { it.username }
                                .distinct()
                        }

                        if (suspectAccounts.isNotEmpty()) {
                            val connectedDiscordAccount = m.pudding.transactionBlocking {
                                DiscordAccounts.selectAll().where {
                                    DiscordAccounts.minecraftId eq player.uniqueId and (DiscordAccounts.isConnected eq true)
                                }.firstOrNull()
                            }

                            val message = buildString {
                                appendLine("<:pantufa_megaphone:997669904633299014> **|** **Uma conta suspeita entrou no servidor!** \uD83D\uDEA8")
                                appendLine()
                                appendLine("<:pantufa_reading:853048447169986590> **|** **Conta suspeita:**`${player.username}`/`${playerIp}` (`${player.uniqueId}`)")
                                appendLine("<:pantufa_analise:853048446813470762> **|** **Contas banidas:** ${suspectAccounts.joinToString(", ") { "`$it`" }}")
                                if (connectedDiscordAccount != null) {
                                    appendLine("<:pantufa_coffee:853048446981111828> **|** A conta foi **permitida**, pois a pessoa tem uma conta no Discord conectada: <@${connectedDiscordAccount[DiscordAccounts.discordId]}> (${connectedDiscordAccount[DiscordAccounts.discordId]})")
                                } else {
                                    appendLine("<:pantufa_bonk:1028160322990776331> **|** A conta foi **bloqueada**, pois a pessoa não tem uma conta no Discord conectada!")
                                }
                                appendLine()
                                appendLine("~~ㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤㅤ~~")
                            }

                            m.survivalLogInWebhook.send(message)

                            if (connectedDiscordAccount == null) {
                                e.player.sendMessage(
                                    TextComponent {
                                        color(NamedTextColor.RED)
                                        content("Você precisa conectar a sua conta do Discord com a sua conta do SparklyPower antes de poder entrar no SparklyPower Survival! Para entrar no nosso servidor no Discord, use ")
                                        appendCommand("/discord")
                                    }
                                )
                                e.result = ServerPreConnectEvent.ServerResult.denied()
                            }
                        }
                    }
                }
            }
        }
    }
}