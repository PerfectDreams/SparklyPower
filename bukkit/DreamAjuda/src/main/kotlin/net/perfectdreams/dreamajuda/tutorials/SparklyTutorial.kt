package net.perfectdreams.dreamajuda.tutorials

import com.griefprevention.visualization.BoundaryVisualization
import com.griefprevention.visualization.VisualizationType
import io.papermc.paper.math.Position
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import me.ryanhamshire.GriefPrevention.util.BoundingBox
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.perfectdreams.dreamajuda.theatermagic.RecordingPlaybackContext
import net.perfectdreams.dreamajuda.theatermagic.TheaterMagicStoredRecordingAnimation
import net.perfectdreams.dreamchat.DreamChat
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.BlockUtils
import net.perfectdreams.dreamcore.utils.LocationUtils
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendCommand
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawn
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawnWithEffects
import net.perfectdreams.dreamcore.utils.npc.SkinTexture
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import org.bukkit.*
import org.bukkit.craftbukkit.entity.CraftLightningStrike
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.LightningStrike
import org.bukkit.event.weather.LightningStrikeEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.io.File
import kotlin.math.atan2
import kotlin.math.sqrt

sealed class SparklyTutorial(val playerTutorial: PlayerTutorial) {
    var isCompleted = false

    abstract fun getTaskName(): TextComponent

    open fun getScoreboardExplanation(): List<TextComponent> = emptyList()

    abstract suspend fun onStart()
    abstract suspend fun onComplete()
    open fun cleanUp() {}

    fun playCompletionEffects() {
        playerTutorial.player.spawnParticle(
            Particle.TOTEM_OF_UNDYING,
            playerTutorial.player.location.clone().add(0.0, 2.0, 0.0),
            50
        )

        playerTutorial.player.playSound(
            playerTutorial.player,
            Sound.ENTITY_PLAYER_LEVELUP,
            1f,
            2f
        )
    }

    class LeaveTheSubway(playerTutorial: PlayerTutorial) : SparklyTutorial(playerTutorial) {
        override fun getTaskName() = textComponent {
            content("Sair do Metrô")
        }

        override fun getScoreboardExplanation() = listOf(
            textComponent {
                //      "Explore o mundo fora"
                content("Explore o mundo fora")
            },
            textComponent {
                //      "Explore o mundo fora"
                content("do metrô!")
            }
        )

        override suspend fun onStart() {
            this.playerTutorial.activeTutorial = this
            playerTutorial.pantufaNPC.teleport(Location(Bukkit.getWorld("RevampedTutorialIsland"), -119.53930367255634, 116.0, -74.58403106255085, -89.89465f, -1.8347139f))
        }

        override suspend fun onComplete() {
            playCompletionEffects()
            this.isCompleted = true
            // playerTutorial.player.sendMessage("Você completou o tutorial de sair do metrô")
            playerTutorial.switchActiveTutorial(KitNoob(playerTutorial))
        }
    }

    class KitNoob(playerTutorial: PlayerTutorial) : SparklyTutorial(playerTutorial) {
        override fun getTaskName() = textComponent {
            content("Ferramentas")
        }

        override suspend fun onStart() {
            playerTutorial.pantufaNPC.teleport(Location(Bukkit.getWorld("RevampedTutorialIsland"), -119.53930367255634, 116.0, -74.58403106255085, -89.89465f, -1.8347139f))

            /* playerTutorial.launchMainThreadTutorialTask {
                while (true) {
                    Bukkit.broadcastMessage("This should be cancelled when the tutorial is cancelled")
                    delayTicks(20L)
                }
            } */

            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Olá, ")
                    }

                    appendTextComponent {
                        color(NamedTextColor.AQUA)
                        content(playerTutorial.player.name)
                    }

                    appendTextComponent {
                        content("! Finalmente você chegou! A ")
                    }

                    appendTextComponent {
                        color(NamedTextColor.AQUA)
                        content("Loritta")
                    }

