package net.perfectdreams.dreamajuda.commands

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.decodeFromString
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.util.TriState
import net.perfectdreams.dreamajuda.tutorials.SparklyTutorial
import net.perfectdreams.dreamajuda.DreamAjuda
import net.perfectdreams.dreamajuda.cutscenes.CutsceneEntityManager
import net.perfectdreams.dreamajuda.cutscenes.SparklyCutsceneCamera
import net.perfectdreams.dreamajuda.cutscenes.SparklyTutorialCutsceneConfig
import net.perfectdreams.dreamajuda.cutscenes.SparklyTutorialCutsceneFinalCut
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendCommand
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawn
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawnWithEffects
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawnWithEffectsAwait
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamemptyworldgenerator.EmptyBiomeProvider
import net.perfectdreams.dreamemptyworldgenerator.EmptyWorldGenerator
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import java.io.File
import kotlin.time.measureTimedValue

class SkipTutorialExecutor(val m: DreamAjuda) : SparklyCommandExecutor() {
    companion object {
        // TODO: Do everything on the same world, or do everything in different worlds?
        //  My concern is that creating a different world for each player will lag the server, because loading worlds is a bit expensive
        //  Took 13.922030ms to load the ephemeral world for jubbskkk
        //  Took 3.222899ms to unload the ephemeral world for jubbskkk
        //  It isn't that expensive, but you also need to think how to handle WHEN the world will be loaded/unloaded

        const val COPY_WORLD_FROM_TEMPLATE_WORLD = false
    }

    override fun execute(context: CommandContext, args: CommandArguments) {
        val player = context.requirePlayer()

        val activeTutorial = m.activeTutorials[player]
        if (activeTutorial != null) {
            context.sendMessage {
                color(NamedTextColor.YELLOW)
                appendTextComponent {
                    content("Você saiu do tutorial... Se você quiser voltar no tutorial, basta usar ")
                }
                appendCommand("/tutorial")
                appendTextComponent {
                    content("!")
                }
            }
            m.endTutorial(player)
            player.teleportToServerSpawnWithEffects()
            for (staff in Bukkit.getOnlinePlayers().asSequence().filter { it.hasPermission("dreamajuda.snooptutorial") }) {
                staff.sendMessage(
                    textComponent {
                        color(NamedTextColor.GRAY)
                        appendTextComponent {
                            append("Player ")
                        }
                        appendTextComponent {
                            color(NamedTextColor.AQUA)
                            append(activeTutorial.player.name)
                        }
                        appendTextComponent {
                            color(NamedTextColor.RED)
                            append(" pulou o tutorial")
                        }
                        appendTextComponent {
                            append("! Seção do Tutorial que o Player estava: ${activeTutorial.activeTutorial::class.simpleName}")
                        }
                    }
                )
            }
            return
        } else {
            context.sendMessage {
                color(NamedTextColor.RED)
                appendTextComponent {
                    content("Você não está em um tutorial! Se você quer entrar no tutorial, use ")
                }
                appendCommand("/tutorial")
                appendTextComponent {
                    content("!")
                }
            }
            return
        }
    }
}