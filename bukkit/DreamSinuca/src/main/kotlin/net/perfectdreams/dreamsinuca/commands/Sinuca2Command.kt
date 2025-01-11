package net.perfectdreams.dreamsinuca.commands

import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamsinuca.DreamSinuca
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.dyn4j.collision.CollisionBody
import org.dyn4j.collision.Fixture
import org.dyn4j.collision.continuous.TimeOfImpact
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.BodyFixture
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Rectangle
import org.dyn4j.world.BroadphaseCollisionData
import org.dyn4j.world.ManifoldCollisionData
import org.dyn4j.world.NarrowphaseCollisionData
import org.dyn4j.world.World
import org.dyn4j.world.listener.CollisionListener
import org.dyn4j.world.listener.CollisionListenerAdapter
import org.dyn4j.world.listener.TimeOfImpactListener
import org.joml.Matrix4f
import org.joml.Vector2f
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.*

class Sinuca2Command(val m: DreamSinuca) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("sinucadyn4j")) {
        executor = SinucaExecutor(m)
    }

    class SinucaExecutor(val m: DreamSinuca) : SparklyCommandExecutor() {
        // 827x457
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

            val sparklySinuca = SparklySinuca(
                player,
                location,
                5f,
                3f,
                playfield
            )

            /* val listener = object: CollisionListener<Body, BodyFixture> {
                override fun collision(collision: BroadphaseCollisionData<Body, BodyFixture>): Boolean {
                    return true
                }

                override fun collision(collision: NarrowphaseCollisionData<Body, BodyFixture>): Boolean {
                    return true
                }

                override fun collision(collision: ManifoldCollisionData<Body, BodyFixture>): Boolean {
                    // We use Manifold because BroadphaseCollisionData has a lot of "false positives"
                    if (collision.body1 in sparklySinuca.ballToDisplayEntity.keys && collision.body2 in sparklySinuca.ballToDisplayEntity.keys) {
                        Bukkit.broadcastMessage("Batida de bolinha! ManifoldCollisionData")
                    }

                    return true
                }
            } */

            val listener2 = object: TimeOfImpactListener<Body> {
                override fun collision(body1: Body?, body2: Body?): Boolean {
                    return true
                }

                override fun collision(
                    body1: Body?,
                    fixture1: BodyFixture?,
                    body2: Body?,
                    fixture2: BodyFixture?
                ): Boolean {
                    return true
                }

                override fun collision(
                    body1: Body?,
                    fixture1: BodyFixture?,
                    body2: Body?,
                    fixture2: BodyFixture?,
                    toi: TimeOfImpact?
                ): Boolean {
                    return true
                }

                override fun collision(body1: Body, body2: Body, toi: TimeOfImpact): Boolean {
                    // This seems to be our "bread and butter" to listen for impacts
                    // I don't know the difference between the collision listener and this however...
                    if (sparklySinuca.balls.contains(body1) && sparklySinuca.balls.contains(body2)) {
                        Bukkit.broadcastMessage("ball bouncy!")

                        val x = (body1.worldCenter.x - sparklySinuca.translationOffsetX)
                        val z = (body1.worldCenter.y - sparklySinuca.translationOffsetY)

                        sparklySinuca.gameOriginLocation.world.playSound(sparklySinuca.gameOriginLocation.clone().add(x, 0.0, z), "sparklypower:snooker.ball_clack", 1f, 1f)
                    }

                    return true
                }
            }

            playfield.addTimeOfImpactListener(
                listener2
            )

            // playfield.addCollisionListener(listener)

            m.sinucasNew.add(sparklySinuca)

            // val another = playfield.spawnCircle(5f, 3f, 0.01f, Color.RED)
            // another.velocity = Vector2f(0.1f, 0f)

            for ((index, ball) in sparklySinuca.balls.withIndex()) {
                val ballEntity = player.world.spawn(
                    location,
                    ItemDisplay::class.java
                ) {
                    it.setItemStack(if (index == 0) ItemStack.of(Material.WHITE_CONCRETE) else ItemStack.of(Material.DIAMOND_BLOCK))
                    it.isPersistent = false
                    it.interpolationDuration = 2
                }

                sparklySinuca.ballToDisplayEntity[ball] = ballEntity
            }

            run {
                for ((ball, itemDisplay) in sparklySinuca.ballToDisplayEntity) {
                    itemDisplay.setTransformationMatrix(
                        Matrix4f()
                            .translate(
                                (ball.worldCenter.x - sparklySinuca.translationOffsetX).toFloat(),
                                0.0f,
                                (ball.worldCenter.y - sparklySinuca.translationOffsetY).toFloat(),
                            )
                            .scale(0.25f)
                    )
                }
            }

            sparklySinuca.spawnCueballHitbox()
            m.launchMainThread {
                while (true) {
                    while (true) {
                        player.spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            sparklySinuca.gameOriginLocation.clone().add(
                                -sparklySinuca.translationOffsetX.toDouble(),
                                0.0,
                                -sparklySinuca.translationOffsetY.toDouble(),
                            ),
                            1
                        )

                        player.spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            sparklySinuca.gameOriginLocation.clone().add(
                                sparklySinuca.translationOffsetX.toDouble(),
                                0.0,
                                -sparklySinuca.translationOffsetY.toDouble(),
                            ),
                            1
                        )

                        player.spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            sparklySinuca.gameOriginLocation.clone().add(
                                sparklySinuca.translationOffsetX.toDouble(),
                                0.0,
                                sparklySinuca.translationOffsetY.toDouble(),
                            ),
                            1
                        )

                        player.spawnParticle(
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
                            player.spawnParticle(
                                Particle.NOTE,
                                sparklySinuca.gameOriginLocation.clone().add(
                                    topX - sparklySinuca.translationOffsetX,
                                    0.0,
                                    topY - sparklySinuca.translationOffsetY,
                                ),
                                1
                            )

                            player.spawnParticle(
                                Particle.NOTE,
                                sparklySinuca.gameOriginLocation.clone().add(
                                    bottomX - sparklySinuca.translationOffsetX,
                                    0.0,
                                    bottomY - sparklySinuca.translationOffsetY,
                                ),
                                1
                            )
                        }

                        playfield.step(1)
                        // Bukkit.broadcastMessage("ball1mc: ${another.location.x - translationOffsetX}")

                        for ((ball, itemDisplay) in sparklySinuca.ballToDisplayEntity) {
                            // Bukkit.broadcastMessage("Are we at rest? ${ball.isAtRest} ${ball.linearVelocity}")
                            itemDisplay.setTransformationMatrix(
                                Matrix4f()
                                    .translate(
                                        (ball.worldCenter.x - sparklySinuca.translationOffsetX).toFloat(),
                                        0.0f,
                                        (ball.worldCenter.y - sparklySinuca.translationOffsetY).toFloat(),
                                    )
                                    .scale(0.125f)
                            )

                            // itemDisplay.interpolationDelay = -1
                        }

                        delayTicks(1L)
                    }
                    delayTicks(1L)
                }
            }
        }
    }
}

