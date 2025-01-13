package net.sparklypower.sparklyneonvelocity.commands

import com.velocitypowered.api.proxy.ProxyServer
import net.sparklypower.common.utils.fromLegacySectionToTextComponent
import net.sparklypower.sparklyneonvelocity.PunishmentManager
import net.sparklypower.sparklyneonvelocity.SparklyNeonVelocity
import net.sparklypower.sparklyneonvelocity.dao.Ban
import net.sparklypower.sparklyneonvelocity.dao.Warn
import net.sparklypower.sparklyneonvelocity.tables.Bans
import net.sparklypower.sparklyneonvelocity.tables.Warns
import net.sparklypower.sparklyneonvelocity.utils.DreamUtils
import net.sparklypower.sparklyneonvelocity.utils.prettyBoolean
import net.sparklypower.sparklyvelocitycore.utils.commands.context.CommandArguments
import net.sparklypower.sparklyvelocitycore.utils.commands.context.CommandContext
import net.sparklypower.sparklyvelocitycore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.sparklypower.sparklyvelocitycore.utils.commands.declarations.sparklyCommand
import net.sparklypower.sparklyvelocitycore.utils.commands.executors.SparklyCommandExecutor
import net.sparklypower.sparklyvelocitycore.utils.commands.options.CommandOptions
import net.sparklypower.sparklyvelocitycore.utils.commands.options.buildSuggestionsBlockFromList
import java.time.Instant
import java.util.*

class CheckBanCommand(private val m: SparklyNeonVelocity, private val server: ProxyServer) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("checkban")) {
        permission = "sparklyneonvelocity.checkban"

        executor = CheckBanExecutor(m, server)
    }
    
    class CheckBanExecutor(private val m: SparklyNeonVelocity, private val server: ProxyServer) : SparklyCommandExecutor() {
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

            val punishedUniqueId = try { UUID.fromString(playerName) } catch (e: IllegalArgumentException) { m.punishmentManager.getUniqueId(playerName) }

            context.sendMessage("§eSobre §b$playerName§e...".fromLegacySectionToTextComponent())

            m.pudding.transactionBlocking {
                val allBans = Ban.find {
                    Bans.player eq punishedUniqueId
                }.sortedByDescending {
                    it.punishedAt
                }

                val currentlyActiveBan = allBans.firstOrNull {
                    if (it.temporary) it.expiresAt!! > System.currentTimeMillis() else true
                }

                // Estamos fazendo isto dentro de uma transaction!!
                // É bom? Não... mas fazer o que né
                context.sendMessage("§eBanido? ${(currentlyActiveBan != null).prettyBoolean()}".fromLegacySectionToTextComponent())
                if (currentlyActiveBan != null) {
                    context.sendMessage("§eMotivo do Ban: ${currentlyActiveBan.reason}".fromLegacySectionToTextComponent())
                    context.sendMessage("§eQuem baniu? §b${m.punishmentManager.getPunisherName(currentlyActiveBan.punishedBy)}".fromLegacySectionToTextComponent())
                    context.sendMessage("§eTemporário? §b${(currentlyActiveBan.temporary).prettyBoolean()}".fromLegacySectionToTextComponent())
                }

                if (allBans.isNotEmpty()) {
                    context.sendMessage("§eBans anteriores:".fromLegacySectionToTextComponent())
                    allBans.forEach {
                        val instant = Instant.ofEpochMilli(it.punishedAt)
                            .atZone(DreamUtils.serverZoneId)
                            .toOffsetDateTime()

                        val day = instant.dayOfMonth.toString().padStart(2, '0')
                        val month = instant.monthValue.toString().padStart(2, '0')
                        val year = instant.year.toString()

                        val hour = instant.hour.toString().padStart(2, '0')
                        val minute = instant.minute.toString().padStart(2, '0')

                        context.sendMessage("§f[$day/$month/$year $hour:$minute] §7${it.reason} por ${m.punishmentManager.getPunisherName(it.punishedBy)}".fromLegacySectionToTextComponent())
                    }
                }

                val warns = Warn.find { Warns.player eq punishedUniqueId }.toMutableList()
                val validWarns = warns.filter { System.currentTimeMillis() <= PunishmentManager.WARN_EXPIRATION + it.punishedAt }.sortedBy { it.punishedAt }
                val invalidWarns = warns.filter { PunishmentManager.WARN_EXPIRATION + it.punishedAt <= System.currentTimeMillis() }.sortedBy { it.punishedAt }
                context.sendMessage("§eNúmero de avisos (${validWarns.size} avisos válidos):".fromLegacySectionToTextComponent())
                for (invalidWarn in invalidWarns) {
                    val instant = Instant.ofEpochMilli(invalidWarn.punishedAt)
                        .atZone(DreamUtils.serverZoneId)
                        .toOffsetDateTime()

                    val day = instant.dayOfMonth.toString().padStart(2, '0')
                    val month = instant.monthValue.toString().padStart(2, '0')
                    val year = instant.year.toString()

                    val hour = instant.hour.toString().padStart(2, '0')
                    val minute = instant.minute.toString().padStart(2, '0')

                    context.sendMessage("§f[$day/$month/$year $hour:$minute] §7${invalidWarn.reason} por ${m.punishmentManager.getPunisherName(invalidWarn.punishedBy)}".fromLegacySectionToTextComponent())
                }
                for (validWarn in validWarns) {
                    val instant = Instant.ofEpochMilli(validWarn.punishedAt)
                        .atZone(DreamUtils.serverZoneId)
                        .toOffsetDateTime()

                    val day = instant.dayOfMonth.toString().padStart(2, '0')
                    val month = instant.monthValue.toString().padStart(2, '0')
                    val year = instant.year.toString()

                    val hour = instant.hour.toString().padStart(2, '0')
                    val minute = instant.minute.toString().padStart(2, '0')

                    context.sendMessage("§f[$day/$month/$year $hour:$minute]  §a${validWarn.reason} por ${m.punishmentManager.getPunisherName(validWarn.punishedBy)}".fromLegacySectionToTextComponent())
                }
            }
        }
    }
}