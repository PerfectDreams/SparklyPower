package net.perfectdreams.dreamreflections.commands

import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamreflections.DreamReflections
import net.perfectdreams.dreamreflections.sessions.storedmodules.AutoRespawn
import net.perfectdreams.dreamreflections.sessions.storedmodules.ViolationCounterModule
import org.bukkit.Bukkit
import org.bukkit.util.Vector
import java.io.File

class DreamReflectionsCommand(val m: DreamReflections) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("reflections")) {
        permission = "dreamreflections.use"

        subcommand(listOf("player")) {
            permission = "dreamreflections.use"
            executor = PlayerInfoExecutor(m)
        }

        subcommand(listOf("topviolations")) {
            permission = "dreamreflections.use"
            executor = TopViolationsExecutor(m)
        }

        subcommand(listOf("killaura")) {
            permission = "dreamreflections.use"
            executor = KillAuraTesterExecutor(m)
        }

        subcommand(listOf("killauralegit")) {
            permission = "dreamreflections.use"
            executor = KillAuraLegitTesterExecutor(m)
        }

        subcommand(listOf("nofallchecker")) {
            permission = "dreamreflections.use"
            executor = NoFallCheckerExecutor(m)
        }
    }

    class PlayerInfoExecutor(val m: DreamReflections) : SparklyCommandExecutor() {
        class Options : CommandOptions() {
            val player = player("player")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = args[options.player]
            val session = m.getActiveReflectionSession(player)
            if (session == null) {
                context.sendMessage {
                    appendTextComponent {
                        content("Nenhuma sessão de reflexões ativa para ${player.name}!")
                        color(NamedTextColor.RED)
                    }
                }
                return
            }

            // val swingStreaks = x.swingsPerSecond.getSwingStreaks()

            val cps = session.swingsPerSecond.getClicksPerSecond()
            val lastClickEpochMillis = session.swingsPerSecond.swings.lastOrNull()
            val suspiciousRespawns = session.autoRespawn.getSuspiciousRespawns()

            context.sendMessage {
                appendTextComponent {
                    content("Reflexões sobre ${player.name}:")
                    color(DreamReflections.REFLECTIONS_COLOR)
                }
                appendNewline()

                val viaVersion = Via.getAPI()
                val playerVersion = ProtocolVersion.getProtocol(viaVersion.getPlayerVersion(player)).name

                appendTextComponent {
                    content("Versão: ${if (player.isBedrockClient) { "Minecraft: Bedrock Edition (emulando $playerVersion)" } else { "Minecraft $playerVersion" }}")
                }
                appendNewline()
                appendTextComponent {
                    content("Brand: ${player.clientBrandName}")
                }
                appendNewline()

                if (lastClickEpochMillis != null) {
                    appendTextComponent {
                        content("Cliques por Segundos: $cps (último clique a ${System.currentTimeMillis() - lastClickEpochMillis}ms atrás)")
                    }
                } else {
                    appendTextComponent {
                        content("Cliques por Segundos: $cps")
                    }
                }

                appendNewline()

                fun appendViolationCounterModule(module: ViolationCounterModule) {
                    appendTextComponent {
                        color(DreamReflections.MODULE_NAME_COLOR)
                        content("${module.moduleName}:")
                    }
                    appendTextComponent {
                        color(NamedTextColor.RED)
                        content(" ${module.violations}x")
                    }
                    appendNewline()
                }

                appendViolationCounterModule(session.boatFly)
                appendViolationCounterModule(session.wurstNoFall)
                appendViolationCounterModule(session.killAura)
                appendViolationCounterModule(session.killAuraRotation)
                appendViolationCounterModule(session.fastPlace)
                appendViolationCounterModule(session.wurstCreativeFlight)
                appendNewline()
                appendTextComponent {
                    color(TextColor.color(DreamReflections.MODULE_NAME_COLOR))
                    content("AutoRespawn:")
                }
                appendTextComponent {
                    color(NamedTextColor.RED)
                    content(" ${suspiciousRespawns.size} respawns em menos de ${AutoRespawn.SUSPICIOUS_MILLISECONDS}ms")
                }
            }
        }
    }

    class TopViolationsExecutor(val m: DreamReflections) : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            context.sendMessage {
                for ((player, session) in m.activeReflectionSessions) {
                    fun appendViolationCounterModuleIfNotZero(module: ViolationCounterModule) {
                        if (module.violations != 0) {
                            appendTextComponent {
                                color(DreamReflections.MODULE_NAME_COLOR)
                                content("${module.moduleName}:")
                            }
                            appendTextComponent {
                                color(NamedTextColor.RED)
                                content(" ${module.violations}x")
                            }
                            appendNewline()
                        }
                    }

                    appendTextComponent {
                        content("${player.name}:")
                    }
                    appendNewline()
                    appendViolationCounterModuleIfNotZero(session.boatFly)
                    appendViolationCounterModuleIfNotZero(session.wurstNoFall)
                    appendViolationCounterModuleIfNotZero(session.killAura)
                    appendViolationCounterModuleIfNotZero(session.killAuraRotation)
                    appendViolationCounterModuleIfNotZero(session.fastPlace)
                    appendViolationCounterModuleIfNotZero(session.wurstCreativeFlight)
                }
            }
        }
    }

    class KillAuraTesterExecutor(val m: DreamReflections) : SparklyCommandExecutor() {
        class Options : CommandOptions() {
            val player = player("player")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = args[options.player]

            val success = m.spawnKillAuraTester(player)
            if (success) {
                context.sendMessage {
                    color(NamedTextColor.YELLOW)
                    content("Teste de KillAura iniciado!")
                }
            } else {
                context.sendMessage {
                    color(NamedTextColor.RED)
                    content("Não foi possível iniciar o teste de KillAura... Será que o player já está sendo testado?")
                }
            }
        }
    }

    class KillAuraLegitTesterExecutor(val m: DreamReflections) : SparklyCommandExecutor() {
        class Options : CommandOptions() {
            val player = player("player")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = args[options.player]

            val success = m.spawnKillAuraLegitTester(player)
            if (success) {
                context.sendMessage {
                    color(NamedTextColor.YELLOW)
                    content("Teste de KillAura Legit iniciado!")
                }
            } else {
                context.sendMessage {
                    color(NamedTextColor.RED)
                    content("Não foi possível iniciar o teste de KillAura... Será que o player já está sendo testado?")
                }
            }
        }
    }

    class NoFallCheckerExecutor(val m: DreamReflections) : SparklyCommandExecutor() {
        class Options : CommandOptions() {
            val player = player("player")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = args[options.player]

            context.sendMessage {
                color(NamedTextColor.YELLOW)
                content("Executando teste de NoFall...")
            }

            val originalLocation = player.location

            m.launchMainThread {
                repeat(20) {
                    player.teleport(originalLocation.clone().add(0.0, 1.0, 0.0))
                    player.velocity = player.velocity.add(Vector(0f, -1.0f, 0.0f))
                    delayTicks(1L)
                }

                context.sendMessage {
                    color(NamedTextColor.GREEN)
                    content("Teste finalizado!")
                }
            }
        }
    }
}