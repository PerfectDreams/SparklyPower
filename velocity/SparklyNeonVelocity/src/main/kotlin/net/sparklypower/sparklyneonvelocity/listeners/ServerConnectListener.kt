package net.sparklypower.sparklyneonvelocity.listeners

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import net.sparklypower.sparklyneonvelocity.SparklyNeonVelocity
import net.sparklypower.sparklyneonvelocity.dao.ConnectionLogEntry
import net.sparklypower.sparklyneonvelocity.dao.User
import net.sparklypower.sparklyneonvelocity.tables.Bans
import net.sparklypower.sparklyneonvelocity.tables.ConnectionLogEntries
import net.sparklypower.sparklyneonvelocity.tables.IpBans
import net.sparklypower.sparklyneonvelocity.tables.WhitelistedIps
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

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
                    val suspectAccounts = m.pudding.transactionBlocking {
                        ConnectionLogEntry.find { ConnectionLogEntries.ip eq playerIp and (ConnectionLogEntries.player neq player.uniqueId) }
                            .map { it.player }
                            .distinct()
                            .mapNotNull { User.findById(it) }
                            .toList()
                    }

                    if (suspectAccounts.isNotEmpty()) {
                        // get all banned accounts in the same ip, but don't repeat any username
                        val accounts = m.pudding.transactionBlocking {
                            Bans.selectAll().where {
                                Bans.player inList suspectAccounts.map { it.id.value }
                            }.map { it[Bans.player] }
                                .mapNotNull { User.findById(it) }
                                .map { it.username }
                                .distinct()
                        }

                        val message = buildString {
                            appendLine("<:pantufa_megaphone:997669904633299014> **|** **Uma conta suspeita entrou no servidor!** \uD83D\uDEA8")
                            appendLine()
                            appendLine("<:pantufa_reading:853048447169986590> **|** **Conta suspeita:**`${player.username}`/`${playerIp}` (`${player.uniqueId}`)")
                            appendLine("<:pantufa_analise:853048446813470762> **|** **Contas banidas:** ${accounts.joinToString(", ") { "`$it`" }}")
                        }

                        m.survivalLogInWebhook.send(message)
                    }
                }
            }
        }
    }
}