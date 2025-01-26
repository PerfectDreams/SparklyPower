package net.perfectdreams.pantufa.interactions.vanilla.moderation

import net.perfectdreams.loritta.common.commands.CommandCategory
import net.perfectdreams.loritta.morenitta.interactions.UnleashedContext
import net.perfectdreams.loritta.morenitta.interactions.commands.*
import net.perfectdreams.loritta.morenitta.interactions.commands.options.ApplicationCommandOptions
import net.perfectdreams.pantufa.dao.Command
import net.perfectdreams.pantufa.utils.MessagePanelType
import net.perfectdreams.pantufa.utils.saveAndCreateData
import net.perfectdreams.pantufa.interactions.vanilla.moderation.stuff.CommandsLogUtils.buildCommandsLogMessage
import net.perfectdreams.pantufa.network.Databases
import net.perfectdreams.pantufa.api.commands.styled
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class CommandsLogCommand : SlashCommandDeclarationWrapper {
    companion object {
        const val COMMANDS_PER_PAGE = 7
    }

    override fun command() = slashCommand("commands", "Lista dos comandos usados com base nos critérios escolhidos", CommandCategory.MODERATION) {
        subcommand("log", "Lista dos comandos usados com base nos critérios escolhidos") {
            executor = CommandsLogExecutor()
        }
    }

    inner class CommandsLogExecutor : LorittaSlashCommandExecutor() {
        inner class Options : ApplicationCommandOptions() {
            val player = optionalString("player", "Nome do jogador")
            val world = optionalString("world", "Mundo em que o comando foi utilizado")
            val commandUsed = optionalString("command_used", "Comando que foi utilizado")
            val page = optionalInteger("page", "A página que você quer visualizar")
            val args = optionalString("args", "Argumentos usados dentro do comando")
        }

        override val options = Options()

        override suspend fun execute(context: UnleashedContext, args: SlashCommandArguments) {
            context.deferChannelMessage(true)
            
            val mainLandGuild = context.pantufa.mainLandGuild
            val sparklyPower = context.pantufa.config.sparklyPower

            val staffRole = mainLandGuild!!.getRoleById(sparklyPower.guild.staffRoleId)!!

            if (!context.member.roles.contains(staffRole)) {
                context.reply(true) {
                    styled(
                        "Ei! Você não tem permissão para usar este comando.",
                        "<:pantufa_bonk:1028160322990776331>"
                    )
                }
                return
            }
            val playerWhoExecuted = args[options.player]
            val worldThatWasExecuted = args[options.world]
            val commandUsed = args[options.commandUsed]
            val givenArguments = args[options.args]
            val page = args[options.page] ?: 0

            val fetchedCommands = Command.fetchCommands(
                playerWhoExecuted,
                worldThatWasExecuted,
                commandUsed,
                givenArguments
            )

            val size = transaction(Databases.sparklyPower) { fetchedCommands.count() }

            if (page < 0 || page * COMMANDS_PER_PAGE > size) {
                context.reply(true) {
                    styled(
                        "Essa página não existe! Não parece que esse jogador usa tantos comandos afinal de contas..."
                    )
                }
                return
            }

            val messageData = saveAndCreateData(
                size,
                context.user.idLong,
                UUID.randomUUID(),
                MessagePanelType.COMMANDS_LOG,
                fetchedCommands
            )

            context.reply(false, messageData.buildCommandsLogMessage(
                context.pantufa,
                context.user.idLong,
                page.toLong(),
            ))
        }
    }
}