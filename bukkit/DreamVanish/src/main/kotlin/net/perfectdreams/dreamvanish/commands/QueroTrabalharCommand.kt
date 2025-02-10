package net.perfectdreams.dreamvanish.commands

import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendCommand
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamvanish.DreamVanish
import net.perfectdreams.dreamvanish.DreamVanishAPI
import org.bukkit.Bukkit

class QueroTrabalharCommand(val m: DreamVanish) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("querotrabalhar")) {
        permission = "dreamvanish.querotrabalhar"

        executor = QueroTrabalharCommandExecutor()
    }

    inner class QueroTrabalharCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val playerName = optionalWord("player_name")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val playerName = args[options.playerName]

            if (playerName == null) {
                // If playerName is null, our target is ourselves
                val player = context.requirePlayer()

                if (DreamVanishAPI.isQueroTrabalhar(player)) {
                    DreamVanishAPI.queroTrabalharPlayers.remove(player)
                    context.sendMessage {
                        append("Você saiu do modo trabalhar!") {
                            color(NamedTextColor.GREEN)
                        }
                    }
                } else {
                    DreamVanishAPI.queroTrabalharPlayers.add(player)
                    context.sendMessage {
                        append("Você agora está no modo trabalhar!") {
                            color(NamedTextColor.GREEN)
                        }
                        append("\n")
                        append("Use o ") {
                            color(NamedTextColor.GRAY)
                        }
                        appendCommand("/querotrabalhar")
                        append(" apenas quando for necessário, você é da Staff, seu trabalho é ajudar os players!") {
                            color(NamedTextColor.GRAY)
                        }
                    }
                }
            } else {
                // If playerName is not null, our target is another player
                val player = Bukkit.getPlayer(playerName) ?: run {
                    context.sendMessage {
                        append("O jogador ") {
                            color(NamedTextColor.RED)
                        }
                        append(playerName) {
                            color(NamedTextColor.AQUA)
                        }
                        append(" não está online!") {
                            color(NamedTextColor.RED)
                        }
                    }
                    return
                }

                if (DreamVanishAPI.isQueroTrabalhar(player)) {
                    DreamVanishAPI.queroTrabalharPlayers.remove(player)

                    context.sendMessage {
                        append("§aO jogador ") {
                            color(NamedTextColor.AQUA)
                        }
                        append(playerName) {
                            color(NamedTextColor.AQUA)
                        }
                        append(" §asaiu do modo trabalhar!") {
                            color(NamedTextColor.GREEN)
                        }
                    }

                    player.sendMessage("§aVocê saiu do modo trabalhar!")
                } else {
                    DreamVanishAPI.queroTrabalharPlayers.add(player)

                    context.sendMessage {
                        append("O jogador ") {
                            color(NamedTextColor.GREEN)
                        }
                        append(playerName) {
                            color(NamedTextColor.AQUA)
                        }
                        append(" agora está no modo trabalhar!") {
                            color(NamedTextColor.GREEN)
                        }
                        append("\n")
                        append("Use o ") {
                            color(NamedTextColor.GRAY)
                        }
                        appendCommand("/querotrabalhar")
                        append(" apenas quando for necessário, você é da Staff, seu trabalho é ajudar os players!") {
                            color(NamedTextColor.GRAY)
                        }
                    }

                    player.sendMessage("§aVocê agora está no modo trabalhar!")
                }
            }
        }
    }
}