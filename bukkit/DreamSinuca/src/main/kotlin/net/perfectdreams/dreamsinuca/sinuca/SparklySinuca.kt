package net.perfectdreams.dreamsinuca.sinuca

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.perfectdreams.dreamcore.utils.DreamUtils
import net.perfectdreams.dreamcore.utils.InstantFirework
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamsinuca.DreamSinuca
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.contact.Contact
import org.dyn4j.geometry.AABB
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.world.ContactCollisionData
import org.dyn4j.world.World
import org.dyn4j.world.listener.ContactListenerAdapter
import org.joml.Matrix4f

class SparklySinuca(
    val m: DreamSinuca,
    val poolTable: PoolTable,
    val player1: Player,
    val player2: Player,
    val gameOriginLocation: Location,
    val orientation: PoolTableOrientation,
    // The playfieldWidth and playfieldHeight ALWAYS represent the actual game board
    // This SHOULD NOT change based on the pool table orientation
    val playfieldWidth: Float,
    val playfieldHeight: Float,
    val playfield: World<Body>,
) {
    companion object {
        private const val PADDING_BETWEEN_BALL = 0.14
        private const val HALF_PADDING_BETWEEN_BALL = PADDING_BETWEEN_BALL / 2
        private const val BALL_RADIUS = 0.0625
    }

    // This is the translation offset in the Minecraft world
    val worldTranslationOffsetX = playfieldWidth / 2f
    val worldTranslationOffsetY = playfieldHeight / 2f

    // THE TOP SHOULD ALWAYS BE ON THE BOTTOM LEFT SIDE

    // This is the topX/topY of the table in the world
    val worldTopX = when (orientation) {
        PoolTableOrientation.EAST -> gameOriginLocation.x - worldTranslationOffsetX
        PoolTableOrientation.SOUTH -> gameOriginLocation.x + worldTranslationOffsetY
    }
    val worldTopY = when (orientation) {
        PoolTableOrientation.EAST -> gameOriginLocation.z - worldTranslationOffsetY
        PoolTableOrientation.SOUTH -> gameOriginLocation.z - worldTranslationOffsetX
    }

    val translationOffsetX = playfieldWidth / 2f
    val translationOffsetY = playfieldHeight / 2f

    val turns = mutableListOf(player1, player2)

    /**
     * If true, then the game is still running
     */
    var isPlaying = true

    /**
     * What's the player that is currently playing right now
     */
    val playerTurn: Player
        get() = turns.first()

    var gamePhase: GamePhase = GamePhase.BreakingOut()

    /**
     * The current round
     */
    var round = 1

    /**
     * If true, then the current turn is waiting for the world physics to finish processing
     */
    var isWaitingForPhysicsToEnd = false

    /**
     * If the cueball is in hand, allowing the player to reposition it
     */
    var cueballInHand = false
    var lastCueballInHandTick: Int? = null

    // var cueBallHitbox: Interaction? = null
    // val cueball = playfield.spawnCircle(0f, 0f, 0.13f, Color.RED)

    val ballToDisplayEntity = mutableMapOf<Ball, ItemDisplay>()

    val pockets = mutableListOf<Body>()
    val walls = mutableListOf<Body>()
    val balls = mutableListOf<Ball>()

    val cueball = spawnBall(1.0, translationOffsetY.toDouble(), BallType.CUE)
    // val cueball = spawnBall(0.0, 0.0, BallType.CUE)
    val eightball = spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble(), BallType.EIGHT_BALL)
    // val eightBall = spawnBall(3.0, translationOffsetY.toDouble())

    // To make it easier for us to reason about this, we'll create a "step collisions"
    val currentStepCollisions = mutableListOf<StepCollision>()

    val remainingTypes = buildList {
        repeat(7) {
            add(BallType.RED)
        }

        repeat(7) {
            add(BallType.BLUE)
        }
    }.shuffled().toMutableList()

    val ballToMatrix4f = mutableMapOf<Ball, Matrix4f>()

    // x = forward
    // y = side to side
    val otherBalls = listOf(
        spawnBall(3.0, translationOffsetY.toDouble(), remainingTypes.removeFirst()),

        spawnBall(3.0 + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - HALF_PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),
        spawnBall(3.0 + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + HALF_PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),

        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),
        eightball,
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),

        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - HALF_PADDING_BETWEEN_BALL - PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - HALF_PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + HALF_PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + HALF_PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),

        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() + PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble(), remainingTypes.removeFirst()),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),
        spawnBall(3.0 + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL + PADDING_BETWEEN_BALL, translationOffsetY.toDouble() - PADDING_BETWEEN_BALL - PADDING_BETWEEN_BALL, remainingTypes.removeFirst()),
    )

    val gameStatusDisplay = gameOriginLocation.world.spawn(
        gameOriginLocation.clone().add(0.0, 2.0, 0.0),
        TextDisplay::class.java
    ) {
        it.isPersistent = false
        it.billboard = Display.Billboard.VERTICAL
        it.lineWidth = Int.MAX_VALUE
        it.isShadowed = true
    }

    var previewBallTrajectory = true

    /**
     * How many ticks have elapsed on this round
     */
    var roundElapsedTicks = 0

    fun updateGameStatusDisplay() {
        val s = (1200 - this.roundElapsedTicks)

        val currentRound = textComponent {
            color(NamedTextColor.GOLD)
            decorate(TextDecoration.BOLD)
            content("Round $round")
        }

        val currentTimeStatus = if (isWaitingForPhysicsToEnd) {
            textComponent {
                color(NamedTextColor.LIGHT_PURPLE)
                appendTextComponent {
                    decorate(TextDecoration.BOLD)
                    content("Bolas rolando...")
                }
            }
        } else {
            textComponent {
                color(NamedTextColor.LIGHT_PURPLE)
                appendTextComponent {
                    decorate(TextDecoration.BOLD)
                    content("Tempo Restante: ")
                }

                appendTextComponent {
                    content("${s / 20}s")
                }
            }
        }

        when (val gamePhase = gamePhase) {
            is GamePhase.BreakingOut, is GamePhase.DecidingTeams -> {
                gameStatusDisplay.text(
                    textComponent {
                        append(currentRound)
                        appendNewline()
                        append(currentTimeStatus)
                        appendNewline()
                        appendTextComponent {
                            if (playerTurn == player1) {
                                appendTextComponent {
                                    color(NamedTextColor.GOLD)
                                    content("\uD83E\uDC0A ")
                                }
                            }
                            appendTextComponent {
                                content(player1.name)
                            }
                            if (playerTurn == player1) {
                                appendTextComponent {
                                    color(NamedTextColor.GOLD)
                                    content(" \uD83E\uDC08")
                                }
                            }
                        }
                        appendNewline()
                        appendTextComponent {
                            if (playerTurn == player2) {
                                appendTextComponent {
                                    color(NamedTextColor.GOLD)
                                    content("\uD83E\uDC0A ")
                                }
                            }
                            appendTextComponent {
                                content(player2.name)
                            }
                            if (playerTurn == player2) {
                                appendTextComponent {
                                    color(NamedTextColor.GOLD)
                                    content(" \uD83E\uDC08")
                                }
                            }
                        }
                    }
                )
            }

            is GamePhase.StrikeDown -> {
                gameStatusDisplay.text(
                    textComponent {
                        append(currentRound)
                        appendNewline()
                        append(currentTimeStatus)
                        appendNewline()

                        appendTextComponent {
                            if (playerTurn == player1) {
                                appendTextComponent {
                                    color(NamedTextColor.GOLD)
                                    content("\uD83E\uDC0A ")
                                }
                            }
                            appendTextComponent {
                                content(player1.name)
                                when (gamePhase.player1Team) {
                                    BallType.CUE -> TODO()
                                    BallType.EIGHT_BALL -> TODO()
                                    BallType.BLUE -> color(NamedTextColor.AQUA)
                                    BallType.RED -> color(NamedTextColor.RED)
                                }

                                val totalBallsOfMyTeam = balls.filter { it.type == gamePhase.player1Team }
                                    .sortedByDescending { it.pocketed }

                                append(" ")

                                for (ball in totalBallsOfMyTeam) {
                                    if (ball.pocketed) {
                                        append("⬤")
                                    } else {
                                        append("◯")
                                    }
                                }
                            }
                            if (playerTurn == player1) {
                                appendTextComponent {
                                    color(NamedTextColor.GOLD)
                                    content(" \uD83E\uDC08")
                                }
                            }
                        }

                        appendNewline()

                        appendTextComponent {
                            if (playerTurn == player2) {
                                appendTextComponent {
                                    color(NamedTextColor.GOLD)
                                    content("\uD83E\uDC0A ")
                                }
                            }
                            appendTextComponent {
                                content(player2.name)
                                when (gamePhase.player2Team) {
                                    BallType.CUE -> TODO()
                                    BallType.EIGHT_BALL -> TODO()
                                    BallType.BLUE -> color(NamedTextColor.AQUA)
                                    BallType.RED -> color(NamedTextColor.RED)
                                }

                                val totalBallsOfMyTeam = balls.filter { it.type == gamePhase.player2Team }
                                    .sortedByDescending { it.pocketed }

                                append(" ")

                                for (ball in totalBallsOfMyTeam) {
                                    if (ball.pocketed) {
                                        append("⬤")
                                    } else {
                                        append("◯")
                                    }
                                }
                            }
                            if (playerTurn == player2) {
                                appendTextComponent {
                                    color(NamedTextColor.GOLD)
                                    content(" \uD83E\uDC08")
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // Create walls
    fun spawnWall(topX: Double, topY: Double, width: Double, height: Double) {
        // TODO: How to improve physics? Maybe increate the restitution on balls, decrease on walls?
        // Restitution: Speed decrease after bounce, higher values = more bouncy (up to 1)

        // We need to convert the x and y to be at the CENTER because that's what dyn4j expects
        val bottomX = topX + width
        val bottomY = topY + height
        val centerX = (topX + bottomX) / 2
        val centerY = (topY + bottomY) / 2

        val wall = Body()
        wall.userData = "wall"
        val fixture = wall.addFixture(Geometry.createRectangle(width, height), 1.0, 0.4, 0.3)
        fixture.restitutionVelocity = 0.0
        wall.translate(centerX, centerY)
        wall.setMass(MassType.INFINITE)
        wall.isAtRestDetectionEnabled = false
        wall.isAtRest = true
        playfield.addBody(wall)
        walls.add(wall)
    }

    fun spawnWallFromTo(topX: Double, topY: Double, bottomX: Double, bottomY: Double) {
        val width = bottomX - topX
        val height = bottomY - topY

        spawnWall(topX, topY, width, height)
    }

    // Create pockets
    fun spawnPocket(topX: Double, topY: Double, width: Double, height: Double) {
        // We need to convert the x and y to be at the CENTER because that's what dyn4j expects
        val bottomX = topX + width
        val bottomY = topY + height
        val centerX = (topX + bottomX) / 2
        val centerY = (topY + bottomY) / 2

        val wall = Body()
        wall.userData = "pocket"
        wall.addFixture(Geometry.createRectangle(width, height), 217.97925, 0.08, 0.9)
            .apply {
                setRestitutionVelocity(0.0)
                isSensor = true
            }
        wall.translate(centerX, centerY)
        wall.setMass(MassType.NORMAL)
        wall.isAtRestDetectionEnabled = false
        wall.isAtRest = true
        playfield.addBody(wall)
        pockets.add(wall)
    }

    // Create balls
    fun spawnBall(x: Double, y: Double, type: BallType): Ball {
        val body = Body()
        body.userData = "ball"
        val fixture = body.addFixture(Geometry.createCircle(BALL_RADIUS), 217.97925, 0.08, 0.9)
        fixture.restitutionVelocity = 0.001
        body.translate(x, y)
        body.setMass(MassType.NORMAL)
        body.setLinearDamping(0.8)
        body.setAngularDamping(0.8)
        body.isBullet = true
        playfield.addBody(body)
        val ball = Ball(this, body, type)
        balls.add(ball)
        body.isAtRestDetectionEnabled = true
        return ball
    }

    init {
        // Bukkit.broadcastMessage("Top: $worldTopX; $worldTopY")
        //Bukkit.broadcastMessage("Bottom: $worldBottomX; $worldBottomY")
        spawnWall(0.0, 0.25, 0.25, 1.25) // border
        spawnWall(0.0, 2.75, 5.0, 0.25) // borderbottom
        spawnWall(2.625, 2.5, 1.6749999999999998, 0.25) // border
        spawnWall(0.7000000000000002, 2.5, 1.6749999999999998, 0.25) // border
        spawnWall(0.7000000000000002, 0.25, 1.6749999999999998, 0.25) // border
        spawnWall(4.75, 0.25, 0.25, 1.25) // border
        spawnWall(0.0, 0.0, 5.0, 0.25) // bordertop
        spawnWall(2.625, 0.25, 1.6749999999999998, 0.25) // border
        spawnWall(4.75, 1.5, 0.25, 1.25) // border
        spawnWall(4.5, 0.7000000000000002, 0.25, 0.7999999999999998) // border
        spawnWall(0.25, 0.7000000000000002, 0.25, 0.7999999999999998) // border
        spawnWall(4.5, 1.5, 0.25, 0.7999999999999998) // border
        spawnWall(4.75, 1.5, 0.25, 1.25) // border
        spawnWall(4.75, 0.25, 0.25, 1.25) // border
        spawnWall(0.25, 1.5, 0.25, 0.7999999999999998) // border
        spawnWall(0.0, 1.5, 0.25, 1.25) // border
        spawnPocket(2.375, 0.25, 0.25, 0.25) // pocket1
        spawnPocket(2.375, 2.5, 0.25, 0.25) // pocket2
        spawnPocket(0.44999999999999996, 2.5, 0.2500000000000002, 0.25) // pocket3
        spawnPocket(0.24999999999999978, 2.3000000000000007, 0.2500000000000011, 0.25) // pocket4
        spawnPocket(0.24999999999999978, 0.4500000000000064, 0.2500000000000011, 0.2500000000000009) // pocket5
        spawnPocket(0.44999999999999996, 0.2500000000000071, 0.2500000000000002, 0.2500000000000009) // pocket5
        spawnPocket(4.3, 0.2500000000000071, 0.25, 0.2500000000000009) // pocket6
        spawnPocket(4.499999999999999, 0.4500000000000064, 0.25, 0.2500000000000009) // pocket7
        spawnPocket(4.499999999999999, 2.3000000000000025, 0.25, 0.2500000000000009) // pocket8
        spawnPocket(4.3, 2.5000000000000018, 0.25, 0.2500000000000009) // pocket8

        playfield.addContactListener(object: ContactListenerAdapter<Body>() {
            override fun begin(collision: ContactCollisionData<Body>, contact: Contact) {
                // Bukkit.broadcastMessage("begin!")

                // Before we used TimeOfImpact listeners:
                //   This seems to be our "bread and butter" to listen for impacts
                //   I don't know the difference between the collision listener and this however...
                // But TimeOfImpact does not include sensors collisions?!
                currentStepCollisions.add(StepCollision(collision.body1, collision.body2, contact.point, contact.depth))
            }
        })
    }

    val ballTrajectoryEntities = mutableListOf<ItemDisplay>()

    /**
     * Starts a new task and starts previewing the ball line trajectory if [previewBallTrajectory] is true
     */
    fun startBallTrajectoryPreviewTask() {
        m.launchMainThread {
            while (isPlaying) {
                if (previewBallTrajectory) {
                    val gamePhase = gamePhase

                    val checkTeam = if (gamePhase is GamePhase.StrikeDown) {
                        val team = if (playerTurn == player1) {
                            gamePhase.player1Team
                        } else {
                            gamePhase.player2Team
                        }

                        val pocketedAllBalls = balls.filter { it.type == team }.all { it.pocketed }

                        if (pocketedAllBalls) {
                            BallType.EIGHT_BALL
                        } else
                            team
                    } else {
                        null
                    }

                    val usedEntities = mutableListOf<ItemDisplay>()
                    val playerLocationStraight = playerTurn.location.apply {
                        this.pitch = 0f

                        if (orientation == PoolTableOrientation.SOUTH) {
                            this.yaw -= 90f
                        }
                    }

                    var isTargetingValid = true

                    trajectoryLoop@ for (it in 0 until 50) {
                        val velocity = playerLocationStraight.direction.multiply(it * 0.08)
                        val gameX = cueball.body.worldCenter.x + velocity.x
                        val gameZ = cueball.body.worldCenter.y + velocity.z

                        for (body in playfield.bodies) {
                            if (body == cueball.body)
                                continue

                            val aabb = AABB(gameX - BALL_RADIUS, gameZ - BALL_RADIUS, gameX + BALL_RADIUS, gameZ + BALL_RADIUS)
                            val bodyAABB = body.createAABB()

                            if (aabb.overlaps(bodyAABB)) {
                                val ballType = balls.firstOrNull { it.body == body }
                                if (ballType != null && checkTeam != null && ballType.type != checkTeam) {
                                    isTargetingValid = false
                                }
                                break@trajectoryLoop
                            }
                        }

                        // We use display entities because while particles would be cool, we can't control the duration of them (sadly)
                        val pointEntity = ballTrajectoryEntities.getOrNull(it)

                        val location = gameOriginLocation.clone()

                        when (orientation) {
                            PoolTableOrientation.EAST -> {
                                location.add(
                                    gameX - worldTranslationOffsetX,
                                    0.0,
                                    gameZ - worldTranslationOffsetY
                                )
                            }
                            //  (worldTranslationOffsetY - ball.body.worldCenter.y).toFloat(),
                            //                                0.0f,
                            //                                (ball.body.worldCenter.x - worldTranslationOffsetX).toFloat(),
                            PoolTableOrientation.SOUTH -> {
                                location.add(
                                    worldTranslationOffsetY - gameZ,
                                    0.0,
                                    gameX - worldTranslationOffsetX
                                )
                            }
                        }

                        val currentEntity = if (pointEntity == null) {
                            val entity = gameOriginLocation.world.spawn(
                                location,
                                ItemDisplay::class.java
                            ) {
                                it.isPersistent = false
                                it.setTransformationMatrix(
                                    Matrix4f().scale(0.05f)
                                )
                                it.teleportDuration = 1
                                // We set the ItemStack later
                            }

                            ballTrajectoryEntities.add(entity)

                            entity
                        } else {
                            pointEntity.teleport(location)
                            pointEntity
                        }

                        usedEntities.add(currentEntity)
                    }

                    val unusedEntities = ballTrajectoryEntities.filter { it !in usedEntities }
                    for (unusedEntity in unusedEntities) {
                        unusedEntity.remove()
                        ballTrajectoryEntities.remove(unusedEntity)
                    }

                    val itemStack = if (isTargetingValid) {
                        ItemStack.of(Material.SNOW_BLOCK)
                    } else {
                        ItemStack.of(Material.REDSTONE_BLOCK)
                    }

                    for (entity in ballTrajectoryEntities) {
                        entity.setItemStack(itemStack)
                    }
                } else {
                    for (entity in ballTrajectoryEntities) {
                        entity.remove()
                    }

                    ballTrajectoryEntities.clear()
                }

                delayTicks(1L)
            }
        }
    }

    fun calculateCueballCoordinates(worldPosX: Double, worldPosZ: Double): Pair<Double, Double> {
        // Calculating the relative position according to the raytraced value is a bit tricky due to the different orientations
        val cueballX: Double
        val cueballY: Double

        when (orientation) {
            PoolTableOrientation.EAST -> {
                cueballX = worldPosX - worldTopX
                cueballY = worldPosZ - worldTopY
            }
            PoolTableOrientation.SOUTH -> {
                cueballX = worldPosZ - worldTopY
                cueballY = worldTopX - worldPosX
            }
        }

        return Pair(cueballX, cueballY)
    }

    /**
     * Previews the cueball position if [cueballInHand] is true
     *
     * We use this instead of [PlayerMoveEvent] because [PlayerMoveEvent] does not call every time the player moves their head, which makes it look a bit wonky
     */
    fun startCueballPreviewTask() {
        m.launchMainThread {
            loop@while (isPlaying) {
                if (cueballInHand) {
                    run {
                        val raytraceResult = playerTurn.rayTraceBlocks(7.0) ?: return@run // Couldn't ray trace, abort!
                        val position = raytraceResult.hitPosition

                        // Calculating the relative position according to the raytraced value is a bit tricky due to the different orientations
                        val (cueballX, cueballY) = calculateCueballCoordinates(position.x, position.z)

                        // Bukkit.broadcastMessage("Top: $worldTopX; $worldTopY; CueballX: $cueballX - CueballY: $cueballY")

                        if (!checkIfCoordinateIsInsidePlayfield(cueballX.toFloat(), cueballY.toFloat())) {
                            // If outside of playfield, just ignore it and don't attempt to translate
                            return@run
                        }

                        // Unpocket that thang
                        // We need to calculate where are we looking by using *raytracing* (omg)
                        cueball.body.translateToOrigin()

                        // Bukkit.broadcastMessage("Translating to $cueballX, $cueballY")

                        cueball.body.translate(cueballX, cueballY)

                        val cueballAABB = cueball.body.createAABB()
                        var isValidPosition = true

                        for (body in playfield.bodies) {
                            if (body == cueball.body)
                                continue

                            val bodyAABB = body.createAABB()

                            if (cueballAABB.overlaps(bodyAABB)) {
                                ballToDisplayEntity[this@SparklySinuca.cueball]?.setItemStack(ItemStack.of(Material.REDSTONE_BLOCK))
                                isValidPosition = false
                                break
                            }
                        }

                        if (isValidPosition) {
                            ballToDisplayEntity[this@SparklySinuca.cueball]?.setItemStack(ItemStack.of(Material.WHITE_CONCRETE))
                        } else {
                            ballToDisplayEntity[this@SparklySinuca.cueball]?.setItemStack(ItemStack.of(Material.REDSTONE_BLOCK))
                        }

                        updateGameObjects()
                    }
                }

                if (!isWaitingForPhysicsToEnd) {
                    val s = (1200 - roundElapsedTicks)

                    // peter pulls the plug: well that's annoying
                    /* if (roundElapsedTicks % 20 == 0) {
                        playerTurn.playSound(playerTurn.location, Sound.UI_BUTTON_CLICK, 0.4f, 1f)
                    } */

                    if (300 >= s) {
                        if (s % 20 == 0) {
                            playerTurn.playSound(playerTurn.location, Sound.UI_BUTTON_CLICK, 0.4f, 1f)
                        }
                    }

                    if (s == 0) {
                        // Bye, it's over
                        val winner = if (this@SparklySinuca.playerTurn == this@SparklySinuca.player1) {
                            this@SparklySinuca.player2
                        } else {
                            this@SparklySinuca.player1
                        }

                        poolTable.sendSinucaMessageToPlayersNearIt(
                            textComponent {
                                color(NamedTextColor.GREEN)

                                appendTextComponent {
                                    content("Parabéns ")
                                }

                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(winner.name)
                                }

                                appendTextComponent {
                                    content("! Você venceu a sinuca pois parece que ")
                                }

                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(this@SparklySinuca.playerTurn.name)
                                }

                                appendTextComponent {
                                    content(" dormiu no teclado...")
                                }
                            }
                        )

                        poolTable.cancelActive8BallPool()
                        return@launchMainThread
                    }

                    updateGameStatusDisplay()

                    roundElapsedTicks++
                }

                delayTicks(1L)
            }
        }
    }

    /**
     * Starts a new task and starts processing the sinuca's physics, the game should wait this task to process any other tasks related to the game!
     */
    fun processPhysics() {
        this.isWaitingForPhysicsToEnd = true
        this.previewBallTrajectory = false

        m.launchMainThread {
            var firstHitWasSelfBall: Boolean? = null
            var pocketedSelfBall = false
            val startingGamePhase = this@SparklySinuca.gamePhase

            val isOnLastPhase = if (startingGamePhase is GamePhase.StrikeDown) {
                val currentTeamBeforeStart = if (playerTurn == player1) {
                    startingGamePhase.player1Team
                } else {
                    startingGamePhase.player2Team
                }

                balls.filter { it.type == currentTeamBeforeStart }.all { it.pocketed }
            } else {
                false
            }
            var ticks = 0

            while (isPlaying) {
                // val start = System.nanoTime()
                // val startStep: Long
                // val endStep: Long

                val sparklySinuca = this@SparklySinuca

                var finishedProcessingAllBalls = true
                for (ball in balls) {
                    // We only care about the at rest status of balls
                    // (because hitting a wall causes it to not be at rest and it never reverts to at rest?!)
                    if (playfield.containsBody(ball.body)) {
                        // The ball is still in the game!
                        if (!ball.body.isAtRest) {
                            // Haven't finished yet... sad
                            finishedProcessingAllBalls = false

                            if (ticks >= 200) {
                                finishedProcessingAllBalls = true
                                m.logger.info("More than 200 ticks have elapsed and ${ball.body.userData} is not in rest! Bailing physics processing... ${ball.body.linearVelocity} ${ball.body.angularVelocity}")
                            }
                            break
                        }
                    }
                }

                if (finishedProcessingAllBalls) {
                    sparklySinuca.round++
                    sparklySinuca.roundElapsedTicks = 0
                    sparklySinuca.isWaitingForPhysicsToEnd = false

                    poolTable.sendSinucaMessageToPlayersNearIt(
                        textComponent {
                            color(NamedTextColor.GRAY)

                            appendTextComponent {
                                content("Round encerrado! ($ticks ticks)")
                            }
                        }
                    )

                    if (sparklySinuca.gamePhase is GamePhase.BreakingOut) {
                        val didAnyTeamBallBrokeOut = balls.any { it.pocketed && (it.type == BallType.RED || it.type == BallType.BLUE) }
                        if (!didAnyTeamBallBrokeOut) {
                            sparklySinuca.gamePhase = GamePhase.DecidingTeams()
                        }
                    }

                    // While there is a bit of temptation to add a "updateGameStatusDisplay()" here, it is better to update the display only after processing everything
                    // To avoid the display showing outdated info (even if for only one tick)

                    // Uh oh, the eightball was pocketed!
                    if (sparklySinuca.eightball.pocketed) {
                        when (val gamePhase = sparklySinuca.gamePhase) {
                            is GamePhase.BreakingOut -> {
                                poolTable.sendSinucaMessageToPlayersNearIt(
                                    textComponent {
                                        color(NamedTextColor.YELLOW)

                                        appendTextComponent {
                                            content("O jogo acabou em empate, pois a bola 8 foi encaçapada durante a fase de break!")
                                        }
                                    }
                                )

                                poolTable.cancelActive8BallPool()
                                return@launchMainThread
                            }

                            is GamePhase.DecidingTeams -> {
                                val winner = if (sparklySinuca.playerTurn == sparklySinuca.player1) {
                                    sparklySinuca.player2
                                } else {
                                    sparklySinuca.player1
                                }

                                poolTable.sendSinucaMessageToPlayersNearIt(
                                    textComponent {
                                        color(NamedTextColor.GREEN)

                                        appendTextComponent {
                                            content("Parabéns ")
                                        }

                                        appendTextComponent {
                                            color(NamedTextColor.AQUA)
                                            content(winner.name)
                                        }

                                        appendTextComponent {
                                            content("! Você venceu a sinuca!")
                                        }
                                    }
                                )

                                poolTable.cancelActive8BallPool()
                                return@launchMainThread
                            }

                            is GamePhase.StrikeDown -> {
                                // Did we collect all 7 dragon balls? heh
                                val currentTeam = if (sparklySinuca.playerTurn == sparklySinuca.player1) {
                                    gamePhase.player1Team
                                } else {
                                    gamePhase.player2Team
                                }

                                val pocketedAllBalls = balls.filter { it.type == currentTeam }
                                    .all { it.pocketed }

                                val winner: Player
                                val loser: Player

                                // If you pocket both the eight ball AND the cue ball, you lose
                                if (pocketedAllBalls && !sparklySinuca.cueball.pocketed) {
                                    winner = sparklySinuca.playerTurn
                                    loser = turns.get(1)
                                } else {
                                    winner = turns.get(1)
                                    loser = sparklySinuca.playerTurn
                                }

                                poolTable.sendSinucaMessageToPlayersNearIt(
                                    textComponent {
                                        color(NamedTextColor.GREEN)

                                        appendTextComponent {
                                            content("Parabéns ")
                                        }

                                        appendTextComponent {
                                            color(NamedTextColor.AQUA)
                                            content(winner.name)
                                        }

                                        appendTextComponent {
                                            content("! Você venceu a partida de sinuca!")
                                        }
                                    }
                                )

                                val r = DreamUtils.random.nextInt(0, 256)
                                val g = DreamUtils.random.nextInt(0, 256)
                                val b = DreamUtils.random.nextInt(0, 256)

                                val fadeR = Math.max(0, r - 60)
                                val fadeG = Math.max(0, g - 60)
                                val fadeB = Math.max(0, b - 60)

                                val fireworkEffect = FireworkEffect.builder()
                                    .withTrail()
                                    .withColor(Color.fromRGB(r, g, b))
                                    .withFade(Color.fromRGB(fadeR, fadeG, fadeB))
                                    .with(FireworkEffect.Type.values()[DreamUtils.random.nextInt(0, FireworkEffect.Type.values().size)])
                                    .build()

                                InstantFirework.spawn(
                                    winner.location,
                                    fireworkEffect
                                )

                                poolTable.cancelActive8BallPool()
                                return@launchMainThread
                            }
                        }
                    }

                    // Now, we have a bunch of checks that we NEED to do before continuing...
                    if (sparklySinuca.cueball.pocketed) {
                        // If the cueball was pocketed, the next player can move the cueball
                        sparklySinuca.previewBallTrajectory = false
                        sparklySinuca.cueballInHand = true

                        sparklySinuca.turns.add(sparklySinuca.turns.removeFirst())

                        playerTurn.playSound(
                            playerTurn.location,
                            "minecraft:entity.player.levelup",
                            1f,
                            2f
                        )

                        poolTable.sendSinucaMessageToPlayersNearIt(
                            textComponent {
                                color(NamedTextColor.YELLOW)

                                appendTextComponent {
                                    content("A bola branca foi encaçapada! ")
                                }

                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(sparklySinuca.playerTurn.name)
                                }

                                appendTextComponent {
                                    content(" está com ela na mão e pode colocar onde quiser!")
                                }
                            }
                        )
                        updateGameStatusDisplay()
                        return@launchMainThread
                    } else if (startingGamePhase is GamePhase.StrikeDown && (firstHitWasSelfBall == null || firstHitWasSelfBall == false)) {
                        sparklySinuca.cueball.markAsPocketed()
                        sparklySinuca.previewBallTrajectory = false
                        sparklySinuca.cueballInHand = true

                        val faultPlayer = playerTurn
                        sparklySinuca.turns.add(sparklySinuca.turns.removeFirst())

                        playerTurn.playSound(
                            playerTurn.location,
                            "minecraft:entity.player.levelup",
                            1f,
                            2f
                        )

                        poolTable.sendSinucaMessageToPlayersNearIt(
                            textComponent {
                                color(NamedTextColor.YELLOW)

                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(faultPlayer.name)
                                }

                                appendTextComponent {
                                    content(" cometeu uma falta! ")
                                }

                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(sparklySinuca.playerTurn.name)
                                }

                                appendTextComponent {
                                    content(" está com ela na mão e pode colocar onde quiser!")
                                }
                            }
                        )
                        updateGameStatusDisplay()
                        return@launchMainThread
                    } else if (pocketedSelfBall) {
                        // If we pocketed the self ball, then we let the current player continue
                        sparklySinuca.previewBallTrajectory = true

                        playerTurn.playSound(
                            playerTurn.location,
                            "minecraft:entity.player.levelup",
                            1f,
                            2f
                        )

                        poolTable.sendSinucaMessageToPlayersNearIt(
                            textComponent {
                                color(NamedTextColor.YELLOW)

                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(sparklySinuca.playerTurn.name)
                                }

                                appendTextComponent {
                                    content(" conseguiu encaçapar uma de suas bolas, ele continuará jogando!")
                                }
                            }
                        )
                        updateGameStatusDisplay()
                        return@launchMainThread
                    } else {
                        // If nothing else, then we switch to the next turn and continue as is
                        sparklySinuca.turns.add(sparklySinuca.turns.removeFirst())

                        playerTurn.playSound(
                            playerTurn.location,
                            "minecraft:entity.player.levelup",
                            1f,
                            2f
                        )

                        sparklySinuca.previewBallTrajectory = true

                        poolTable.sendSinucaMessageToPlayersNearIt(
                            textComponent {
                                color(NamedTextColor.YELLOW)

                                appendTextComponent {
                                    content("Agora é a vez de ")
                                }

                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(sparklySinuca.playerTurn.name)
                                }

                                appendTextComponent {
                                    content("!")
                                }
                            }
                        )
                        updateGameStatusDisplay()
                        return@launchMainThread
                    }
                } else {
                    // playfield.bodies.filter { !it.isAtRest }.forEach {
                    //      Bukkit.broadcastMessage("Body ${it.userData} ${it.isAtRest} ${it.linearVelocity} ${it.angularVelocity}")
                    // }
                }

                // startStep = System.nanoTime()
                playfield.step(1)
                // endStep = System.nanoTime()


                // Bukkit.broadcastMessage("Cueball Torque: ${cueball.body.torque}")
                // Bukkit.broadcastMessage("ball1mc: ${another.location.x - translationOffsetX}")

                updateGameObjects()

                for (stepCollision in currentStepCollisions) {
                    val ballBody = sparklySinuca.balls.map { it.body }.toSet()
                    val pocketBody = sparklySinuca.pockets

                    val body1 = stepCollision.body1
                    val body2 = stepCollision.body2

                    if (ballBody.contains(body1) && sparklySinuca.walls.contains(body2)) {
                        // Bukkit.broadcastMessage("ball bouncy! depth is ${stepCollision.depth}")
                        val x: Double
                        val z: Double

                        when (orientation) {
                            PoolTableOrientation.EAST -> {
                                x = (stepCollision.point.x - sparklySinuca.worldTranslationOffsetX)
                                z = (stepCollision.point.y - sparklySinuca.worldTranslationOffsetY)
                            }
                            PoolTableOrientation.SOUTH -> {
                                x = (sparklySinuca.worldTranslationOffsetY - stepCollision.point.y)
                                z = (stepCollision.point.x - sparklySinuca.worldTranslationOffsetX)
                            }
                        }

                        sparklySinuca.gameOriginLocation.world.playSound(
                            sparklySinuca.gameOriginLocation.clone().add(x, 0.0, z),
                            "sparklypower:snooker.ball_wall",
                            1f,
                            DreamUtils.random.nextFloat(0.98f, 1.02f)
                        )
                    }

                    if (ballBody.contains(body1) && ballBody.contains(body2)) {
                        // Bukkit.broadcastMessage("ball bouncy! depth is ${stepCollision.depth}")
                        val x: Double
                        val z: Double

                        when (orientation) {
                            PoolTableOrientation.EAST -> {
                                x = (stepCollision.point.x - sparklySinuca.worldTranslationOffsetX)
                                z = (stepCollision.point.y - sparklySinuca.worldTranslationOffsetY)
                            }
                            PoolTableOrientation.SOUTH -> {
                                x = (sparklySinuca.worldTranslationOffsetY - stepCollision.point.y)
                                z = (stepCollision.point.x - sparklySinuca.worldTranslationOffsetX)
                            }
                        }

                        sparklySinuca.gameOriginLocation.world.playSound(
                            sparklySinuca.gameOriginLocation.clone().add(x, 0.0, z),
                            "sparklypower:snooker.ball_clack",
                            1f,
                            DreamUtils.random.nextFloat(0.98f, 1.02f)
                        )

                        // We only care about the STARTING game phase
                        if (startingGamePhase is GamePhase.StrikeDown) {
                            // We check for the body2 because the body2 is the one that gets striked by the cueball
                            val ball1 = sparklySinuca.balls.first { it.body == body1 }
                            val ball2 = sparklySinuca.balls.first { it.body == body2 }

                            if (ball1 == sparklySinuca.cueball) {
                                val currentTeam = if (sparklySinuca.playerTurn == sparklySinuca.player1) {
                                    startingGamePhase.player1Team
                                } else startingGamePhase.player2Team

                                if (firstHitWasSelfBall == null) {
                                    val currentBallTeam = if (isOnLastPhase)
                                        BallType.EIGHT_BALL
                                    else
                                        currentTeam

                                    if (ball2.type != currentBallTeam) {
                                        // Bukkit.broadcastMessage("Primeira bola acertada não foi uma bola do time!")

                                        // Whoops, that's a foul!
                                        firstHitWasSelfBall = false
                                    } else {
                                        // Bukkit.broadcastMessage("Primeira bola acertada foi uma bola do time!")

                                        // Phew, that's not a foul thankfully :3
                                        firstHitWasSelfBall = true
                                    }
                                }
                            }
                        }
                    }

                    if (ballBody.contains(body1) && pocketBody.contains(body2)) {
                        // Bukkit.broadcastMessage("Pocketed! #1")

                        val ball = sparklySinuca.balls.first { it.body == body1 }

                        // A same tick collision may happen
                        if (!ball.pocketed) {
                            ball.markAsPocketed()

                            val gamePhase = sparklySinuca.gamePhase
                            if (gamePhase is GamePhase.StrikeDown) {
                                val currentTeam = if (sparklySinuca.playerTurn == sparklySinuca.player1) {
                                    gamePhase.player1Team
                                } else gamePhase.player2Team

                                if (ball.type == currentTeam) {
                                    // Bukkit.broadcastMessage("Pocketou bola do time, pode continuar :3")
                                    pocketedSelfBall = true
                                }
                            }

                            val x: Double
                            val z: Double

                            when (orientation) {
                                PoolTableOrientation.EAST -> {
                                    x = (stepCollision.point.x - sparklySinuca.worldTranslationOffsetX)
                                    z = (stepCollision.point.y - sparklySinuca.worldTranslationOffsetY)
                                }
                                PoolTableOrientation.SOUTH -> {
                                    x = (sparklySinuca.worldTranslationOffsetY - stepCollision.point.y)
                                    z = (stepCollision.point.x - sparklySinuca.worldTranslationOffsetX)
                                }
                            }
                            sparklySinuca.gameOriginLocation.world.playSound(
                                sparklySinuca.gameOriginLocation.clone().add(x, 0.0, z),
                                "sparklypower:snooker.ball_pocket",
                                1f,
                                DreamUtils.random.nextFloat(0.98f, 1.02f)
                            )
                        }
                    }

                    if (ballBody.contains(body2) && pocketBody.contains(body1)) {
                        // Bukkit.broadcastMessage("Pocketed! #2")
                    }
                }
                currentStepCollisions.clear()

                updateGameStatusDisplay()

                // Bukkit.broadcastMessage("Took ${(System.nanoTime() - start).nanoseconds} - Step: ${(endStep - startStep).nanoseconds}")

                ticks++

                delayTicks(1L)
            }
        }
    }

    /**
     * Creates and updates the in-game objects
     */
    fun updateGameObjects() {
        for (ball in this.balls) {
            val isPocketed = ball.pocketed
            val isACueballDuringHandMoment = ball.type == BallType.CUE && this.cueballInHand

            if (isPocketed && !isACueballDuringHandMoment)
                continue

            val itemDisplay = this@SparklySinuca.ballToDisplayEntity[ball]

            // val (ballCenterX, ballCenterZ) = poolTable.getCorrectXZOrder(ball.body.worldCenter.x, ball.body.worldCenter.y)

            if (ball.type == BallType.CUE) {
                // Bukkit.broadcastMessage("Cue: ${ball.body.worldCenter.x}, ${ball.body.worldCenter.y}")
            }

            val transformationMatrix = Matrix4f()
                .apply {
                    when (orientation) {
                        PoolTableOrientation.EAST -> {
                            translate(
                                (ball.body.worldCenter.x - worldTranslationOffsetX).toFloat(),
                                0.0f,
                                (ball.body.worldCenter.y - worldTranslationOffsetY).toFloat(),
                            )
                        }
                        PoolTableOrientation.SOUTH -> {
                            translate(
                                (worldTranslationOffsetY - ball.body.worldCenter.y).toFloat(),
                                0.0f,
                                (ball.body.worldCenter.x - worldTranslationOffsetX).toFloat(),
                            )
                        }
                    }
                }
                .scale(0.125f)

            if (itemDisplay != null) {
                val currentTransform = this.ballToMatrix4f[ball]
                if (currentTransform != transformationMatrix) {
                    itemDisplay.interpolationDelay = -1
                    itemDisplay.interpolationDuration = 2
                    itemDisplay.setTransformationMatrix(transformationMatrix)
                    this.ballToMatrix4f[ball] = transformationMatrix
                }
            } else {
                val ballEntity = this.gameOriginLocation.world.spawn(
                    this.gameOriginLocation,
                    ItemDisplay::class.java
                ) {
                    it.setTransformationMatrix(transformationMatrix)
                    it.setItemStack(
                        when (ball.type) {
                            SparklySinuca.BallType.CUE -> ItemStack.of(Material.WHITE_CONCRETE)
                            SparklySinuca.BallType.EIGHT_BALL -> ItemStack.of(Material.BLACK_CONCRETE)
                            SparklySinuca.BallType.BLUE -> ItemStack.of(Material.CYAN_CONCRETE)
                            SparklySinuca.BallType.RED -> ItemStack.of(Material.RED_CONCRETE)
                        }
                    )
                    it.isPersistent = false
                    it.interpolationDuration = 2
                }

                this.ballToDisplayEntity[ball] = ballEntity
                this.ballToMatrix4f[ball] = transformationMatrix
            }
        }
    }

    /**
     * Checks if a set of coordinates are inside the playfield
     */
    fun checkIfCoordinateIsInsidePlayfield(x: Float, y: Float): Boolean {
        val isInsidePlayfieldX = x >= 0.0f && this.playfieldWidth > x
        val isInsidePlayfieldY = y >= 0.0f && this.playfieldHeight > y
        val isInsidePlayfield = isInsidePlayfieldX && isInsidePlayfieldY
        return isInsidePlayfield
    }

    data class Ball(
        val sinuca: SparklySinuca,
        val body: Body,
        val type: BallType
    ) {
        var pocketed = false

        fun markAsPocketed() {
            pocketed = true
            sinuca.playfield.removeBody(body)
            sinuca.ballToDisplayEntity.remove(this)?.remove()

            // When this ball is marked as pocketed, we should switch the player's teams
            if (sinuca.gamePhase !is SparklySinuca.GamePhase.StrikeDown && (type == BallType.BLUE || type == BallType.RED)) {
                val isRed = type == BallType.RED
                val isPlayer2 = sinuca.playerTurn == sinuca.player2

                val player1Team: BallType
                val player2Team: BallType

                if (isPlayer2) {
                    if (isRed) {
                        player2Team = BallType.RED
                        player1Team = BallType.BLUE
                    } else {
                        player2Team = BallType.BLUE
                        player1Team = BallType.RED
                    }
                } else {
                    if (isRed) {
                        player2Team = BallType.BLUE
                        player1Team = BallType.RED
                    } else {
                        player2Team = BallType.RED
                        player1Team = BallType.BLUE
                    }
                }

                sinuca.gamePhase = GamePhase.StrikeDown(player1Team, player2Team)
            }
        }
    }

    enum class BallType {
        CUE,
        EIGHT_BALL,
        BLUE,
        RED,
    }

    sealed class GamePhase {
        class BreakingOut : GamePhase()
        class DecidingTeams : GamePhase()
        class StrikeDown(
            val player1Team: BallType,
            val player2Team: BallType,
        ) : GamePhase()
    }
}