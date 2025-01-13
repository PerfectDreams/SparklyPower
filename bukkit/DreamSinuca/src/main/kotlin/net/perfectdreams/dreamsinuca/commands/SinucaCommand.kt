package net.perfectdreams.dreamsinuca.commands

import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamsinuca.DreamSinuca
import net.perfectdreams.dreamsinuca.sinuca.SparklySinuca
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.ContinuousDetectionMode
import org.dyn4j.geometry.Rectangle
import org.dyn4j.geometry.Vector2
import org.dyn4j.world.World

class SinucaCommand(val m: DreamSinuca) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("sinuca")) {
        executor = SinucaExecutor(m)
    }

    class SinucaExecutor(val m: DreamSinuca) : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val player = player("player")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            // "Game Origin"
            val location = Location(Bukkit.getWorld("world"), 1099.5, 65.1, -883.5, 0f, 0f)

            // Everything is smoool, but that's not a problem! dyn4j works fine with this, and that's actually how it is expected to be used!
            val playfield = World<Body>()

            // YES THIS DOES INFLUENCE THE GAMEPLAY BEHAVIOR, EVEN THO WE ARE STEPPING MANUALLY!!!
            // By setting it to 20 ticks, the gameplay ticks WAY faster
            playfield.settings.stepFrequency = 1.0 / 20.0
            playfield.gravity = World.ZERO_GRAVITY
            // playfield.settings.continuousDetectionMode = ContinuousDetectionMode.BULLETS_ONLY
            // playfield.settings.velocityConstraintSolverIterations = 1
            // playfield.settings.positionConstraintSolverIterations = 1
            playfield.settings.maximumAtRestLinearVelocity = 0.1
            playfield.settings.maximumAtRestAngularVelocity = 0.1

            val sparklySinuca = SparklySinuca(
                m,
                player,
                args[options.player],
                location,
                5f,
                3f,
                playfield
            )

            m.sinucas.add(sparklySinuca)

            m.launchMainThread {
                while (true) {
                    sparklySinuca.gameOriginLocation.world.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        sparklySinuca.gameOriginLocation.clone().add(
                            -sparklySinuca.translationOffsetX.toDouble(),
                            0.0,
                            -sparklySinuca.translationOffsetY.toDouble(),
                        ),
                        1
                    )

                    sparklySinuca.gameOriginLocation.world.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        sparklySinuca.gameOriginLocation.clone().add(
                            sparklySinuca.translationOffsetX.toDouble(),
                            0.0,
                            -sparklySinuca.translationOffsetY.toDouble(),
                        ),
                        1
                    )

                    sparklySinuca.gameOriginLocation.world.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        sparklySinuca.gameOriginLocation.clone().add(
                            sparklySinuca.translationOffsetX.toDouble(),
                            0.0,
                            sparklySinuca.translationOffsetY.toDouble(),
                        ),
                        1
                    )

                    sparklySinuca.gameOriginLocation.world.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        sparklySinuca.gameOriginLocation.clone().add(
                            -sparklySinuca.translationOffsetX.toDouble(),
                            0.0,
                            sparklySinuca.translationOffsetY.toDouble(),
                        ),
                        1
                    )

                    for (wall in sparklySinuca.walls) {
                        val shape = wall.getFixture(0).shape as Rectangle
                        val centerX = wall.worldCenter.x
                        val centerY = wall.worldCenter.y

                        val topX = wall.worldCenter.x - (shape.width / 2)
                        val topY = wall.worldCenter.y - (shape.height / 2)
                        val bottomX = wall.worldCenter.x + (shape.width / 2)
                        val bottomY = wall.worldCenter.y + (shape.height / 2)

                        // Bukkit.broadcastMessage("Wall: $topX, $topY to $bottomX, $bottomY (center: $centerX; $centerY)")
                        /* sparklySinuca.gameOriginLocation.world.spawnParticle(
                            Particle.NOTE,
                            sparklySinuca.gameOriginLocation.clone().add(
                                topX - sparklySinuca.translationOffsetX,
                                0.0,
                                topY - sparklySinuca.translationOffsetY,
                            ),
                            1
                        )

                        sparklySinuca.gameOriginLocation.world.spawnParticle(
                            Particle.NOTE,
                            sparklySinuca.gameOriginLocation.clone().add(
                                bottomX - sparklySinuca.translationOffsetX,
                                0.0,
                                bottomY - sparklySinuca.translationOffsetY,
                            ),
                            1
                        ) */
                    }

                    delayTicks(4L)
                }
            }

            // val another = playfield.spawnCircle(5f, 3f, 0.01f, Color.RED)
            // another.velocity = Vector2f(0.1f, 0f)

            sparklySinuca.startBallTrajectoryPreviewTask()
            sparklySinuca.updateGameStatusDisplay()
            sparklySinuca.updateGameObjects()
        }
    }

    data class StepCollision(val body1: Body, val body2: Body, val point: Vector2, val depth: Double) {

    }
}