                    appendTextComponent {
                        content(" me contou sobre você...")
                    }
                }
            )
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Meu nome é Charlotte, amiga da ")
                    }

                    appendTextComponent {
                        color(NamedTextColor.AQUA)
                        content("Loritta")
                    }

                    appendTextComponent {
                        content(", mas pode me chamar de ")
                    }

                    appendTextComponent {
                        color(NamedTextColor.AQUA)
                        content("Pantufa")
                    }

                    appendTextComponent {
                        content(".")
                    }
                }
            )
            delayTicks(80L)
            // playerTutorial.sendMessageAsPantufa(textComponent { content("Eu sei que você deve estar pegando fogo para ir para a cidade grande, mas antes você precisa aprender um pouco.") })
            playerTutorial.sendMessageAsPantufa(textComponent { content("O lugar dos seus sonhos está logo ali, mas antes você precisa provar que você merece ir para lá.") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Espero que você já saiba o básico de Survival... Se você não sabe... bem, você teve infância? *risos*") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Calma, calma! Não fique triste, era apenas uma piada!")
                    }
                }
            )
            delayTicks(80L)
            this.playerTutorial.activeTutorial = this
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Como desculpas pela minha piadinha, vou te dar algumas ferramentas. Envie ")
                    }
                    appendCommand("/kit noob")
                    appendTextComponent {
                        content(" no chat para pegá-las!")
                    }
                }
            )

            /* playerTutorial.sendMessageAsPantufa(textComponent { content("Olá!") })
            delayTicks(40L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Você deve ser ${playerTutorial.player.name}, certo? A Loritta me falou que viria.") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Meu nome é Charlotte, mas pode me chamar de Pantufa.") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Seja Bem-Vindo ao SparklyPower!") })
            delayTicks(80L)
            // playerTutorial.sendMessageAsPantufa(textComponent { content("Eu sei que você deve estar pegando fogo para ir para a cidade grande, mas antes você precisa aprender um pouco.") })
            playerTutorial.sendMessageAsPantufa(textComponent { content("Eu sei que você deve estar pegando fogo para ir para a cidade grande, mas antes você precisa provar que você merece ir para lá.") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Eu espero que você já saiba as mecânicas básicas do Minecraft Survival... Se você não sabe, tem um carinha chamado Monark que fez um tutorial lá em 2010...") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("*risadas*") })
            delayTicks(80L)
            this.playerTutorial.activeTutorial = this
            playerTutorial.sendMessageAsPantufa(textComponent { content("Antes de a gente começar, use /kit noob!") }) */
        }

        override fun getScoreboardExplanation() = listOf(
            textComponent {
                //      "Sair do Metrô Metrô"
                content("Use /kit noob para")
            },
            textComponent {
                //      "Sair do Metrô Metrô"
                content("pegar ferramentas")
            }
        )

        override suspend fun onComplete() {
            playCompletionEffects()
            this.isCompleted = true
            /* playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("O que você acabou de usar foi um comando, comandos iniciam com ")
                    }

                    appendCommand("/")

                    appendTextComponent {
                        content(" e podem ser enviados no chat.")
                    }
                }
            )
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Com comandos você consegue enviar mensagens privadas, marcar teletransportes rápidos, ir para lugares, e muito mais!")
                    }
                }
            )
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    content("Inclusive, já que tocamos neste assunto, que tal a gente ir pegar recursos?")
                }
            ) */
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    content("Espero que você aceite estas ferramentas como desculpas pela minha piadinha. Aliás, você pode pegá-las novamente quando você quiser!")
                }
            )
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    content("Agora que você tem elas, que tal a gente ir pegar recursos?")
                }
            )
            delayTicks(80L)
            playerTutorial.switchActiveTutorial(WarpResources(playerTutorial))
        }
    }

    class WarpResources(playerTutorial: PlayerTutorial) : SparklyTutorial(playerTutorial) {
        override fun getTaskName() = textComponent {
            content("Novo Mundo")
        }

        override fun getScoreboardExplanation() = listOf(
            textComponent {
                //      "Explore o mundo fora"
                content("Use /warp recursos")
            },
            textComponent {
                //      "Explore o mundo fora"
                content("para explorar o")
            },
            textComponent {
                //      "Explore o mundo fora"
                content("mundo de recursos")
            }
        )

        override suspend fun onStart() {
            playerTutorial.pantufaNPC.teleport(Location(Bukkit.getWorld("RevampedTutorialIsland"), -119.53930367255634, 116.0, -74.58403106255085, -89.89465f, -1.8347139f))

            playerTutorial.sendMessageAsPantufa(textComponent { content("Para não deixar as nossas lindas cidades esburacadas e desflorestadas, nós temos um lugar especial para você pegar recursos.") })
            delayTicks(80L)
            this.playerTutorial.activeTutorial = this
            // Instead of saying "Envie /warp recursos no chat", we tell the user to "use the command /warp resources", this way we make them know
            // that those orange thingies that start with a slash are commands, without explictly telling them
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Para ir ao mundo de recursos, use o comando ")
                    }
                    appendCommand("/warp recursos")
                    appendTextComponent {
                        content("!")
                    }
                }
            )
        }

        override suspend fun onComplete() {
            this.isCompleted = true
            playerTutorial.pantufaNPC.teleport(Location(Bukkit.getWorld("RevampedTutorialIsland"), -119.54773500756609, 120.0, -40.680099608312005, 0.17218018f, -5.0027566f))
            playCompletionEffects()

            playerTutorial.sendMessageAsPantufa(textComponent { content("Boas-Vindas ao Mundo de Recursos!") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("O mundo de recursos é resetado periodicamente, o que torna este mundo horrível para construir a sua casa, mas ótimo para você, e outras pessoas, possam pegar os recursos que elas precisam.") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Deixe eu te mostrar um pouco as maravilhas deste mundo...") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("O Mundo de Recursos serve tanto para pegar os recursos acima da terra...") })
            val location = Location(Bukkit.getWorld("RevampedTutorialIsland"), -118.47921970757355, 120.0, 9.639218026999362, 0.40360194f, -5.012197f)
            playerTutorial.player.teleport(location) // Teletransportar player
            playerTutorial.player.sendMultiBlockChange(
                BlockUtils.getBlocksFromTwoLocations(
                    Location(playerTutorial.player.world, -120.0, 119.0, 8.0),
                    Location(playerTutorial.player.world, -118.0,119.0,10.0),
                ).associate {
                    Position.block(it.location) to Bukkit.createBlockData(Material.GRASS_BLOCK)
                }
            )

            delayTicks(80L)

            playerTutorial.sendMessageAsPantufa(textComponent { content("Como também os recursos que estão debaixo da terra!") })
            // (playerTutorial.player as CraftPlayer).sendEquipmentChange(playerTutorial.pantufaNPC.getEntity()!!, EquipmentSlot.HEAD, ItemStack.of(Material.NETHERITE_PICKAXE))
            playerTutorial.pantufaNPC.teleport(Location(Bukkit.getWorld("RevampedTutorialIsland"), -120.66472203257266, 102.0, 8.510008491942035, -55.94085f, 9.801685f))
            playerTutorial.pantufaNPC.equipment.setItem(EquipmentSlot.HAND, ItemStack.of(Material.NETHERITE_PICKAXE))

            playerTutorial.player.playSound(playerTutorial.player, "minecraft:entity.generic.explode", 1f, 1f)
            playerTutorial.player.spawnParticle(Particle.EXPLOSION, location, 10, 1.0, 1.0, 1.0)
            playerTutorial.player.sendMultiBlockChange(
                BlockUtils.getBlocksFromTwoLocations(
                    Location(playerTutorial.player.world, -120.0, 119.0, 8.0),
                    Location(playerTutorial.player.world, -118.0,119.0,10.0),
                ).associate {
                    Position.block(it.location) to it.blockData
                }
            )

            delayTicks(80L)
            // playerTutorial.player.sendMessage("Você completou o tutorial de teletransporte aleatório")
            playerTutorial.switchActiveTutorial(WarpSurvival(playerTutorial))
        }
    }

    /* class RandomResourcesTP(playerTutorial: PlayerTutorial) : SparklyTutorial(playerTutorial) {
        override suspend fun onStart() {
            playerTutorial.pantufaNPC.teleport(Location(Bukkit.getWorld("RevampedTutorialIsland"), -119.54773500756609, 120.0, -40.680099608312005, 0.17218018f, -5.0027566f))
            val context = RecordingPlaybackContext(
                playerTutorial.m,
                playerTutorial.player,
                Json.decodeFromString<TheaterMagicStoredRecordingAnimation>(
                    File(playerTutorial.m.dataFolder, "pantufa_warp_resources_random_tp.json").readText()
                ),
                playerTutorial.player.world
            ) {
                playerTutorial.pantufaNPC
            }

            context.startPlayback {}
            playerTutorial.sendMessageAsPantufa(textComponent { content("Aqui é o Mundo de Recursos! O lugar perfeito para você esburacar e pegar recursos!") })
            delayTicks(80L)
            this.playerTutorial.activeTutorial = this
            playerTutorial.sendMessageAsPantufa(textComponent { content("Vamos para um lugar aleatório, você pode ir para um lugar aleatório pisando nas placas de pressão!") })
        }

        override suspend fun onComplete() {
            this.isCompleted = true

            playerTutorial.sendMessageAsPantufa(textComponent { content("Ahhh, uma floresta, sinta o ar puro da natureza!") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Aqui você pode pegar quantos recursos você quiser! Não se preocupe, o Ibama não irá atrás de você pelo seu desmatamento.") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("O mundo de recursos serve tanto para pegar os recursos acima da terra...") })
            delayTicks(80L)

            playerTutorial.player.teleport(
                Location(
                    Bukkit.getWorld("RevampedTutorialIsland"),
                    -117.16457031505605,
                    101.0,
                    10.897285693891265,
                    130.32263f,
                    -0.5610731f
                )
            )
            playerTutorial.sendMessageAsPantufa(textComponent { content("Como também os recursos que estão debaixo da terra!") })
            playerTutorial.pantufaNPC.equipment.setItem(EquipmentSlot.HAND, ItemStack.of(Material.NETHERITE_PICKAXE))
            playerTutorial.pantufaNPC.teleport(Location(Bukkit.getWorld("RevampedTutorialIsland"), -120.66472203257266, 102.0, 8.510008491942035, -55.94085f, 9.801685f))

            delayTicks(80L)
            // playerTutorial.player.sendMessage("Você completou o tutorial de teletransporte aleatório")
            playerTutorial.switchActiveTutorial(WarpSurvival(playerTutorial))
        }
    }

    class CollectResources(playerTutorial: PlayerTutorial) : SparklyTutorial(playerTutorial) {
        override suspend fun onStart() {
            this.playerTutorial.activeTutorial = this
            playerTutorial.m.launchMainThread {
                playerTutorial.sendMessageAsPantufa(textComponent { content("Ahhh, uma floresta, sinta o ar puro da natureza!") })
                delayTicks(80L)
                playerTutorial.sendMessageAsPantufa(textComponent { content("Pena que iremos desflorestar as árvores, pegue uma madeira!") })
            }
        }

        override suspend fun onComplete() {
            this.isCompleted = true
            playerTutorial.player.sendMessage("Você completou o tutorial de coletar recursos aleatório")
            playerTutorial.switchActiveTutorial(WarpSurvival(playerTutorial))
        }
    } */

    class WarpSurvival(playerTutorial: PlayerTutorial) : SparklyTutorial(playerTutorial) {
        override fun getTaskName() = textComponent {
            content("Doce Lar")
        }

        override fun getScoreboardExplanation() = listOf(
            textComponent {
                //      "Sair do Metrô Metrô"
                content("Use /warp survival")
            },
            textComponent {
                //      "Sair do Metrô Metrô"
                content("para explorar o")
            },
            textComponent {
                //      "Sair do Metrô Metrô"
                content("mundo survival")
            }
        )

        override suspend fun onStart() {
            playerTutorial.sendMessageAsPantufa(textComponent { content("Enquanto você pode viver como um nômade, eu acho que você quer ter um lugar que você pode chamar de \"minha casa\".") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Nós temos um lugar que você pode construir a sua casa, e ainda por cima protegê-la para que ninguém possa quebrá-la!") })
            delayTicks(80L)
            this.playerTutorial.activeTutorial = this
            // Now we do the same thing as the "/warp recursos", but we only say "use"
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Vamos para lá! Use ")
                    }
                    appendCommand("/warp survival")
                    appendTextComponent {
                        content("!")
                    }
                }
            )
        }

        override suspend fun onComplete() {
            this.isCompleted = true
            playerTutorial.pantufaNPC.teleport(
                Location(Bukkit.getWorld("RevampedTutorialIsland"), -177.42485123822684, 125.0, 21.036693958711638, 179.51291f, -9.119219f)
            )
            playCompletionEffects()
            playerTutorial.sendMessageAsPantufa(textComponent { content("Boas-Vindas ao Mundo Survival!") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("O mundo survival é o lugar perfeito para você construir a sua casa, loja, castelo... e muito mais!") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Diferente do mundo de recursos, o mundo survival não é resetado, e você pode proteger as suas construções") })
            delayTicks(80L)
            playerTutorial.switchActiveTutorial(ProtectTerrain(playerTutorial))
        }
    }

    /* class RandomSurvivalTP(playerTutorial: PlayerTutorial) : SparklyTutorial(playerTutorial) {
        override suspend fun onStart() {
            playerTutorial.sendMessageAsPantufa(textComponent { content("O mundo survival é o lugar perfeito para você construir a sua casa, loja, apartamento, casinha de cachorro, e muito mais!") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Diferente do mundo de recursos, o mundo survival não é resetado, e você pode proteger as suas construções!") })
            delayTicks(80L)
            this.playerTutorial.activeTutorial = this
            playerTutorial.sendMessageAsPantufa(textComponent { content("E igual a warp recursos, você pode pisar nas placas de pressão para ir em um lugar aleatório, e é isso que iremos fazer!") })
            playerTutorial.activeTutorial = this
        }

        override suspend fun onComplete() {
            this.isCompleted = true
            playerTutorial.player.sendMessage("Você completou o tutorial de teletransporte aleatório (Survival)")
            playerTutorial.switchActiveTutorial(ProtectTerrain(playerTutorial))
            // m.activeTutorials[player] = ProtectTerrain(m, player, globalTutorialObjects)
        }
    } */

    class ProtectTerrain(playerTutorial: PlayerTutorial) : SparklyTutorial(playerTutorial) {
        override fun getTaskName() = textComponent {
            content("Proteção de Terrenos")
        }

        override fun getScoreboardExplanation() = listOf(
            textComponent {
                //      "Sair do Metrô Metrô"
                content("Proteja o terreno")
            },
            textComponent {
                //      "Sair do Metrô Metrô"
                content("antes que o griefer")
            },
            textComponent {
                //      "Sair do Metrô Metrô"
                content("comece a botar fogo")
            },
            textComponent {
                //      "Sair do Metrô Metrô"
                content("nele!")
            }
        )

        val points = listOf(
            Location(Bukkit.getWorld("RevampedTutorialIsland"), -133.0, 127.0, 92.0, 0f, 0f),
            Location(Bukkit.getWorld("RevampedTutorialIsland"), -112.0, 127.0, 68.0, 0f, 0f)
        )

        val grieferNPC = DreamCore.INSTANCE.sparklyNPCManager.spawnFakePlayer(
            playerTutorial.m,
            Location(Bukkit.getWorld("RevampedTutorialIsland"), -135.5417664090642, 0.0, 95.50864299101158, -134.90161f, -6.37381f), // Spawn the NPC somewhere
            "Griefer",
            SkinTexture(
                "ewogICJ0aW1lc3RhbXAiIDogMTczMzUwMzQ2Mjc4NywKICAicHJvZmlsZUlkIiA6ICI5MWYwNGZlOTBmMzY0M2I1OGYyMGUzMzc1Zjg2ZDM5ZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTdG9ybVN0b3JteSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mZGZmOWRiODM5ZGRlNjIyYTAyZDA2ODJmNDQwNjMyY2U5OTE2MmFkOWQ4NTFiOWJlNzQ0YjU3MTA4ZTg4YmQwIgogICAgfQogIH0KfQ==",
                "WHyBvFUmsZhCVBEjFVuFFjduMzBQSY1DFYgvMN0TVVPtwH1bTW8VenOmichkheSo2zDXMQQ9WXdkr40XaNX8kiaw2uvCQQQkYaALpNym8f58wf1xuAM++0O9goDBZHAy5246vPL/Fy5VMpAoZJx9VkaK1FCwBTP+txc1kLQuksWEZefhfSzUgSiFHNiXbKn4jw2FxjMD39xVA2u64toNeH+Rkj3WdcVFG8s6RxPi9KWkwiFZzLC9hzW9QhZnCWZJCa8Vr0ipy0F39eastYzdtXhxBwLkZ5MpEVEYhjEhCWtFwMoiswO3pBU5fiYqSrzGy1VZ7l5Hgbr8ZRIQKRdn8UbAKdcoNpxbWvjOg5v1ouJHYNntofO5MdREcFHXsnBwvD2kbmQEwUho/QGgLQYLuj9GMCKIeW0aEm9jQ9tCbemUBaeDec3mbwEoKd8hQdcV5Ng82zdgJhQOhFZxYfBopMjco/RQZ8X2tH2z7XrRrWezwjVhgCZUYuLKd9bbya7xwrWcJ21CAU9+tW1logrp4EOk0gWJ1nW8EgmFq5X67lOiy0Af0FszUdrFJwEUEHxs0OTvSHsj5xSoiIjetIAIffqw2AQn4pB/wan3gGWBm6d5fQDAfcWkuJont7K2ZtdPOtoV6wsIabXHL2e7xWkMn2gZNLbR/Wjqi0+gGzlj93Q="
            )
        ) { entity, npc ->
            npc.setVisibleByDefault(false)
            playerTutorial.player.showEntity(playerTutorial.m, entity)
            npc.equipment.setItem(EquipmentSlot.HAND, ItemStack.of(Material.FLINT_AND_STEEL))
        }

        override fun cleanUp() {
            grieferNPC.remove()
        }

        override suspend fun onStart() {
            val location = Location(Bukkit.getWorld("RevampedTutorialIsland"), -121.5, 127.0, 60.5, 0f, 0f)
            playerTutorial.player.teleport(location)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Já que estamos no mundo survival...") })

            playerTutorial.player.sendMultiBlockChange(
                BlockUtils.getBlocksFromTwoLocations(
                    Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -133.0, 126.0, 68.0),
                    Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -112.0,126.0,92.0)
                ).associate {
                    Position.block(it.location) to Bukkit.createBlockData(Material.GRASS_BLOCK)
                }
            )

            playerTutorial.player.sendMultiBlockChange(
                BlockUtils.getBlocksFromTwoLocations(
                    Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -133.0, 127.0, 68.0),
                    Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -112.0,135.0,92.0)
                ).associate {
                    Position.block(it.location) to Bukkit.createBlockData(Material.BARRIER)
                }
            )

            val context1 = RecordingPlaybackContext(
                playerTutorial.m,
                playerTutorial.player,
                playerTutorial.m.cutsceneCache.pantufaProtectionIntro,
                playerTutorial.player.world
            ) {
                playerTutorial.pantufaNPC
            }

            val job = playerTutorial.launchMainThreadTutorialTask {
                context1.startPlaybackOnCurrentCoroutine(0..200) {
                    if (it == 60) {
                        playerTutorial.sendMessageAsPantufa(textComponent { content("Eu posso fazer isso aqui...") })
                    }

                    if (it == 103) {
                        playerTutorial.player.sendMultiBlockChange(
                            BlockUtils.getBlocksFromTwoLocations(
                                Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -133.0, 127.0, 68.0),
                                Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -112.0,135.0,92.0)
                            ).associate {
                                Position.block(it.location) to it.blockData
                            }
                        )

                        playerTutorial.player.sendMultiBlockChange(
                            BlockUtils.getBlocksFromTwoLocations(
                                Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -133.0, 126.0, 68.0),
                                Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -112.0,126.0,92.0)
                            ).associate {
                                Position.block(it.location) to it.blockData
                            }
                        )

                        BlockUtils.getBlocksFromTwoLocations(
                            Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -133.0, 126.0, 68.0),
                            Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -112.0,135.0,92.0)
                        ).forEach {
                            if (it.type != Material.AIR) {
                                playerTutorial.player.spawnParticle(
                                    Particle.HAPPY_VILLAGER,
                                    it.location,
                                    1,
                                    0.5,
                                    0.5,
                                    0.5
                                )
                            }
                        }

                        playerTutorial.player.playSound(
                            playerTutorial.player,
                            "sparklypower:general.minikit_boom",
                            1f,
                            1f
                        )
                    }
                }
            }

            job.join()

            playerTutorial.sendMessageAsPantufa(textComponent { content("É essa a tal da casa automática que tanto falam? *risos*") })

            delayTicks(80L)

            playerTutorial.sendMessageAsPantufa(textComponent { content("Agora precisamos proteger ela, para que só a gente possa mexer dentro do terreno.") })

            delayTicks(80L)

            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Para proteger, pegue uma pá de ouro na sua mão. Você recebeu ela quando você recebeu as ferramentas do ")
                    }
                    appendCommand("/kit noob")
                }
            )

            /* delayTicks(80L)

            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Imagine... Como você cercaria a casa? Nesta cerca imaginária, as duas pontas na diagonal são as pontas que você precisa marcar com a pá de ouro!")
                    }
                }
            ) */

            /* playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Agora, imagine... Olhando de cima como se fosse um pássaro, como você faria um retângulo que cobre toda a casa?")
                    }
                }
            )

            delayTicks(80L)

            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Neste retângulo que você imaginou, as duas pontas que são conectadas pela diagonal são as pontas que você precisa marcar com a pá de ouro, clicando com botão direito!")
                    }
                }
            ) */

            delayTicks(80L)

            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Ações falam mais que palavras, então eu vou proteger esta casa e, depois que terminar, você tentará proteger sozinho, ok?")
                    }
                }
            )

            delayTicks(80L)

            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Sigam-me os bons!")
                    }
                }
            )

            val context = RecordingPlaybackContext(
                playerTutorial.m,
                playerTutorial.player,
                playerTutorial.m.cutsceneCache.pantufaProtection,
                playerTutorial.player.world
            ) {
                playerTutorial.pantufaNPC
            }

            playerTutorial.launchMainThreadTutorialTask {
                context.startPlaybackOnCurrentCoroutine(0..350) {
                    if (it == 65) {
                        BoundaryVisualization.visualizeArea(
                            playerTutorial.player,
                            BoundingBox.ofBlocks(listOf(points.last().block)),
                            VisualizationType.INITIALIZE_ZONE
                        )
                        playerTutorial.sendMessageAsPantufa(textComponent { content("Eu clico com botão direito aqui... O bloco de diamante indica que iniciamos o processo de proteção de terreno.") })
                        delayTicks(80L)
                        playerTutorial.sendMessageAsPantufa(textComponent { content("E aí eu vou para a outra ponta, na diagonal...") })
                    }

                    if (it == 275) {
                        BoundaryVisualization.visualizeArea(
                            playerTutorial.player,
                            BoundingBox.ofBlocks(points.map { it.block }),
                            VisualizationType.CLAIM
                        )
                        playerTutorial.sendMessageAsPantufa(textComponent { content("E clico com botão direito aqui! Os blocos de ouro e glowstone indicam que tudo deu certo, e mostram a borda do terreno!") })
                        delayTicks(80L)
                        playerTutorial.sendMessageAsPantufa(textComponent { content("Voilà! Tudo dentro da demarcação está protegido!") })
                    }
                }
            }.join()

            this.playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        content("Agora é a sua vez! Eu desprotegi o terreno usando ")
                    }
                    appendCommand("/abandonclaim")
                    appendTextComponent {
                        content(", e agora você—")
                    }
                }
            )

            playerTutorial.player.sendMultiBlockChange(
                BlockUtils.getBlocksFromTwoLocations(
                    Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -133.0, 126.0, 68.0),
                    Location(Bukkit.getWorld("RevampedTutorialIsland")!!, -112.0, 135.0,92.0)
                ).associate {
                    Position.block(it.location) to it.blockData
                }
            )

            delayTicks(80L)

            val grieferSpawn = Location(Bukkit.getWorld("RevampedTutorialIsland"), -135.5417664090642, 127.0, 95.50864299101158, -134.90161f, -6.37381f)

            grieferNPC.teleport(grieferSpawn)

            // Spawn fake lighting
            val lightingBoltEntity = this.playerTutorial.player.world.spawn(grieferSpawn, LightningStrike::class.java) {
                (it as CraftLightningStrike).handle.isEffect = true
                it.setVisibleByDefault(false)
                playerTutorial.player.showEntity(playerTutorial.m, it)
            }

            val pantufaLookCloseToGrieferJob = playerTutorial.launchMainThreadTutorialTask {
                while (true) {
                    val yawAndPitch = LocationUtils.getYawAndPitchLookingAt(this.playerTutorial.pantufaNPC.location, this.grieferNPC.location)

                    this.playerTutorial.pantufaNPC.teleport(
                        this.playerTutorial.pantufaNPC.location.clone()
                            .apply {
                                this.yaw = yawAndPitch.yaw
                                this.pitch = yawAndPitch.pitch
                            }
                    )
                    delayTicks(5L)
                }
            }

            this.playerTutorial.player.playSound(
                lightingBoltEntity.location,
                "minecraft:entity.lightning_bolt.thunder",
                1f,
                2f
            )

            val grieferContext = RecordingPlaybackContext(
                playerTutorial.m,
                playerTutorial.player,
                playerTutorial.m.cutsceneCache.tutorialGrieferIntro,
                playerTutorial.player.world
            ) {
                grieferNPC
            }

            this.playerTutorial.player.playSound(
                grieferNPC.getEntity()!!,
                "sparklypower:tutorial.griefer_gas",
                2.5f,
                1f
            )

            val grieferJob = playerTutorial.launchMainThreadTutorialTask {
                grieferContext.startPlaybackOnCurrentCoroutine(0..200) {}
            }

            delayTicks(40L)

            this.playerTutorial.sendMessageAsGriefer(
                textComponent {
                    content("Hmmm, que casa bonitinha...")
                }
            )

            delayTicks(80L)

            this.playerTutorial.sendMessageAsGriefer(
                textComponent {
                    content("Seria uma pena... se alguém colocasse fogo nela... rs")
                }
            )

            delayTicks(80L)

            pantufaLookCloseToGrieferJob.cancel()

            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    appendTextComponent {
                        color(NamedTextColor.AQUA)
                        content(playerTutorial.player.name)
                    }
                    appendTextComponent {
                        content("! Corra! Proteja o terreno antes que o griefer se renda a tentação e coloque fogo na MINHA casa!!")
                    }
                }
            )

            this.playerTutorial.activeTutorial = this
            return
        }

        override suspend fun onComplete() {
            playCompletionEffects()
            this.isCompleted = true
            BoundaryVisualization.visualizeArea(
                playerTutorial.player,
                BoundingBox.ofBlocks(BlockUtils.getBlocksFromTwoLocations(playerTutorial.fakeClaimPos1!!, playerTutorial.fakeClaimPos2!!)),
                VisualizationType.CLAIM
            )

            playerTutorial.sendMessageAsPantufa(textComponent { content("Você protegeu o terreno! Agora griefers não irão conseguir quebrar o seu terreno!") })
            delayTicks(80L)
            playerTutorial.sendMessageAsGriefer(textComponent { content("Que furada! O terreno está protegido!") })
            delayTicks(80L)
            playerTutorial.sendMessageAsGriefer(textComponent { content("Tô indo nessa antes que eu seja banido por griefing, fui!") })
            delayTicks(80L)

            grieferNPC.remove()

            // Spawn fake lighting
            val lightingBoltEntity = this.playerTutorial.player.world.spawn(grieferNPC.location, LightningStrike::class.java) {
                (it as CraftLightningStrike).handle.isEffect = true
                it.setVisibleByDefault(false)
                playerTutorial.player.showEntity(playerTutorial.m, it)
            }

            this.playerTutorial.player.playSound(
                lightingBoltEntity.location,
                "minecraft:entity.lightning_bolt.thunder",
                1f,
                2f
            )

            playerTutorial.sendMessageAsPantufa(textComponent { content("Ufa! Obrigada por proteger o terreno antes daquele meliante atrevesse a ARREBENTAR a casa.") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Se você quiser ver informações sobre um terreno, basta usar um graveto no chão.") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(textComponent { content("Você também pode expandir o seu terreno usando a pá de ouro, clicando em uma das pontas e depois clicando onde você deseja que fique a nova ponta.") })
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    append("Você também pode deixar seus amigos construirem no seu terreno usando ")
                    appendCommand("/trust")
                }
            )
            delayTicks(80L)
            // playerTutorial.switchActiveTutorial(Money(playerTutorial))

            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    append("Você está pronto para se aventurar no SparklyPower! Divirta-se! Bem... Talvez você ainda tenha ficado com algumas dúvidas pois o nosso tutorial não está 100% pronto, mas para qualquer outra dúvida, veja o ")
                    appendCommand("/ajuda")
                }
            )

            playerTutorial.m.endTutorial(playerTutorial.player)
            playerTutorial.player.teleportToServerSpawnWithEffects()
            for (staff in Bukkit.getOnlinePlayers().asSequence().filter { it.hasPermission("dreamajuda.snooptutorial") }) {
                staff.sendMessage(
                    textComponent {
                        color(NamedTextColor.GRAY)
                        appendTextComponent {
                            append("Player ")
                        }
                        appendTextComponent {
                            color(NamedTextColor.AQUA)
                            append(playerTutorial.player.name)
                        }
                        appendTextComponent {
                            color(NamedTextColor.GREEN)
                            append(" terminou o tutorial")
                        }
                        appendTextComponent {
                            append("! Seção do Tutorial que o Player estava: ${this@ProtectTerrain::class.simpleName}")
                        }
                    }
                )
            }
        }

        fun validateClaim(): Boolean {
            val pos1 = playerTutorial.fakeClaimPos1!!.clone().apply {
                this.y = -64.0
            }
            val pos2 = playerTutorial.fakeClaimPos2!!.clone().apply {
                this.y = 384.0
            }

            for (point in points) {
                if (!LocationUtils.isLocationBetweenLocations(point, pos1, pos2))
                    return false
            }

            return true
        }
    }

    /* class Money(playerTutorial: PlayerTutorial) : SparklyTutorial(playerTutorial) {
        override fun getTaskName() = textComponent {
            content("Sonecas")
        }

        override suspend fun onStart() {
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    append("Capitalismo é o que faz o mundo girar, e aqui temos a nossa própria moeda, as ")
                    append(NamedTextColor.WHITE, "\uE283")
                    append(NamedTextColor.GREEN, " Sonecas")
                    append("!")
                }
            )
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    append("Existem vários jeitos de ganhar ")
                    append(NamedTextColor.WHITE, "\uE283")
                    append(NamedTextColor.GREEN, " Sonecas")
                    append(", mas as principais maneiras são minerando ou farmando, vendendo os itens que você conseguiu na loja do spawn.")
                }
            )
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    append("Você pode ir para a loja do spawn com ")
                    appendCommand("/warp loja")
                    append(", e players podem criar as suas próprias lojas.")
                }
            )
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    append("Se você conhece a Loritta, você pode transferir seus sonhos para sonecas, e vice-versa!")
                }
            )
            delayTicks(80L)
            playerTutorial.sendMessageAsPantufa(
                textComponent {
                    append("Também temos os Pesadelos")
                }
            )
            delayTicks(80L)
            this.playerTutorial.activeTutorial = this
        }

        override suspend fun onComplete() {
            playerTutorial.m.endTutorial(playerTutorial.player)
            playerTutorial.player.teleportToServerSpawn()
        }
    } */

    /* class ViewTerrainInfo(playerTutorial: PlayerTutorial) : SparklyTutorial(playerTutorial) {
        override suspend fun onStart() {}

        override suspend fun onComplete() {
            // player.sendMessage("Você completou o tutorial de proteção de terrenos (Survival)")
        }
    } */
}