package net.perfectdreams.pantufa.interactions.vanilla.minecraft

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonObject
import dev.minn.jda.ktx.messages.Embed
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.perfectdreams.loritta.common.commands.CommandCategory
import net.perfectdreams.loritta.morenitta.interactions.UnleashedContext
import net.perfectdreams.loritta.morenitta.interactions.commands.*
import net.perfectdreams.loritta.morenitta.interactions.commands.options.OptionReference
import net.perfectdreams.pantufa.PantufaBot
import net.perfectdreams.pantufa.utils.Constants.SPARKLYPOWER_OFFLINE
import net.perfectdreams.pantufa.utils.socket.SocketUtils
import net.perfectdreams.pantufa.utils.Constants
import net.sparklypower.rpc.proxy.ProxyGetProxyOnlinePlayersRequest
import net.sparklypower.rpc.proxy.ProxyGetProxyOnlinePlayersResponse

class OnlineCommand(val m: PantufaBot) : SlashCommandDeclarationWrapper {
    companion object {
        val prettyNameServer = hashMapOf(
            "sparklypower_lobby" to "SparklyPower Lobby",
            "sparklypower_survival" to "SparklyPower Survival",
        )
    }

    override fun command() = slashCommand("online", "Veja os players que estão online no SparklyPower!", CommandCategory.MINECRAFT) {
        enableLegacyMessageSupport = true
        alternativeLegacyAbsoluteCommandPaths.apply {
            add("online")
        }

        executor = OnlineCommandExecutor()
    }

    inner class OnlineCommandExecutor : LorittaSlashCommandExecutor(), LorittaLegacyMessageCommandExecutor {
        override suspend fun execute(context: UnleashedContext, args: SlashCommandArguments) {
            val showGraph = false // args[options.showGraph]
            val jsonObject = JsonObject()
            val sparklyPower = context.pantufa.config.sparklyPower
            jsonObject["type"] = "getOnlinePlayersInfo"

            val getProxyOnlinePlayersResponse = try {
                m.proxyRPC.makeRPCRequest<ProxyGetProxyOnlinePlayersResponse>(ProxyGetProxyOnlinePlayersRequest)
            } catch (e: Exception) {
                SPARKLYPOWER_OFFLINE.invoke(context)
            }

            when (getProxyOnlinePlayersResponse) {
                is ProxyGetProxyOnlinePlayersResponse.Success -> {
                    val servers = getProxyOnlinePlayersResponse.players.groupBy { it.connectedToServerName }
                    val totalPlayersOnline = getProxyOnlinePlayersResponse.players.size
                    val survivalPlayers = mutableListOf<String>()
                    val lobbyPlayers = mutableListOf<String>()
                    var page = 0

                    for (server in servers) {
                        val name = server.key
                        val players = server.value.map { it.name }.sortedBy { it.lowercase() }

                        when (name) {
                            "sparklypower_survival" -> {
                                survivalPlayers.addAll(players)
                            }
                            "sparklypower_lobby" -> {
                                lobbyPlayers.addAll(players)
                            }
                        }
                    }

                    context.pantufa.launch {
                        context.reply(false) {
                            embeds += buildEmbed("SparklyPower Survival", survivalPlayers)
                            embeds += buildEmbed("SparklyPower Lobby", lobbyPlayers)
                        }
                    }
                }
            }
        }

        override suspend fun convertToInteractionsArguments(
            context: LegacyMessageCommandContext,
            args: List<String>
        ): Map<OptionReference<*>, Any?> {
            return LorittaLegacyMessageCommandExecutor.NO_ARGS
        }

        private fun buildEmbed(
            sectionName: String,
            sectionPlayers: List<String>
        ) = Embed {
            title = "**Players Online no $sectionName (${sectionPlayers.size} players online)**"
            color = Constants.LORITTA_AQUA.rgb

            description = if (sectionPlayers.isNotEmpty()) {
                sectionPlayers.joinToString(", ", transform = { "**`$it`**" })
            } else "Ninguém online... \uD83D\uDE2D"
        }
    }
}