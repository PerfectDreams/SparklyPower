package net.perfectdreams.dreamxizum.structures

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

        m.server.broadcast(textComponent {
            append(DreamXizum.prefix())
            appendSpace()
            append("A batalha entre §b${player.displayName} §6e §b${opponent.displayName} §6está prestes a começar!") {
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
        m.launchMainThread {
            countdown = true

            for (idx in 5 downTo 1) {
                listOf(player, opponent).forEach {
                    it.sendTitle("§c$idx", "§7Prepare-se para a batalha!", 10, 80, 10)

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
                it.sendTitle("§a§lComeçou!", "§7Que vença o melhor!", 10, 80, 10)
            }

            while (started) {
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
        countdown = false
        started = false
        ended = true

        val winner = if (loser == player) opponent else player

        m.arenas.first { it.data.arenaName == arena.data.arenaName }.let {
            it.inUse = false
        }

        val prettyReason = when (result) {
            XizumBattleResult.DRAW -> "§6A batalha entre §b${player.displayName} §6e §b${opponent.displayName} §6terminou em empate!"
            XizumBattleResult.KILLED -> "§b${winner.displayName} §atriunfou sobre §b${loser.displayName}§a! A batalha foi encerrada!"
            XizumBattleResult.DISCONNECTION -> "§b${loser.displayName} §adesconectou-se da batalha! §b${winner.displayName} §aé o vencedor!"
            XizumBattleResult.COULD_NOT_TELEPORT -> "§cNão foi possível teleportar §b${loser.displayName} §cpara a batalha! §b${winner.displayName} §aé o vencedor!"
            XizumBattleResult.RAN -> "§b${loser.displayName} §afugiu da batalha! §b${winner.displayName} §aé o vencedor!"
            XizumBattleResult.TIMEOUT -> "§cA batalha entre §b${player.displayName} §ce §b${opponent.displayName} §cdurou tanto que excedeu o tempo limite!"
        }

        m.server.broadcast(textComponent {
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

        val items = winner.location.world.getNearbyEntities(winner.location, 100.0, 100.0, 100.0) { it is Item }

        items.forEach {
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
                        m.server.broadcast(textComponent {
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
        countdown = false
        started = false
        ended = true

        m.arenas.first { it.data.arenaName == arena.data.arenaName }.let {
            it.inUse = false
        }

        BattleListener.enderPearlCooldown.clear()

        m.server.broadcast(textComponent {
            append(DreamXizum.prefix())
            appendSpace()
            append("A batalha entre §b${player.displayName} §6e §b${opponent.displayName} §6terminou em empate!") {
                color(NamedTextColor.GOLD)
            }
        })

        val items = player.location.world.getNearbyEntities(player.location, 100.0, 100.0, 100.0) { it is Item }

        items.forEach {
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
    }
}