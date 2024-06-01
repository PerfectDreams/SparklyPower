package net.perfectdreams.dreamtntrun.utils

import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.perfectdreams.dreamcash.utils.Cash
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.extensions.removeAllPotionEffects
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawn
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawnWithEffects
import net.perfectdreams.dreamcorreios.utils.addItemIfPossibleOrAddToPlayerMailbox
import net.perfectdreams.dreammapwatermarker.DreamMapWatermarker
import net.perfectdreams.dreamtntrun.DreamTNTRun
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.util.Vector
import java.util.*

class TNTRun(val m: DreamTNTRun) {
    companion object {
        const val WORLD_NAME = "TNTRun"
    }

    var spawns = mutableListOf<Location>()
    var players = mutableSetOf<Player>()
    var playersInQueue = mutableSetOf<Player>()
    var canDestroyBlocks = false
    var isStarted = false
    var isPreStart = false
    val isGamePhase
        get() = isStarted && !isPreStart
    val world by lazy { Bukkit.getWorld(WORLD_NAME) }
    val queueSpawn by lazy {
        Location(
            world,
            10.5,
            140.0,
            0.5,
            0.0f,
            0.0f
        )
    }
    var isServerEvent = false
    var currentEventId = UUID.randomUUID()
    var lastWinner = UUID.randomUUID()
    val blocksToBeRestored = mutableMapOf<Location, BlockState>()

    fun preStart(isServerEvent: Boolean) {
        this.isServerEvent = isServerEvent
        if (isServerEvent)
            m.eventoTNTRun.running = true

        val eventId = UUID.randomUUID()
        this.currentEventId = eventId

        spawns.clear()
        blocksToBeRestored.clear()
        spawns.addAll(
            listOf(
                Location(
                    world,
                    10.5,
                    127.0,
                    0.5,
                    0.0f,
                    0.0f
                )
            )
        )

        playersInQueue.clear()
        isStarted = true
        isPreStart = true
        canDestroyBlocks = false

        scheduler().schedule(m) {
            val startAt = 60

            for (i in startAt downTo 1) {
                if (currentEventId != eventId) // Parece que o evento acabou e outro começou
                    return@schedule

                val announce = (i in 15..60 && i % 15 == 0) || (i in 0..14 && i % 5 == 0)

                if (announce) {
                    Bukkit.broadcastMessage("${DreamTNTRun.PREFIX} O Evento TNT Run começará em $i segundos! Use §6/tntrun§e para entrar!")
                }

                waitFor(20)
            }

            if (currentEventId != eventId) // Parece que o evento acabou e outro começou
                return@schedule

            start()
        }
    }

