package net.perfectdreams.dreamxizum.structures

import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.InstantFirework
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.extensions.removeAllPotionEffects
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawnWithEffects
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawnWithEffectsAwait
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.listeners.BattleListener
import net.perfectdreams.dreamxizum.modes.AbstractXizumBattleMode
import net.perfectdreams.dreamxizum.modes.vanilla.CompetitiveXizumMode
import net.perfectdreams.dreamxizum.modes.vanilla.StandardXizumMode
import net.perfectdreams.dreamxizum.tables.XizumMatchesResults
import net.perfectdreams.dreamxizum.tables.dao.XizumProfile
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import net.perfectdreams.dreamxizum.utils.XizumBattleResult
import net.perfectdreams.dreamxizum.utils.XizumRank
import org.bukkit.*
import org.bukkit.entity.Arrow
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import kotlin.math.pow

class XizumBattle(
    val m: DreamXizum,
    val arena: XizumArena,
    val mode: AbstractXizumBattleMode,
    val player: Player,
    val opponent: Player,
) {
    var countdown = false
    var started = false
    var ended = false
    var duration = 180

    var playerPreviousInventory = arrayOf<ItemStack?>()
    var opponentPreviousInventory = arrayOf<ItemStack?>()
    private var arenaBattleTask: Job? = null

    val playerPosition = arena.playerPos ?: error("player position is null on arena '${arena.data.arenaName}'!")
    val opponentPosition = arena.opponentPos ?: error("opponent position is null on arena '${arena.data.arenaName}'!")

    fun start() {
        if (mode is Listener) {
            m.registerEvents(mode)
        }

        val asPair = Pair(player, opponent)

        m.arenas.firstOrNull { it.data.arenaName == arena.data.arenaName }?.let {
            it.inUse = true
        }

        announceToAllPlayersInXizumWorld(textComponent {
            append(DreamXizum.prefix())
            appendSpace()
            append("A batalha entre ")
            append(player.displayName())
            append(" e ")
            append(opponent.displayName())
            append(" está prestes a começar! Modo: ")
            append(XizumBattleMode.prettify(mode.enum) ?: "Desconhecido") {
                color(NamedTextColor.GOLD)
            }
        })

        if (mode !is StandardXizumMode) {
            if (!player.inventory.isEmpty) {
                playerPreviousInventory = player.inventory.contents.clone()
            }

            if (!opponent.inventory.isEmpty) {
                opponentPreviousInventory = opponent.inventory.contents.clone()
            }
        }

        if (!player.teleport(playerPosition)) {
            end(player, XizumBattleResult.COULD_NOT_TELEPORT)
            return
        }

        if (!opponent.teleport(opponentPosition)) {
            end(opponent, XizumBattleResult.COULD_NOT_TELEPORT)
            return
        }

        mode.setupInventory(asPair)

        updatePlayerStatus(player, false)
        updatePlayerStatus(opponent, false)

        // countdown to start the battle
        // This task MUST be cancelled when the arena ends/draws, if not, if a lot of players are joining the arena, the task WILL continue active and WILL cause bugs
        this.arenaBattleTask = m.launchMainThread {
            countdown = true

            for (idx in 5 downTo 1) {
                listOf(player, opponent).forEach {
                    it.sendTitle("§c$idx", "§7Prepare-se para a batalha!", 10, 60, 10)

                    it.sendActionBar(textComponent {
                        color(NamedTextColor.GREEN)
                        XizumBattleMode.prettify(mode.enum)?.let { it1 -> append(it1) }
                    })
                }

                delayTicks(20)
            }

            countdown = false

            mode.addAfterCountdown(asPair)

            started = true

            Bukkit.getOnlinePlayers().forEach {
                if (it == player || it == opponent)
                    return@forEach

                player.hidePlayer(m, it)
                opponent.hidePlayer(m, it)
            }

            updatePlayerStatus(player, true)
            updatePlayerStatus(opponent, true)

            listOf(player, opponent).forEach {
                it.sendTitle("§a§lComeçou!", "§7Que vença o melhor!", 10, 20, 10)
            }

            while (started) {
                if (!coroutineContext.isActive)
                    return@launchMainThread

                if (duration <= 0) {
                    draw()
                    break
                }

                if (duration <= 10) {
                    listOf(player, opponent).forEach {
                        it.playSound(it.location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f)
                    }
                }

                listOf(player, opponent).forEach {
                    it.sendActionBar(textComponent {
                        append("Restam $duration segundos!")
                    })
                }

                delayTicks(20)
                duration--
            }
        }
    }

    fun end(loser: Player, result: XizumBattleResult) {
        arenaBattleTask?.cancel()
        arenaBattleTask = null
        countdown = false
        started = false
        ended = true

        val winner = if (loser == player) opponent else player

        val prettyReason = when (result) {
            XizumBattleResult.DRAW -> textComponent {
                append("§6A batalha entre ")
                append(player.displayName())
                append(" §6e ")
                append(opponent.displayName())
                append(" §6terminou em empate!")
            }

            XizumBattleResult.KILLED -> textComponent {
                append(winner.displayName())
                append(" §atriunfou sobre ")
                append(loser.displayName())
                append("§a! A batalha foi encerrada!")
            }
            XizumBattleResult.DISCONNECTION -> textComponent {
                append(loser.displayName())
                append(" §adesconectou-se da batalha! ")
                append(winner.displayName())
                append(" §aé o vencedor!")
            }
            XizumBattleResult.COULD_NOT_TELEPORT -> textComponent {
                append("§cNão foi possível teleportar ")
                append(loser.displayName())
                append(" §cpara a batalha! ")
                append(winner.displayName())
                append(" §aé o vencedor!")
            }
            XizumBattleResult.RAN -> textComponent {
                append(loser.displayName())
                append(" §bfugiu da batalha! ")
                append(winner.displayName())
                append(" §aé o vencedor!")
            }
            XizumBattleResult.TIMEOUT -> textComponent {
                append("§cA batalha entre ")
                append(player.displayName())
                append(" §ce ")
                append(opponent.displayName())
                append(" §cterminou por tempo limite!")
            }
        }

        announceToAllPlayersInXizumWorld(textComponent {
            append(DreamXizum.prefix())
            appendSpace()
            append(prettyReason)
        })

        if (mode !is StandardXizumMode) {
            // restore player inventory
            player.inventory.clear()
            player.inventory.contents = playerPreviousInventory
            playerPreviousInventory = arrayOf()

            // restore opponent inventory
            opponent.inventory.clear()
            opponent.inventory.contents = opponentPreviousInventory
            opponentPreviousInventory = arrayOf()
        }

        val items = player.location.world.getNearbyEntities(player.location, 100.0, 100.0, 100.0) { it is Item }
        val arrows = player.location.world.getNearbyEntities(player.location, 100.0, 100.0, 100.0) { it is Arrow }

        items.forEach {
            it.remove()
        }

        arrows.forEach {
            it.remove()
        }

        listOf(player, opponent).forEach {
            BattleListener.enderPearlCooldown.remove(it.uniqueId)
        }

        m.launchAsyncThread {
            var winnerPoints = 0
            var loserPoints = 0

            var winnerTotalPoints = 0
            var loserTotalPoints = 0

            var loserProfile: XizumProfile? = null

            transaction(Databases.databaseNetwork) {
                if (mode.enum !in listOf(XizumBattleMode.CUSTOM, XizumBattleMode.STANDARD)) {
                    val winnerDb = XizumProfile.findOrCreate(winner.uniqueId)
                    val loserDb = XizumProfile.findOrCreate(loser.uniqueId)

                    loserProfile = loserDb

                    XizumMatchesResults.insert {
                        it[XizumMatchesResults.arenaName] = arena.data.arenaName
                        it[XizumMatchesResults.arenaMode] = arena.data.mode!!.name // Should NEVER be null!
                        it[XizumMatchesResults.winner] = winner.uniqueId
                        it[XizumMatchesResults.loser] = loser.uniqueId
                        it[XizumMatchesResults.finishedAt] = Instant.now()
                    }

                    if (mode is CompetitiveXizumMode && result != XizumBattleResult.DRAW) {
                        val k = when {
                            winnerDb.rating < 30 -> 40  // new player
                            winnerDb.rating < 2400 -> 20  // high elo player
                            winnerDb.rating >= 2400 -> 10  // expert player
                            else -> 20
                        }

                        val expectedWinner = 1 / (1 + 10.0.pow((loserDb.rating - winnerDb.rating) / 400.0))

                        val victoryResult = 1.0
                        val winnerRatingChange = (k * (victoryResult - expectedWinner)).toInt()

                        winnerPoints = winnerRatingChange
                        winnerDb.rating += winnerPoints

                        val expectedLoser = 1 - expectedWinner
                        val loserRatingChange = (k * (0 - expectedLoser)).toInt()

                        val limitedLoserRatingChange = maxOf(-40, loserRatingChange)

                        loserPoints = if (loserDb.rating <= 0) 0 else limitedLoserRatingChange
                        loserDb.rating += if (loserDb.rating <= 0) 0 else loserPoints

                        winnerTotalPoints = winnerDb.rating
                        loserTotalPoints = if (loserDb.rating <= 0) 0 else loserDb.rating
                    }
                }
            }

            onMainThread {
                Bukkit.getOnlinePlayers().forEach {
                    player.showPlayer(m, it)
                    opponent.showPlayer(m, it)
                }

                if (mode is CompetitiveXizumMode) {
                    val previousRank = XizumRank.entries.lastOrNull { it.rating <= (winnerTotalPoints - winnerPoints) }
                    val currentRank = XizumRank.entries.lastOrNull { it.rating <= winnerTotalPoints }

                    if (previousRank != null && currentRank != previousRank) {
                        announceToAllPlayersInXizumWorld(textComponent {
                            append(DreamXizum.prefix())
                            appendSpace()
                            append("§b${winner.displayName} §7subiu de rank de ${previousRank.text} §7para ${currentRank?.text}§7!")
                        })
                    }

                    winner.sendTitle("§7Parabéns, §a§lvocê venceu§7!", "§f", 10, 80, 10)
                    loser.sendTitle("§7Que pena, §c§lvocê perdeu§7!", "§f", 10, 80, 10)

                    winner.sendMessage(textComponent {
                        append(DreamXizum.prefix())
                        appendSpace()
                        color(NamedTextColor.GREEN)
                        append("Você ganhou §b$winnerPoints§a pontos! Agora você tem §b$winnerTotalPoints§a pontos no total!")
                    })

                    loser.sendMessage(textComponent {
                        append(DreamXizum.prefix())
                        appendSpace()
                        color(NamedTextColor.RED)
                        append("Você perdeu §4$loserPoints§c pontos! Agora você tem §b$loserTotalPoints§c pontos no total!")
                    })
                }

                var backItem: Item? = null

                if (loserProfile?.canDropHead == true) {
                    val head = ItemStack(Material.PLAYER_HEAD).meta<SkullMeta> {
                        playerProfile = loser.playerProfile
                    }

                    if (result == XizumBattleResult.RAN) {
                        val item = winner.location.world.dropItemNaturally(winner.location, head)

                        backItem = item

                    } else {
                        val item = loser.location.world.dropItemNaturally(loser.location, head)

                        backItem = item
                    }

                }

                // If the player is dead, we can't teleport it somewhere else
                // The player will respawn anyway
                updateLoserDeathStatus(loser)

                delayTicks(100)

                backItem?.remove()

                m.activeBattles.remove(this@XizumBattle)

                updatePlayerStatus(winner, true)
                winner.teleportToServerSpawnWithEffectsAwait()
                m.arenas.first { it.data.arenaName == arena.data.arenaName }.let {
                    it.inUse = false
                }
            }
        }

        InstantFirework.spawn(loser.location, FireworkEffect.builder()
            .with(FireworkEffect.Type.STAR)
            .withColor(Color.RED)
            .withFade(Color.BLACK)
            .withFlicker()
            .withTrail()
            .build())

        InstantFirework.spawn(winner.location, FireworkEffect.builder()
            .with(FireworkEffect.Type.STAR)
            .withColor(Color.GREEN)
            .withFade(Color.BLACK)
            .withFlicker()
            .withTrail()
            .build())
    }

    private fun updatePlayerStatus(player: Player, reset: Boolean) {
        player.gameMode = GameMode.SURVIVAL
        player.foodLevel = 20
        player.health = player.maxHealth
        player.allowFlight = false

        if (reset) {
            player.walkSpeed = 0.2f
        } else {
            player.walkSpeed = 0f
            player.removeAllPotionEffects()
            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 100, -5))
        }
    }

    private fun updateLoserDeathStatus(player: Player) {
        // We CANNOT set the player's health because, if we set it, the player won't be able to respawn because they are not "dead"
        player.gameMode = GameMode.SURVIVAL
        player.allowFlight = false
        player.walkSpeed = 0.2f // Yes, we need to reset the walk speed
    }

    fun draw() {
        arenaBattleTask?.cancel()
        arenaBattleTask = null
        countdown = false
        started = false
        ended = true

        m.arenas.first { it.data.arenaName == arena.data.arenaName }.let {
            it.inUse = false
        }

        BattleListener.enderPearlCooldown.clear()

        announceToAllPlayersInXizumWorld(textComponent {
            append(DreamXizum.prefix())
            appendSpace()
            append("§6A batalha entre ")
            append(player.displayName())
            append(" §6e ")
            append(opponent.displayName())
            append(" §6terminou em empate!")
        })

        val items = player.location.world.getNearbyEntities(player.location, 100.0, 100.0, 100.0) { it is Item }
        val arrows = player.location.world.getNearbyEntities(player.location, 100.0, 100.0, 100.0) { it is Arrow }

        items.forEach {
            it.remove()
        }

        arrows.forEach {
            it.remove()
        }

        player.teleportToServerSpawnWithEffects()
        opponent.teleportToServerSpawnWithEffects()

        // reset the inventory
        if (mode !is StandardXizumMode) {
            // restore player inventory
            player.inventory.clear()
            player.inventory.contents = playerPreviousInventory
            playerPreviousInventory = arrayOf()

            // restore opponent inventory
            opponent.inventory.clear()
            opponent.inventory.contents = opponentPreviousInventory
            opponentPreviousInventory = arrayOf()
        }

        updatePlayerStatus(player, true)
        updatePlayerStatus(opponent, true)

        m.activeBattles.remove(this)

        m.arenas.first { it.data.arenaName == arena.data.arenaName }.let {
            it.inUse = false
        }
    }

    private fun announceToAllPlayersInXizumWorld(content: Component) {
        m.server.onlinePlayers.forEach {
            if (it.world.name == m.arenas.first().data.worldName) {
                it.sendMessage(content)
            }
        }
    }
}