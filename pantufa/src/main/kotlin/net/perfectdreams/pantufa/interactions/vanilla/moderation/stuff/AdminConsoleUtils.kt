package net.perfectdreams.pantufa.interactions.vanilla.moderation.stuff

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.string
import mu.KotlinLogging
import net.dv8tion.jda.api.utils.FileUpload
import net.perfectdreams.loritta.morenitta.interactions.UnleashedContext
import net.perfectdreams.loritta.morenitta.interactions.commands.LegacyMessageCommandContext
import net.perfectdreams.loritta.morenitta.interactions.commands.LorittaLegacyMessageCommandExecutor
import net.perfectdreams.loritta.morenitta.interactions.commands.LorittaSlashCommandExecutor
import net.perfectdreams.loritta.morenitta.interactions.commands.SlashCommandArguments
import net.perfectdreams.loritta.morenitta.interactions.commands.options.ApplicationCommandOptions
import net.perfectdreams.loritta.morenitta.interactions.commands.options.OptionReference
import net.perfectdreams.pantufa.network.Databases
import net.perfectdreams.pantufa.tables.LuckPermsGroupPermissions
import net.perfectdreams.pantufa.tables.LuckPermsUserPermissions
import net.perfectdreams.pantufa.utils.Emotes
import net.perfectdreams.pantufa.api.commands.PantufaReply
import net.perfectdreams.pantufa.api.commands.createStyledContent
import net.perfectdreams.pantufa.utils.Server
import net.perfectdreams.pantufa.api.commands.styled
import net.sparklypower.rpc.proxy.ProxyExecuteCommandRequest
import net.sparklypower.rpc.proxy.ProxyExecuteCommandResponse
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object AdminConsoleUtils {
    open class AdminConsoleBungeeExecutor(
        val requiredServerPermission: String,
        val commandToBeExecuted: String,
        val server: Server
    ): LorittaSlashCommandExecutor(), LorittaLegacyMessageCommandExecutor {
        companion object {
            private val logger = KotlinLogging.logger {}
        }

        inner class Options : ApplicationCommandOptions() {
            val args = string("args", "Argumentos do comando")
        }

        override val options = Options()

        override suspend fun execute(context: UnleashedContext, args: SlashCommandArguments) {
            context.deferChannelMessage(false)

            logger.info { "Retrieving ${context.member.id}'s Minecraft Account (or failing if not present)..." }
            val minecraftAccountInfo = context.retrieveConnectedMinecraftAccount()!!

            logger.info { "Getting ${context.member.id}'s Minecraft Account (${minecraftAccountInfo.uniqueId}) permissions..." }
            val userPerms = transaction(Databases.sparklyPowerLuckPerms) {
                LuckPermsUserPermissions.selectAll()
                    .where { LuckPermsUserPermissions.uuid eq minecraftAccountInfo.uniqueId.toString() }.map { it[LuckPermsUserPermissions.permission] }
            }

            val groupNames = userPerms.filter { it.startsWith("group.") }
                .map { it.removePrefix("group.") }

            val allPermissions = groupNames.flatMap { getGroupPermissions(it) }

            logger.info { "Does ${context.user.id}'s Minecraft Account (${minecraftAccountInfo.uniqueId}) has permission $requiredServerPermission? ${requiredServerPermission !in allPermissions}" }
            if (requiredServerPermission !in allPermissions) {
                context.reply(true) {
                    styled(
                        "Você por o acaso tem permissão para fazer isso? Não, né. Então pare de fazer perder meu tempo!",
                        Emotes.PantufaBonk.toString()
                    )
                }
                return
            }

            logger.info { "Executing ${context.user.id}'s command in SparklyPower's BungeeCord with Minecraft Account (${minecraftAccountInfo.uniqueId})..." }

            val response = context.pantufa.proxyRPC.makeRPCRequest<ProxyExecuteCommandResponse>(
                ProxyExecuteCommandRequest(
                    minecraftAccountInfo.uniqueId,
                    "$commandToBeExecuted ${args[options.args]}"
                )
            )

            when (response) {
                is ProxyExecuteCommandResponse.Success -> {
                    val messages =  response.messages

                    var isFirst = true

                    val replies = messages.map {
                        val reply = PantufaReply(it, mentionUser = isFirst)
                        isFirst = false
                        reply
                    }

                    val repliesAsString = replies.joinToString("\n") { createStyledContent(it) }

                    // Now we are going to do some special checks
                    if (repliesAsString.length > 2000) { // if it is greater than 1900, we are going to send a file
                        context.reply(false) {
                            files += FileUpload.fromData(messages.joinToString("\n").toByteArray(Charsets.UTF_8), "result.txt")
                        }
                    } else {
                        context.reply(false) {
                            content = repliesAsString
                        }
                    }
                }
            }
        }

        override suspend fun convertToInteractionsArguments(
            context: LegacyMessageCommandContext,
            args: List<String>
        ): Map<OptionReference<*>, Any?>? {
            return mapOf(
                options.args to args.joinToString(" ")
            )
        }

        private fun getGroupPermissions(groupName: String): List<String> {
            val perms = mutableListOf<String>()

            val permissions = transaction(Databases.sparklyPowerLuckPerms) {
                LuckPermsGroupPermissions.selectAll().where { LuckPermsGroupPermissions.name eq groupName }.map { it[LuckPermsGroupPermissions.permission] }
            }

            perms.addAll(permissions)

            perms.addAll(
                permissions.filter { it.startsWith("group.") }
                    .map { it.removePrefix("group.") }
                    .flatMap { getGroupPermissions(it) }
            )

            return perms
        }
    }
}