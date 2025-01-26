package net.perfectdreams.pantufa.interactions.vanilla.moderation

import com.github.michaelbull.jdbc.transaction
import net.dv8tion.jda.api.utils.AttachedFile
import net.perfectdreams.loritta.common.commands.CommandCategory
import net.perfectdreams.loritta.morenitta.interactions.UnleashedContext
import net.perfectdreams.loritta.morenitta.interactions.commands.*
import net.perfectdreams.loritta.morenitta.interactions.commands.options.ApplicationCommandOptions
import net.perfectdreams.pantufa.api.commands.styled
import net.perfectdreams.pantufa.dao.Command
import net.perfectdreams.pantufa.network.Databases
import net.perfectdreams.pantufa.tables.Commands
import net.perfectdreams.pantufa.utils.extensions.username
import net.perfectdreams.pantufa.utils.extensions.uuid
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.io.path.createTempFile
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

class AllCommandsCommand : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand("allcommands", "Veja todos os comandos que um player usou!", CommandCategory.MODERATION) {
        executor = AllCommandCommandExecutor()
    }

    inner class AllCommandCommandExecutor : LorittaSlashCommandExecutor() {
        inner class Options : ApplicationCommandOptions() {
            val player = string("player", "Nome do jogador")
        }

        override val options = Options()

        override suspend fun execute(context: UnleashedContext, args: SlashCommandArguments) {
            // defer cuz it can take a while
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

            // let's support UUID... why not?
            val isAnUUID = try {
                UUID.fromString(args[options.player])
                true
            } catch (e: IllegalArgumentException) {
                false
            }

            val player = if (isAnUUID) {
                UUID.fromString(args[options.player]).username
            } else {
                args[options.player]
            }

            if (player == null) {
                context.reply(true) {
                    styled(
                        "O jogador **${args[options.player]}** não foi encontrado!",
                        "<:pantufa_bonk:1028160322990776331>"
                    )
                }
                return
            }

            val commandsStringBuilder = StringBuilder()

            val commands = transaction(Databases.sparklyPower) {
                Command.find { Commands.player eq player }
                    .forEach { command ->
                        // [xx-xx-xxxx xx:xx:xx] - <player> - /command
                        // - world:
                        // - args:
                        // - X, Y, Z:
                        // - Full Command: /command args
                        val instant = Instant.ofEpochMilli(command.time).atZone(ZoneId.of("America/Sao_Paulo"))
                        val hour = "${instant.hour}".padStart(2, '0')
                        val minute = "${instant.minute}".padStart(2, '0')
                        val second = "${instant.second}".padStart(2, '0')

                        val formattedTime = "[${instant.dayOfMonth}-${instant.monthValue}-${instant.year} $hour:$minute:$second]"

                        commandsStringBuilder.append("$formattedTime - ${command.player} - /${command.alias}\n")
                        commandsStringBuilder.append("  - Args: ${command.args ?: ""}\n")
                        commandsStringBuilder.append("  - World: ${command.world}\n")
                        commandsStringBuilder.append("  - XYZ: ${command.x}, ${command.y}, ${command.z}\n")
                        commandsStringBuilder.append("  - Full Command: /${command.alias} ${command.args ?: ""}\n")
                        commandsStringBuilder.appendLine()
                    }
            }

            if (commandsStringBuilder.isEmpty()) {
                context.reply(true) {
                    styled(
                        "O jogador **${player}** (`${player.uuid()}`) não executou nenhum comando!",
                        "<:pantufa_bonk:1028160322990776331>"
                    )
                }
                return
            }

            context.reply(true) {
                files.plusAssign(
                    AttachedFile.fromData(commandsStringBuilder.toString().toByteArray(Charsets.UTF_8), "commands-${player}.txt")
                )
            }
        }
    }
}