class SparklySinuca(
    val player: Player,
    val gameOriginLocation: Location,
    val playfieldWidth: Float,
    val playfieldHeight: Float,
    val playfield: World<Body>
) {
    companion object {
        private val  PADDING_BETWEEN_BALL = 0.12
        private val HALF_PADDING_BETWEEN_BALL = PADDING_BETWEEN_BALL / 2
    }
    val translationOffsetX = playfieldWidth / 2f
    val translationOffsetY = playfieldHeight / 2f

    // var cueBallHitbox: Interaction? = null
    // val cueball = playfield.spawnCircle(0f, 0f, 0.13f, Color.RED)

    val ballToDisplayEntity = mutableMapOf<Body, ItemDisplay>()
    val balls = mutableListOf<Body>()
    val walls = mutableListOf<Body>()

    val cueball = spawnBall(1.0, translationOffsetY.toDouble())
    // val eightBall = spawnBall(3.0, translationOffsetY.toDouble())

    // x = forward
    // y = side to side
    val otherBalls = listOf(
        spawnBall(3.0, translationOffsetY.toDouble()),

        spawnBall(3.0 + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - HALF_PADDING_BETWEEN_BALL),
        spawnBall(3.0 + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + HALF_PADDING_BETWEEN_BALL),

        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - PADDING_BETWEEN_BALL),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble()),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + PADDING_BETWEEN_BALL),

        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + HALF_PADDING_BETWEEN_BALL + HALF_PADDING_BETWEEN_BALL),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + HALF_PADDING_BETWEEN_BALL),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - HALF_PADDING_BETWEEN_BALL),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - HALF_PADDING_BETWEEN_BALL - HALF_PADDING_BETWEEN_BALL),

        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + PADDING_BETWEEN_BALL),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble()),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - PADDING_BETWEEN_BALL),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - PADDING_BETWEEN_BALL - PADDING_BETWEEN_BALL),
    )

    // val ball1 = spawnBall(3.0, 2.1)
    // val ball2 = spawnBall(3.2, 2.4)
    // val ball3 = spawnBall(3.4, 2.6)
    // val ball4 = spawnBall(3.6, 2.8)
    // val ball5 = spawnBall(3.8, 2.5)

    // Create walls
    fun spawnWall(topX: Double, topY: Double, width: Double, height: Double) {
        // We need to convert the x and y to be at the CENTER because that's what dyn4j expects
        val bottomX = topX + width
        val bottomY = topY + height
        val centerX = (topX + bottomX) / 2
        val centerY = (topY + bottomY) / 2

        val wall = Body()
        wall.addFixture(Geometry.createRectangle(width, height), 1.0, 0.4, 1.0)
            .apply {
                setRestitutionVelocity(0.0)
            }
        wall.translate(centerX, centerY)
        wall.setMass(MassType.INFINITE)
        playfield.addBody(wall)
        walls.add(wall)
    }

    // Create balls
    fun spawnBall(x: Double, y: Double): Body {
        val body = Body()
        body.addFixture(Geometry.createCircle(0.07), 217.97925, 0.08, 0.9)

        body.translate(x, y)
        body.setMass(MassType.NORMAL)
        body.setLinearDamping(1.0)
        body.setAngularDamping(1.0)
        body.setBullet(true)
        playfield.addBody(body)
        balls.add(body)
        body.isAtRestDetectionEnabled = true
        return body
    }

    init {
        // TODO: Pockets, I think we need to make the walls a bit more "inwards" to avoid the pockets being outside of the 5x3 area
        // Top
        spawnWall(0.0, -1.0, 5.0, 0.25)

        spawnWall(0.0, -1.0, 5.0, 1.0)

        // Bottom
        spawnWall(0.0, 3.0, 5.0, 1.0)

        // Left
        spawnWall(-1.0, 0.0, 1.0, 3.0)

        // Right
        spawnWall(5.0, 0.0, 1.0, 3.0)
    }

    fun spawnCueballHitbox() {
        /* cueBallHitbox?.remove()

        cueBallHitbox = player.world.spawn(gameOriginLocation.clone().add(
            (cueball.location.x - translationOffsetX).toDouble(),
            0.0,
            (cueball.location.y - translationOffsetY).toDouble(),
        ), Interaction::class.java) {
            it.interactionHeight = 0.25f
            it.interactionWidth = 0.25f
            it.isResponsive = false
            it.isPersistent = false
        } */
    }
}