    fun start() {
        isPreStart = false

        val validPlayersInQueue = playersInQueue.filter { it.isValid && it.world.name == WORLD_NAME }
        playersInQueue.clear()
        playersInQueue.addAll(validPlayersInQueue)

        if (1 >= playersInQueue.size) {
            m.eventoTNTRun.running = false
            m.eventoTNTRun.lastTime = System.currentTimeMillis()
            isStarted = false
            isPreStart = false

            playersInQueue.forEach { player ->
                player.teleportToServerSpawnWithEffects()
                player.sendMessage("${DreamTNTRun.PREFIX} §cO TNT Run foi cancelado devido a falta de players...")
            }

            playersInQueue.clear()
            return
        }

        playersInQueue.forEachIndexed { index, player ->
            val locationToTeleportTo = spawns[index % spawns.size]

            PlayerUtils.healAndFeed(player)
            player.removeAllPotionEffects()
            players.add(player)

            val successfullyTeleported = player.teleport(locationToTeleportTo)
            if (!successfullyTeleported) {
                // uuh... that's not good
                // we will skip the finish check because what if it was the first user that caused this issue?
                removeFromGame(player, skipFinishCheck = true)
                return@forEachIndexed
            }
        }

        // Iniciar minigame... daqui a pouquitcho, yay!
        scheduler().schedule(m) {
            players.forEach {
                it.sendTitle("§c5", "§f", 0, 20, 0)
                it.playSound(it.location, Sound.UI_BUTTON_CLICK, 1f, 0.2f)
            }
            waitFor(20)
            players.forEach {
                it.sendTitle("§c4", "§f", 0, 20, 0)
                it.playSound(it.location, Sound.UI_BUTTON_CLICK, 1f, 0.4f)
            }
            waitFor(20)
            players.forEach {
                it.sendTitle("§c3", "§f", 0, 20, 0)
                it.playSound(it.location, Sound.UI_BUTTON_CLICK, 1f, 0.6f)
            }
            waitFor(20)
            players.forEach {
                it.sendTitle("§c2", "§f", 0, 20, 0)
                it.playSound(it.location, Sound.UI_BUTTON_CLICK, 1f, 0.8f)
            }
            waitFor(20)
            players.forEach {
                it.sendTitle("§c1", "§f", 0, 20, 0)
                it.playSound(it.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }
            waitFor(20)
            players.forEach {
                it.sendTitle("§4§lSobreviva!", "§f", 0, 20, 0)
                it.playSound(it.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                it.playSound(it.location, "perfectdreams.sfx.special_stage", 100f, 1f)
                it.gameMode = GameMode.ADVENTURE // Avoid issues with users being able to place "lag blocks" to not fall

                // Start breaking the blocks where the player is in, to avoid users standing still just to not trigger a block break
                val blockBelowThem = it.location.block.getRelative(BlockFace.DOWN)
                val blockBelowBelowThem = blockBelowThem.getRelative(BlockFace.DOWN)

                if (canDestroyBlocks && !blocksToBeRestored.containsKey(blockBelowThem.location)) {
                    m.TNTRun.startBlockBreak(
                        blockBelowThem,
                        blockBelowBelowThem
                    )
                }
            }

            canDestroyBlocks = true

            while (isStarted) {
                if (players.size == 1) {
                    m.logger.warning { "Players remaining detected as 1 in the event in the repeating schedule! This should never happen!" }
                    finish()
                    continue
                }

                m.logger.info { "Remaining players: ${players.map { it.name }}" }
                // A cada um segundo iremos verificar players inválidos que ainda estão no jogo
                // We will also check if the player y position is above the queue spawn because, if it is, then they weren't teleported (for some reason)
                val invalidPlayers = players.filter { !it.isValid || it.location.world.name != WORLD_NAME || (it.location.world.name == WORLD_NAME && it.location.y >= queueSpawn.y) }
                m.logger.info { "Removing invalid players $invalidPlayers" }
                invalidPlayers.forEach {
                    removeFromGame(it, skipFinishCheck = true)
                }

                waitFor(20L)
            }
        }
    }

    fun finish() {
        val player = players.firstOrNull()
        if (player == null) {
            isStarted = false
            isPreStart = false

            m.eventoTNTRun.lastTime = System.currentTimeMillis()
            m.eventoTNTRun.running = false

            Bukkit.broadcastMessage("${DreamTNTRun.PREFIX} Parece que o TNT Run acabou sem nenhum ganhador... isto é um bug e jamais deveria acontecer!")
            return
        }

        // No need to check if the event has finished for the last player
        removeFromGame(player, skipFinishCheck = true)

        isStarted = false
        isPreStart = false

        m.eventoTNTRun.lastTime = System.currentTimeMillis()
        m.eventoTNTRun.running = false

        blocksToBeRestored.forEach { (_, blockState) ->
            // https://www.spigotmc.org/threads/cloning-a-block.390751/
            m.logger.info { "Restoring block $blockState" }
            blockState.update(true)
        }

        blocksToBeRestored.clear()

        val howMuchMoneyWillBeGiven = 50_000
        val howMuchNightmaresWillBeGiven = 1

        Bukkit.broadcastMessage("${DreamTNTRun.PREFIX} §b${player.displayName}§e venceu o TNT Run! Ele ganhou §2$howMuchMoneyWillBeGiven sonecas§a e §c$howMuchNightmaresWillBeGiven pesadelo§a!")

        lastWinner = player.uniqueId
        player.deposit(howMuchMoneyWillBeGiven.toDouble(), TransactionContext(type = TransactionType.EVENTS, extra = "TNT Run"))

        val map = ItemStack(Material.FILLED_MAP).meta<MapMeta> {
            this.mapId = 26785

            this.displayName(
                Component.text("Venci o evento ")
                    .color(NamedTextColor.YELLOW)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("TNT Run").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD))
                    .append(Component.text("!"))
            )
        }

        DreamMapWatermarker.watermarkMap(map, null)
        player.addItemIfPossibleOrAddToPlayerMailbox(map)

        scheduler().schedule(m, SynchronizationContext.ASYNC) {
            val wonAt = System.currentTimeMillis()

            DreamCore.INSTANCE.dreamEventManager.addEventVictory(
                player,
                "TNT Run",
                wonAt
            )

            Cash.giveCash(player, howMuchNightmaresWillBeGiven.toLong(), TransactionContext(type = TransactionType.EVENTS, extra = "TNT Run"))
        }
    }

    fun removeFromGame(player: Player, skipFinishCheck: Boolean) {
        m.logger.info { "Removing ${player.name} from the game. Skip finish check? $skipFinishCheck" }
        if (!players.contains(player))
            return

        // Você perdeu, que sad...
        players.remove(player)

        // Reset player velocity to avoid them dying before teleporting (due to falling from the tower)
        player.velocity = Vector(0, 0, 0)
        player.teleportToServerSpawnWithEffects()
        player.gameMode = GameMode.SURVIVAL

        if (!skipFinishCheck && isGamePhase && players.size == 1)
            finish()
    }

    fun removeFromQueue(player: Player) {
        m.logger.info { "Removing ${player.name} from the queue" }
        if (!playersInQueue.contains(player))
            return

        player.teleportToServerSpawnWithEffects()
        playersInQueue.remove(player)
    }

    fun joinQueue(player: Player) {
        m.logger.info { "Adding ${player.name} to the queue!" }
        player.teleport(queueSpawn)
        playersInQueue.add(player)
    }

    fun startBlockBreak(blockBelowThem: Block, blockBelowBelowThem: Block) {
        blocksToBeRestored[blockBelowThem.location] = blockBelowThem.state
        blocksToBeRestored[blockBelowBelowThem.location] = blockBelowBelowThem.state

        m.launchMainThread {
            delay(400L)
            if (!m.TNTRun.isStarted) // Event has already ended!
                return@launchMainThread

            blockBelowThem.type = Material.AIR
            blockBelowBelowThem.type = Material.AIR

            blockBelowThem.world.playSound(
                blockBelowBelowThem.location,
                Sound.ENTITY_CHICKEN_EGG,
                1f,
                DreamUtils.random.nextFloat(0.9f, 1.1f)
            )

            blockBelowThem.world.spawnParticle(
                Particle.SMOKE,
                blockBelowBelowThem.location,
                5
            )
        }
    }
}