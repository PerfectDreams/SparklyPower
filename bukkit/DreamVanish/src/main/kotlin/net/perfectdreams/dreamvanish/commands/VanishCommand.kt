package net.perfectdreams.dreamvanish.commands

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamvanish.DreamVanish
import net.perfectdreams.dreamvanish.DreamVanishAPI
import org.bukkit.Bukkit

class VanishCommand(val m: DreamVanish) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("vanish")) {
        permission = "dreamvanish.vanish"

        executor = VanishCommandExecutor()
    }

    inner class VanishCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val playerName = optionalWord("player_name")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val playerName = args[options.playerName]

            if (playerName == null) {
                // If the playerName is null, our target is ourselves
                val player = context.requirePlayer()

                if (DreamVanishAPI.isVanished(player)) {
                    DreamVanishAPI.setVanishedStatus(player, false)
                    context.sendMessage {
                        append("Você agora está ") {
                            color(NamedTextColor.GREEN)
                        }

                        append("visível") {
                            decorate(TextDecoration.BOLD)
                            color(NamedTextColor.GREEN)
                        }

                        append("!") {
                            color(NamedTextColor.GREEN)
                        }
                    }
                } else {
                    DreamVanishAPI.setVanishedStatus(player, true)
                    context.sendMessage {
                        append("Você agora está ") {
                            color(NamedTextColor.GREEN)
                        }

                        append("invisível") {
                            decorate(TextDecoration.BOLD)
                            color(NamedTextColor.GREEN)
                        }

                        append("!") {
                            color(NamedTextColor.GREEN)
                        }
                    }
                }
            } else {
                // If the playerName is not null, our target is another player
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

                if (DreamVanishAPI.isVanished(player)) {
                    DreamVanishAPI.setVanishedStatus(player, false)
                    context.sendMessage {
                        append("O jogador ") {
                            color(NamedTextColor.GREEN)
                        }
                        append(playerName) {
                            color(NamedTextColor.AQUA)
                        }
                        append(" agora está ") {
                            color(NamedTextColor.GREEN)
                        }

                        append("visível") {
                            decorate(TextDecoration.BOLD)
                            color(NamedTextColor.GREEN)
                        }

                        append("!") {
                            color(NamedTextColor.GREEN)
                        }
                    }
                    player.sendMessage("§aVocê agora está §lvísível§a!")
                } else {
                    DreamVanishAPI.setVanishedStatus(player, true)
                    context.sendMessage {
                        append("O jogador ") {
                            color(NamedTextColor.GREEN)
                        }
                        append(playerName) {
                            color(NamedTextColor.AQUA)
                        }
                        append(" agora está ") {
                            color(NamedTextColor.GREEN)
                        }
                        append("invisível") {
                            decorate(TextDecoration.BOLD)
                            color(NamedTextColor.GREEN)
                        }
                        append("!") {
                            color(NamedTextColor.GREEN)
                        }
                    }
                    player.sendMessage("§aVocê agora está §linvisível§a!")
                }
            }
        }
    }
}