package net.perfectdreams.dreamemotes.commands

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamemotes.DreamEmotes
import net.perfectdreams.dreamemotes.blockbench.BlockbenchModel
import net.perfectdreams.dreamemotes.gestures.SparklyGestureData
import java.io.File

class TestGestureCommand(val m: DreamEmotes) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("testgesture")) {
        permission = "dreamemotes.setup"
        executor = EmoteExecutor(m)
    }

    class EmoteExecutor(val m: DreamEmotes) : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val gestureFile = word("gesture_file")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()
            val requestedLocation = player.location

            val gestureFile = args[options.gestureFile]

            m.launchAsyncThread {
                val gestureSkinHeads = m.gesturesManager.getOrCreatePlayerGesturePlaybackSkins(player)

                val sidecar = Yaml.default.decodeFromString<SparklyGestureData>(File(m.gesturesFolder, "${gestureFile}.yml").readText(Charsets.UTF_8))

                val blockbenchModel = Json {
                    ignoreUnknownKeys = true
                }.decodeFromString<BlockbenchModel>(File(m.modelsFolder, sidecar.blockbenchModel + ".bbmodel").readText())

                onMainThread {
                    val currentPlayerLocation = player.location

                    if (requestedLocation.world == currentPlayerLocation.world && 2 >= currentPlayerLocation.distanceSquared(requestedLocation)) {
                        // Cancel current gesture just for us to get the CORRECT exit location of the player
                        m.gesturesManager.stopGesturePlayback(player)

                        m.gesturesManager.createGesturePlayback(
                            player,
                            currentPlayerLocation,
                            gestureSkinHeads,
                            blockbenchModel,
                            sidecar
                        )
                    } else {
                        player.sendMessage(
                            textComponent {
                                color(NamedTextColor.RED)
                                content("Você se moveu enquanto o gesto estava sendo carregado! Se você quiser usar o gesto, use o comando novamente.")
                            }
                        )
                    }
                }
            }
        }
    }
}