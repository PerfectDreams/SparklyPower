package net.sparklypower.sparklyneonvelocity.commands

import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.sparklypower.common.utils.adventure.TextComponent
import net.sparklypower.common.utils.adventure.appendCommand
import net.sparklypower.common.utils.adventure.appendTextComponent
import net.sparklypower.common.utils.fromLegacySectionToTextComponent
import net.sparklypower.sparklyneonvelocity.SparklyNeonVelocity
import net.sparklypower.sparklyneonvelocity.dao.DiscordAccount
import net.sparklypower.sparklyneonvelocity.tables.DiscordAccounts
import net.sparklypower.sparklyvelocitycore.utils.commands.context.CommandArguments
import net.sparklypower.sparklyvelocitycore.utils.commands.context.CommandContext
import net.sparklypower.sparklyvelocitycore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.sparklypower.sparklyvelocitycore.utils.commands.declarations.sparklyCommand
import net.sparklypower.sparklyvelocitycore.utils.commands.executors.SparklyCommandExecutor
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere

class DiscordCommand(private val m: SparklyNeonVelocity) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("discord")) {
        executor = DiscordExecutor(m)

        subcommand(listOf("register", "registrar")) {
            executor = DiscordRegisterExecutor(m)
        }

        subcommand(listOf("unregister", "desregistrar")) {
            executor = DiscordUnregisterExecutor(m)
        }
    }

    class DiscordRegisterExecutor(private val m: SparklyNeonVelocity) : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            val account = m.pudding.transactionBlocking {
                DiscordAccount.find { DiscordAccounts.minecraftId eq player.uniqueId }
                    .firstOrNull()
            }

            if (account == null) {
                context.sendMessage("§cVocê não tem nenhum registro pendente! Use \"-registrar ${player.username}\" no nosso servidor no Discord para registrar a sua conta!".fromLegacySectionToTextComponent())
                return
            }

            m.pudding.transactionBlocking {
                account.isConnected = true

                DiscordAccounts.deleteWhere {
                    DiscordAccounts.minecraftId eq player.uniqueId and (DiscordAccounts.id neq account.id)
                }
            }

            player.sendMessage("§aConta do Discord foi registrada com sucesso, yay!".fromLegacySectionToTextComponent())

            m.discordAccountAssociationsWebhook.send("Conta **`${player.username}`** (`${player.uniqueId}`) foi associada a conta `${account.discordId}` (<@${account.discordId}>)")
            return
        }
    }

    class DiscordUnregisterExecutor(private val m: SparklyNeonVelocity) : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            val account = m.pudding.transactionBlocking {
                DiscordAccount.find { DiscordAccounts.minecraftId eq player.uniqueId and (DiscordAccounts.isConnected eq true) }
                    .firstOrNull()
            }

            if (account == null) {
                player.sendMessage("§cVocê não tem nenhum registro! Use \"-registrar ${player.username}\" no nosso servidor no Discord para registrar a sua conta!".fromLegacySectionToTextComponent())
                return
            }

            m.pudding.transactionBlocking {
                account.delete()
            }

            player.sendMessage("§aConta do Discord foi desregistrada com sucesso, yay!".fromLegacySectionToTextComponent())

            m.discordAccountAssociationsWebhook.send("Conta **`${player.username}`** (`${player.uniqueId}`) foi desassociada da conta `${account.discordId}` (<@${account.discordId}>)")
            return
        }
    }

    class DiscordExecutor(private val m: SparklyNeonVelocity) : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            context.sendMessage(
                TextComponent {
                    appendTextComponent {
                        color(NamedTextColor.YELLOW)
                        content("Nosso Discord!")
                    }

                    appendTextComponent {
                        content(" ")
                    }

                    appendTextComponent {
                        content("https://discord.gg/sparklypower")
                        clickEvent(ClickEvent.openUrl("https://discord.gg/sparklypower"))
                    }

                    appendNewline()
                    appendTextComponent {
                        color(NamedTextColor.YELLOW)
                        appendTextComponent {
                            content("Se você quer conectar a sua conta do SparklyPower no Discord, use ")
                        }
                        appendCommand("/registrar")
                        appendTextComponent {
                            content("no nosso servidor no Discord, no bot Pantufa!")
                        }
                    }
                }
            )
        }
    }
}