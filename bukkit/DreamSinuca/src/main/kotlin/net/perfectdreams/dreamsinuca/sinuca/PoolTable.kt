package net.perfectdreams.dreamsinuca.sinuca

import io.papermc.paper.math.BlockPosition
import io.papermc.paper.math.Position
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcustomitems.items.SparklyItemsRegistry
import net.perfectdreams.dreamsinuca.DreamSinuca
import net.perfectdreams.dreamsinuca.tables.EightBallPoolGameMatches
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.dyn4j.dynamics.Body
import org.dyn4j.world.World
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * Stores a reference to a pool table
 */
class PoolTable(
    val m: DreamSinuca,
    val poolTableEntity: ItemDisplay,
    val orientation: PoolTableOrientation
) {
    val world
        get() = poolTableEntity.world

    private val location
        get() = poolTableEntity.location

    // TODO: Add a display entity and stuff
    //  How can we hook up the SparklySinuca into the PoolTable code?
    var activeSinuca: SparklySinuca? = null
    var pendingPlayer: Player? = null

    private val previousGameBalls = mutableListOf<ItemDisplay>()

    /* val gameStatusDisplay = world.spawn(
        location.clone().add(0.0, 2.0 + 1.1, 0.0),
        TextDisplay::class.java
    ) {
        it.isPersistent = false
        it.billboard = Display.Billboard.VERTICAL
        it.lineWidth = Int.MAX_VALUE
        it.text(
            textComponent {
                color(NamedTextColor.GOLD)
                content("Sinuquinha")
            }
        )
    } */

    val blocks = mutableSetOf<BlockPosition>()

    fun getCorrectXZOrder(x: Int, z: Int): Pair<Int, Int> {
        return when (orientation) {
            PoolTableOrientation.EAST -> Pair(x, z)
            PoolTableOrientation.SOUTH -> Pair(z, x)
        }
    }

    fun getCorrectXZOrder(x: Float, z: Float): Pair<Float, Float> {
        return when (orientation) {
            PoolTableOrientation.EAST -> Pair(x, z)
            PoolTableOrientation.SOUTH -> Pair(z, x)
        }
    }

    fun getCorrectXZOrder(x: Double, z: Double): Pair<Double, Double> {
        return when (orientation) {
            PoolTableOrientation.EAST -> Pair(x, z)
            PoolTableOrientation.SOUTH -> Pair(z, x)
        }
    }

    fun makeBarrierBlocks() {
        // With this, we can know where we should place the blocks
        // Keep in mind that the blockX/blockZ will be always offset by a bit
        for (x in -2..2) {
            for (z in -1..1) {
                val (correctedX, correctedZ) = getCorrectXZOrder(x.toDouble(), z.toDouble())
                val targetLocation = location.clone().add(correctedX, 0.0, correctedZ)
                targetLocation.block.type = Material.BARRIER
            }
        }
    }

    fun configure() {
        val startBlockX = location.blockX
        val startBlockZ = location.blockZ

        // With this, we can know where we should place the blocks
        // Keep in mind that the blockX/blockZ will be always offset by a bit
        for (x in -2..2) {
            for (z in -1..1) {
                val (correctedX, correctedZ) = getCorrectXZOrder(x, z)
                blocks.add(Position.block((startBlockX + correctedX), location.y.toInt(), (startBlockZ + correctedZ)))
            }
        }
    }

    /**
     * Starts an 8 ball pool game between [player1] and [player2]
     */
    fun start8BallPool(player1: Player, player2: Player) {
        val now = Instant.now()

        this.pendingPlayer = null

        this.previousGameBalls.forEach { it.remove() }
        this.previousGameBalls.clear()

        // "Game Origin"
        // The game origin is always at 1.1 blocks above the pool table entity
        // We also need to think that the "poolTableEntity" is not at the center of the pool table entity

        val gameOriginLocation = Location(poolTableEntity.world, poolTableEntity.x, poolTableEntity.y + 1.1, poolTableEntity.z, 0f, 0f)

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
            this,
            player1,
            player2,
            gameOriginLocation,
            orientation,
            5f,
            3f,
            playfield,
        )
        // sparklySinuca.cueball.markAsPocketed()
        // sparklySinuca.cueballInHand = true
        sendSinucaMessageToPlayersNearIt(
            textComponent {
                color(NamedTextColor.GREEN)
                content("O jogo de sinuca entre ")
                appendTextComponent {
                    color(NamedTextColor.AQUA)
                    content(player1.name)
                }
                appendTextComponent {
                    content(" e ")
                }
                appendTextComponent {
                    color(NamedTextColor.AQUA)
                    content(player2.name)
                }
                appendTextComponent {
                    content(" começou!")
                }
            }
        )

        m.launchAsyncThread {
            val matchId = transaction(Databases.databaseNetwork) {
                EightBallPoolGameMatches.insertAndGetId {
                    it[EightBallPoolGameMatches.startedAt] = now

                    it[EightBallPoolGameMatches.player1] = player1.uniqueId
                    it[EightBallPoolGameMatches.player2] = player2.uniqueId

                    it[EightBallPoolGameMatches.world] = gameOriginLocation.world.name
                    it[EightBallPoolGameMatches.x] = gameOriginLocation.x
                    it[EightBallPoolGameMatches.y] = gameOriginLocation.y
                    it[EightBallPoolGameMatches.z] = gameOriginLocation.z
                }

            }

            sparklySinuca.matchId.value = matchId.value
        }

        this.activeSinuca = sparklySinuca

        sparklySinuca.startBallTrajectoryPreviewTask()
        sparklySinuca.startCueballPreviewTask()
        sparklySinuca.updateGameStatusDisplay()
        sparklySinuca.updateGameObjects()

        this.world.playSound(
            gameOriginLocation,
            "minecraft:entity.player.levelup",
            1f,
            1f
        )
    }

    fun tearDown() {
        cancelActive8BallPool(null, null, FinishReason.POOL_TABLE_TEAR_DOWN)

        // Actually... if we are tearing down the table, we want to remove everything
        for (entity in this.previousGameBalls) {
            entity.remove()
        }

        this.previousGameBalls.clear()

        this.activeSinuca = null
    }

    fun cancelActive8BallPool(winner: Player?, loser: Player?, reason: FinishReason) {
        val activeSinuca = this.activeSinuca

        if (activeSinuca != null) {
            activeSinuca.isPlaying = false

            val endedAt = Instant.now()

            m.launchAsyncThread {
                val matchId = activeSinuca.matchId.filterNotNull().first()

                transaction(Databases.databaseNetwork) {
                    EightBallPoolGameMatches.update({ EightBallPoolGameMatches.id eq matchId }) {
                        it[EightBallPoolGameMatches.winner] = winner?.uniqueId
                        it[EightBallPoolGameMatches.loser] = loser?.uniqueId
                        it[EightBallPoolGameMatches.finishReason] = reason
                        it[EightBallPoolGameMatches.finishedAt] = endedAt
                    }
                }
            }

            activeSinuca.gameStatusDisplay.remove()

            for (entity in activeSinuca.ballTrajectoryEntities) {
                entity.remove()
            }

            // To make it more "alive", the previous game balls will persist in the table until the next game starts
            previousGameBalls.addAll(activeSinuca.ballToDisplayEntity.values)

            this.activeSinuca = null
        }
    }

    /**
     * Checks if the [player] is near this pool table
     */
    fun isNearTable(player: Player): Boolean {
        if (player.world != this.world)
            return false

        val locationVerticallyAlignedToTable = player.location.apply {
            this.y = this@PoolTable.location.y
        }

        val distanceSquared = locationVerticallyAlignedToTable.distanceSquared(this.location)

        return 256 >= distanceSquared // 16 blocks
    }

    /**
     * Gets all players that are near this pool table
     */
    fun getNearbyPlayers(): List<Player> {
        return this.location.world.players.filter { isNearTable(it) }
    }

    /**
     * Sends a player about this pool table near
     */
    fun sendSinucaMessageToPlayersNearIt(component: Component) {
        for (player in this.getNearbyPlayers()) {
            sendSinucaMessageToPlayer(player, component)
        }
    }

    /**
     * Sends a player about this pool table near
     */
    fun sendSinucaMessageToPlayer(player: Player, component: Component) {
        player.sendMessage(
            textComponent {
                append(DreamSinuca.PREFIX)
                append(" ")
                append(component)
            }
        )
    }

    /**
     * Makes [player] join the current pool table queue
     */
    fun joinQueue(player: Player) {
        // Are we already in another sinuca?
        for (sinuca in m.poolTables) {
            val activeSinuca = sinuca.value.activeSinuca

            if (activeSinuca != null) {
                val isInCurrentSinuca = activeSinuca.player1 == player || activeSinuca.player2 == player

                if (isInCurrentSinuca) {
                    sinuca.value.sendSinucaMessageToPlayer(
                        player,
                        textComponent {
                            color(NamedTextColor.RED)
                            content("Você já está em outro jogo de sinuca!")
                        }
                    )
                    return
                }
            }

            if (sinuca.value.pendingPlayer == player) {
                sinuca.value.pendingPlayer = null
            }
        }

        val hasStick = m.checkIfPlayerHasCueStickInInventory(player)
        if (!hasStick) {
            sendSinucaMessageToPlayer(
                player,
                textComponent {
                    color(NamedTextColor.RED)
                    content("Você precisa ter um taco de sinuca no seu inventário para entrar na fila da mesa de sinuca!")
                }
            )
            return
        }

        this.pendingPlayer = player

        sendSinucaMessageToPlayer(
            player,
            textComponent {
                color(NamedTextColor.GREEN)
                content("Você entrou na fila desta mesa de sinuca! Quando outro player entrar, a partida irá começar!")
            }
        )
    }
}