package net.sparklypower.sparklyneonvelocity.commands

import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.event.HoverEvent
import net.sparklypower.common.utils.fromLegacySectionToTextComponent
import net.sparklypower.sparklyneonvelocity.SparklyNeonVelocity
import net.sparklypower.sparklyneonvelocity.dao.ConnectionLogEntry
import net.sparklypower.sparklyneonvelocity.dao.User
import net.sparklypower.sparklyneonvelocity.tables.ConnectionLogEntries
import net.sparklypower.sparklyneonvelocity.utils.DreamUtils
import net.sparklypower.sparklyvelocitycore.utils.commands.context.CommandArguments
import net.sparklypower.sparklyvelocitycore.utils.commands.context.CommandContext
import net.sparklypower.sparklyvelocitycore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.sparklypower.sparklyvelocitycore.utils.commands.declarations.sparklyCommand
import net.sparklypower.sparklyvelocitycore.utils.commands.executors.SparklyCommandExecutor
import net.sparklypower.sparklyvelocitycore.utils.commands.options.CommandOptions
import net.sparklypower.sparklyvelocitycore.utils.commands.options.buildSuggestionsBlockFromList
import java.time.Instant
import java.util.*

class AdvancedDupeIpCommand(val m: SparklyNeonVelocity, val server: ProxyServer) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("advdupeip", "advanceddupeip", "advancedupeip")) {
        permission = "sparklyneonvelocity.advanceddupeip"

        executor = AdvancedDupeIpExecutor(m, server)
    }

    class AdvancedDupeIpExecutor(val m: SparklyNeonVelocity, val server: ProxyServer) : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val playerName = word(
                "player_name",
                buildSuggestionsBlockFromList {
                    server.allPlayers.map { it.username }
                }
            )
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val playerName = args[options.playerName]

            // Primeiramente vamos pegar o UUID para achar o IP
            val playerUniqueId = try {
                UUID.fromString(playerName)
            } catch (e: IllegalArgumentException) {
                m.punishmentManager.getUniqueId(playerName)
            }

            val lookUpIp = playerName.contains(".")

            // Agora vamos achar todos os players que tem o mesmo IP ou todos os IPs que o player utilizou
            val connections = m.pudding.transactionBlocking {
                if (lookUpIp) {
                    ConnectionLogEntry.find { ConnectionLogEntries.ip eq playerName }.sortedBy { it.connectedAt }.toList()
                } else {
                    val tempConnections = ConnectionLogEntry.find { ConnectionLogEntries.player eq playerUniqueId }

                    // Mas se estamos procurando pelo PLAYER, queremos saber das alts dele!
                    // Para isso, vamos pegar todas as conexões de cada IP que o usuário já usou!
                    ConnectionLogEntry.find { ConnectionLogEntries.ip inList tempConnections.map { it.ip }.distinct() }.sortedBy { it.connectedAt }.toList()
                }
            }

            if (connections.isEmpty()) {
                if (lookUpIp) {
                    context.sendMessage("§cO IP $playerName nunca jogou no servidor!".fromLegacySectionToTextComponent())
                } else {
                    context.sendMessage("§cO player $playerName nunca jogou no servidor!".fromLegacySectionToTextComponent())
                }
                return
            }

            // Caso achar...
            context.sendMessage("§7Escaneando §b$playerName".fromLegacySectionToTextComponent())

            var currentIp = ""

            val retrievedNames = mutableMapOf<UUID, String>()

            for (connection in connections) {
                if (currentIp != connection.ip) {
                    currentIp = connection.ip
                    context.sendMessage("§7Lista de jogadores que utilizaram §b$currentIp§7...".fromLegacySectionToTextComponent())
                }

                val instant = Instant.ofEpochMilli(connection.connectedAt)
                val instantAtZone = instant.atZone(DreamUtils.serverZoneId)
                val hour = instantAtZone.hour.toString().padStart(2, '0')
                val minute = instantAtZone.minute.toString().padStart(2, '0')
                val second = instantAtZone.second.toString().padStart(2, '0')

                val day = instantAtZone.dayOfMonth.toString().padStart(2, '0')
                val month = instantAtZone.monthValue.toString().padStart(2, '0')
                val year = instantAtZone.year

                val playerNameFromUniqueId = retrievedNames.getOrPut(connection.player) {
                    m.pudding.transactionBlocking { User.findById(connection.player) }?.username
                        ?: connection.player.toString()
                }

                context.sendMessage(
                    "§8• ${connection.connectionStatus.color}${playerNameFromUniqueId} §7às §f$hour:$minute:$second $day/$month/$year".fromLegacySectionToTextComponent()
                        .run {
                            hoverEvent(HoverEvent.showText("§eStatus: §6${connection.connectionStatus.color}${connection.connectionStatus.fancyName}\n§eUUID: §6${connection.player}\n§7Tentou se conectar às $hour:$minute:$second $day/$month/$year".fromLegacySectionToTextComponent()))
                        }
                )
            }
        }
    }
}