// MrPowerGamerBR's quick and dirty 8 ball pool implementation written from scratch!

class Ball(
    var location: Vector2f,
    var radius: Float,
    var velocity: Vector2f,
    var color: Color
) {
    var collision = false
    var mass = 1f
    var friction = 0.95f
}

class Wall(
    var location: Vector2f,
    val dimensions: Vector2f,
    val type: WallType
)

enum class WallType {
    LEFT, RIGHT, TOP, BOTTOM
}

class Playfield(val width: Int, val height: Int) {
    companion object {
        private const val EFFECTIVELY_ZERO_EPSILON = 0.00001f
    }

    val walls = mutableListOf<Wall>()
    val balls = mutableListOf<Ball>()

    fun spawnWall(x: Float, y: Float, width: Float, height: Float, wallType: WallType): Wall {
        val wall = Wall(Vector2f(x, y), Vector2f(width, height), wallType)
        walls.add(wall)
        return wall
    }

    fun spawnCircle(x: Float, y: Float, radius: Float, color: Color): Ball {
        val ball = Ball(Vector2f(x, y), radius, Vector2f(0f, 0f), color)
        balls.add(ball)
        return ball
    }

    fun step() {
        for (element in balls) {
            element.location.add(element.velocity)

            for (collidingElement in balls) {
                if (collidingElement == element)
                    continue

                val result = detectCollision(element, collidingElement)
                if (result) {
                    println("Colliding! $result")
                    resolveCollision(element, collidingElement)
                    handleCollision(element, collidingElement)
                }

                collidingElement.collision = result
            }

            for (collidingElement in walls) {
                detectAndHandleBallToWallCollision(element, collidingElement)
            }

            element.velocity.mul(element.friction)

            println(element.velocity.x)
            println(element.velocity.y)
            if (EFFECTIVELY_ZERO_EPSILON > element.velocity.lengthSquared()) {
                element.velocity.x = 0f
                element.velocity.y = 0f
            }
        }
    }

    // Some parts are ChatGPT, but ChatGPT also provides some very stupid code that does not work, so a lot of trial and error was needed
    fun detectCollision(ballA: Ball, ballB: Ball): Boolean {
        // Calculate the distance between the two circle centers
        val distance = ballA.location.distance(ballB.location)

        // Check if the distance is less than the sum of the radii
        if (ballA.radius + ballB.radius > distance)
            return true
        else
            return false
    }

