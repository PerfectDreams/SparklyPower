package net.sparklypower.sparklyneonvelocity.commands

import com.velocitypowered.api.proxy.ProxyServer
import net.sparklypower.common.utils.fromLegacySectionToTextComponent
import net.sparklypower.sparklyneonvelocity.SparklyNeonVelocity
import net.sparklypower.sparklyneonvelocity.tables.WhitelistedIps
import net.sparklypower.sparklyvelocitycore.utils.commands.context.CommandArguments
import net.sparklypower.sparklyvelocitycore.utils.commands.context.CommandContext
import net.sparklypower.sparklyvelocitycore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.sparklypower.sparklyvelocitycore.utils.commands.declarations.sparklyCommand
import net.sparklypower.sparklyvelocitycore.utils.commands.executors.SparklyCommandExecutor
import net.sparklypower.sparklyvelocitycore.utils.commands.options.CommandOptions
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class WhitelistIpCommand(private val m: SparklyNeonVelocity, private val server: ProxyServer) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("whitelistip")) {
        permission = "sparklyneonvelocity.whitelistip"

        subcommand(listOf("add", "adicionar")) {
            executor = WhitelistIpAddCommandExecutor()
        }

        subcommand(listOf("remove", "remover")) {
            executor = WhitelistIpRemoveCommandExecutor()
        }
    }

    inner class WhitelistIpAddCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val ip = word("ip")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val ip = args[options.ip]

            val thisIpIsAlreadyWhitelisted = m.pudding.transactionBlocking {
                WhitelistedIps.selectAll()
                    .where { WhitelistedIps.ip eq ip }
                    .count() > 0
            }

            if (thisIpIsAlreadyWhitelisted) {
                context.sendMessage("§cEste IP já está na whitelist.".fromLegacySectionToTextComponent())
                return
            }

            m.pudding.transactionBlocking {
                WhitelistedIps.insert {
                    it[WhitelistedIps.ip] = ip
                }
            }

            context.sendMessage("§aIP $ip adicionado à whitelist.".fromLegacySectionToTextComponent())
        }
    }

    inner class WhitelistIpRemoveCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val ip = word("ip") { context, builder ->
                val ipList = m.pudding.transactionBlocking {
                    WhitelistedIps.selectAll()
                        .map { it[WhitelistedIps.ip] }
                }

                ipList.forEach {
                    builder.suggest(it)
                }
            }
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val ip = args[options.ip]

            val isThisIpWhitelisted = m.pudding.transactionBlocking {
                WhitelistedIps.selectAll()
                    .where { WhitelistedIps.ip eq ip }
                    .count() != 0L
            }

            if (!isThisIpWhitelisted) {
                context.sendMessage("§cEste IP não está na whitelist.".fromLegacySectionToTextComponent())
                return
            }

            m.pudding.transactionBlocking {
                WhitelistedIps.deleteWhere { WhitelistedIps.ip eq ip }
            }

            context.sendMessage("§aIP $ip removido da whitelist.".fromLegacySectionToTextComponent())
        }
    }
}