    fun resolveCollision(ball1: Ball, ball2: Ball) {
        val delta = Vector2f(ball2.location).sub(ball1.location)
        val distance = delta.length()
        val overlap = (distance - ball1.radius - ball2.radius) / 2.0f

        delta.normalize()
        ball1.location.sub(delta.mul(overlap * ball2.mass / (ball1.mass + ball2.mass)))
        delta.negate()
        ball2.location.sub(delta.mul(overlap * ball1.mass / (ball1.mass + ball2.mass)))
    }

    fun handleCollision(ball1: Ball, ball2: Ball) {
        val collisionNormal = Vector2f(ball2.location).sub(ball1.location).normalize()
        val relativeVelocity = Vector2f(ball2.velocity).sub(ball1.velocity)
        val velocityAlongNormal = relativeVelocity.dot(collisionNormal)

        if (velocityAlongNormal > 0) return

        val restitution = 1.0f
        var impulseMagnitude = -(1 + restitution) * velocityAlongNormal
        impulseMagnitude /= (1 / ball1.mass) + (1 / ball2.mass)

        val impulse = Vector2f(collisionNormal).mul(impulseMagnitude)
        ball1.velocity.sub(impulse.mul(1 / ball1.mass))
        ball2.velocity.add(impulse.mul(1 / ball2.mass))
    }

    fun detectAndHandleBallToWallCollision(ball: Ball, wall: Wall) {
        // This one we need to do everything HERE because of the ball bounce
        val ballTopX = ball.location.x - ball.radius
        val ballTopY = ball.location.y - ball.radius

        val ballBottomX = ball.location.x + ball.radius
        val ballBottomY = ball.location.y + ball.radius

        val wallTopX = wall.location.x
        val wallTopY = wall.location.y

        val wallBottomX = wall.location.x + wall.dimensions.x
        val wallBottomY = wall.location.y + wall.dimensions.y

        // Check for AABB collision
        val isColliding = ballBottomX >= wallTopX && // Ball's right is past wall's left
                ballTopX <= wallBottomX && // Ball's left is before wall's right
                ballBottomY >= wallTopY && // Ball's bottom is past wall's top
                ballTopY <= wallBottomY     // Ball's top is before wall's bottom

        if (isColliding) {
            // If we are colliding, we need to get the collision type and reflect it!

            // TODO: Bounce energy?
            when (wall.type) {
                WallType.LEFT -> {
                    ball.velocity.x = ball.velocity.x * -1
                    ball.location.x = wallBottomX + ball.radius
                }
                WallType.RIGHT -> {
                    ball.velocity.x = ball.velocity.x * -1
                    ball.location.x = wallTopX - ball.radius
                }
                WallType.TOP -> {
                    ball.velocity.y = ball.velocity.y * -1
                    ball.location.y = wallBottomY + ball.radius
                }
                WallType.BOTTOM -> {
                    ball.velocity.y = ball.velocity.y * -1
                    ball.location.y = wallTopY - ball.radius
                }
            }
        }
    }

    fun hasFinishedPhysicsSimulation(): Boolean {
        return balls.all { it.velocity == Vector2f(0f, 0f) }
    }
}

fun main() {
    val playfield = Playfield(320, 240)
    playfield.spawnWall(150f, -16f, playfield.width.toFloat(), 100f, WallType.RIGHT)
    playfield.spawnWall(0f, -16f, playfield.width.toFloat(), 16f, WallType.TOP)
    val ball1 = playfield.spawnCircle(16.0f, 24.0f, 16.0f, Color.RED)
    ball1.velocity = Vector2f(4f, -1f)

    val ball2 = playfield.spawnCircle(64.0f, 18.0f, 16.0f, Color.GREEN)

    val ball3 = playfield.spawnCircle(81.0f, 24.0f, 16.0f, Color.GREEN)

    while (true) {
        val bufferedImage = BufferedImage(playfield.width, playfield.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = bufferedImage.createGraphics()
        val random = Random()
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, 320, 240)

        for (element in playfield.walls) {
            graphics.color = Color.ORANGE
            graphics.fillRect(element.location.x.toInt(), element.location.y.toInt(), element.dimensions.x.toInt(), element.dimensions.y.toInt())
        }

        for (element in playfield.balls) {
            if (element.collision) {
                graphics.color = Color.BLUE
            } else {
                graphics.color = element.color
            }
            // Draw at the CENTER of the ball, the coordinates are at the CENTER

            val topX = (element.location.x - element.radius).toInt()
            val topY = (element.location.y - element.radius).toInt()

            // fillOval requires the WIDTH AND HEIGHT, not radius!
            graphics.fillOval(topX, topY, element.radius.toInt() * 2, element.radius.toInt() * 2)
        }

        playfield.step()

        if (playfield.hasFinishedPhysicsSimulation()) {
            ball1.velocity = Vector2f(8f, 0f)
            println("Finished physics simulation!")
        }

        // 20 ticks
        Thread.sleep(1000 / 20)
    }
}