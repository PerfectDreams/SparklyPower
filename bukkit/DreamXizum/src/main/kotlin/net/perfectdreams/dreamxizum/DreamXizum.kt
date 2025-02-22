package net.perfectdreams.dreamxizum

import com.okkero.skedule.schedule
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamxizum.commands.DreamXizumCommand
import net.perfectdreams.dreamxizum.commands.XizumCommand
import net.perfectdreams.dreamxizum.listeners.BattleListener
import net.perfectdreams.dreamxizum.listeners.XizumRequestInventoryListener
import net.perfectdreams.dreamxizum.listeners.XizumCustomRequestInventoryListener
import net.perfectdreams.dreamxizum.modes.vanilla.*
import net.perfectdreams.dreamxizum.structures.XizumArena
import net.perfectdreams.dreamxizum.structures.XizumBattle
import net.perfectdreams.dreamxizum.structures.XizumBattleRequest
import net.perfectdreams.dreamxizum.tables.XizumProfiles
import net.perfectdreams.dreamxizum.tables.XizumMatchesResults
import net.perfectdreams.dreamxizum.tables.dao.XizumProfile
import net.perfectdreams.dreamxizum.utils.*
import net.perfectdreams.dreamxizum.utils.config.XizumPluginConfig
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.UUID
import kotlin.math.abs

class DreamXizum : KotlinPlugin() {
    companion object {
        fun prefix() = textComponent {
            append("[") {
                color(NamedTextColor.DARK_GRAY)
            }
            append("Xizum") {
                color(NamedTextColor.BLUE)
                decorate(TextDecoration.BOLD)
            }
            append("]") {
                color(NamedTextColor.DARK_GRAY)
            }
        }

        val IS_IN_CAMAROTE = SparklyNamespacedKey("is_in_camarote", PersistentDataType.BOOLEAN)

        lateinit var INSTANCE: DreamXizum

        private val json = Json {
            ignoreUnknownKeys = true
        }

        private val MAX_TIME_IN_QUEUE = 60 * 1000
    }

    private val configFile = File(dataFolder, "config.json")
    lateinit var config: XizumPluginConfig

    var arenas = mutableSetOf<XizumArena>()
    val activeBattles = mutableSetOf<XizumBattle>()
    val queue = mutableSetOf<XizumBattleRequest>()

    override fun softEnable() {
        super.softEnable()

        preload()

        INSTANCE = this

        registerCommand(DreamXizumCommand(this))
        registerCommand(XizumCommand(this))

        registerEvents(BattleListener(this))
        registerEvents(XizumRequestInventoryListener(this))
        registerEvents(XizumCustomRequestInventoryListener(this))

        startQueueCheckTask()

        transaction(Databases.databaseNetwork) {
            SchemaUtils.createMissingTablesAndColumns(
                XizumProfiles,
                XizumMatchesResults
            )
        }
    }

    override fun softDisable() {
        super.softDisable()

        cleanup()
    }

    private fun cleanup() {
        activeBattles.filter { it.started || it.countdown }.forEach { it.draw() }
        activeBattles.clear()
        // The arenas should ONLY be cleared after all battles have been drawn, because the draw code does call the arena clear
        arenas.clear()
    }

    private fun createConfigFile() {
        if (!configFile.exists()) {
            val content = """
                {
                    "spectatorPos": null,
                    "arenas": []
                }
            """.trimIndent()

            configFile.writeText(content)
        }
    }

    private fun startQueueCheckTask() {
        scheduler().runTaskTimer(this, Runnable {
            XizumBattleMode.entries.forEach {
                checkQueue(it)
            }
        }, 0, 20L)
    }

    private fun preload() {
        if (!configFile.exists()) createConfigFile()
        config = json.decodeFromString<XizumPluginConfig>(configFile.readText())

        config.arenas.forEach {
            arenas.add(
                XizumArena(
                    it,
                    it.playerPos?.toBukkitLocation(it.worldName),
                    it.opponentPos?.toBukkitLocation(it.worldName)
                )
            )
        }
    }

    fun createArena(arenaConfig: XizumPluginConfig.XizumArenaConfig) {
        val builtArena = XizumArena(arenaConfig, null, null)

        arenas.add(builtArena)

        updateConfigFile()
    }

    fun deleteArena(arenaName: String) {
        val arena = arenas.firstOrNull { it.data.arenaName == arenaName } ?: return

        arenas.remove(arena)

        updateConfigFile()
    }

    fun updateArena(arenaName: String, data: XizumPluginConfig.XizumArenaConfig) {
        val arena = arenas.firstOrNull { it.data.arenaName == arenaName } ?: return

        arena.data = data

        updateConfigFile()
    }

    fun setRatingForPlayer(playerUniqueId: UUID, newRating: Int): XizumProfile? {
        return try {
            transaction(Databases.databaseNetwork) {
                val profile = XizumProfile.findOrCreate(playerUniqueId)
                profile.rating = newRating
                profile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun updateRuntimeArenas() {
        arenas = config.arenas.map { XizumArena(it, it.playerPos?.toBukkitLocation(it.worldName), it.opponentPos?.toBukkitLocation(it.worldName)) }.toMutableSet()
    }

    fun updateConfigFile() {
        val arenasData = arenas.map { it.data }.toMutableList()

        config.arenas = arenasData

        configFile.writeText(json.encodeToString(config))

        updateRuntimeArenas()
    }

    fun getArena(arenaName: String) = arenas.firstOrNull { it.data.arenaName == arenaName }

    fun notifyNoArena(playerRequest: XizumBattleRequest, otherRequest: XizumBattleRequest) {
        listOf(playerRequest.player, otherRequest.player).forEach { player ->
            player.sendMessage(textComponent {
                append(prefix())
                appendSpace()
                append("Não foi possível encontrar uma arena disponível! Tente novamente mais tarde.") {
                    color(NamedTextColor.RED)
                }
            })
        }
    }

    private fun checkQueue(mode: XizumBattleMode) {
        launchMainThread {
            while (queue.isNotEmpty()) {
                for (request in queue) {
                    if (!request.processed) {
                        val duration = (System.currentTimeMillis() - request.time) / 1000
                        val timeFormatted = String.format("%02d:%02d", (duration % 3600) / 60, duration % 60)

                        request.player.sendActionBar(textComponent {
                            append("Procurando um oponente...") {
                                color(NamedTextColor.GOLD)
                            }
                            when (duration) {
                                in 0..30 -> append(" Tempo em fila: $timeFormatted") { color(NamedTextColor.GREEN) }
                                in 31..60 -> append(" Tempo em fila: $timeFormatted") { color(NamedTextColor.YELLOW) }
                                else -> append(" Tempo em fila: $timeFormatted") { color(NamedTextColor.RED) }
                            }
                        })
                    }
                }

                delayTicks(20L)
            }
        }

        when (mode) {
            XizumBattleMode.CUSTOM -> {
                for (request in queue) {
                    if (request.time > (System.currentTimeMillis() + (60 * 1000))) {
                        queue.remove(request)

                        request.player.sendActionBar(textComponent {
                            append("O seu convite para ${request.opponent!!.displayName} foi expirado!") {
                                color(NamedTextColor.RED)
                            }
                        })

                        // if it's a request, we can non-null assert it because a x1 challenge needs an opponent.
                        request.opponent!!.sendMessage(textComponent {
                            append("O convite de ${request.player.displayName} para você foi expirado!") {
                                color(NamedTextColor.RED)
                            }
                        })
                    }
                }
            }

            else -> {
                if (queue.size < 2) return

                val (playerRequest, otherRequest) = findMatchRequests(mode) ?: return

                if (playerRequest.processed || otherRequest.processed)
                    return

                // wait for 40 sticks (2 seconds) to make sure the player saw the title

                if (mode == XizumBattleMode.COMPETITIVE) {
                    val player = transaction(Databases.databaseNetwork) {
                        XizumProfile.findOrCreate(playerRequest.player.uniqueId)
                    }

                    val otherPlayer = transaction(Databases.databaseNetwork) {
                        XizumProfile.findOrCreate(otherRequest.player.uniqueId)
                    }

                    playerRequest.player.sendTitle("§c§lPartida encontrada!", "§aSeu oponente é §7[${XizumRank.getTextByRating(otherPlayer.rating)}§7] §b${otherRequest.player.name}", 20, 40, 20)
                    otherRequest.player.sendTitle("§c§lPartida encontrada!", "§aSeu oponente é §7[${XizumRank.getTextByRating(player.rating)}§7] §b${playerRequest.player.name}", 20, 40, 20)
                } else {
                    playerRequest.player.sendTitle("§c§lPartida encontrada!", "§aSeu oponente é §b${otherRequest.player.name}", 20, 40, 20)
                    otherRequest.player.sendTitle("§c§lPartida encontrada!", "§aSeu oponente é §b${playerRequest.player.name}", 20, 40, 20)
                }

                listOf(playerRequest, otherRequest).forEach {
                    it.player.playSound(it.player.location, "entity.player.levelup", 1f, 1f)
                }

                val arena = arenas.filter { !it.inUse && it.data.mode == mode }.randomOrNull() ?: return notifyNoArena(playerRequest, otherRequest)

                if (arena.data.mode == null) {
                    listOf(playerRequest.player, otherRequest.player).forEach { player ->
                        player.sendMessage(textComponent {
                            append(prefix())
                            appendSpace()
                            append("A arena §b${arena.data.arenaName} §cnão possui um modo de jogo definido! Reporte à staff do servidor!") {
                                color(NamedTextColor.RED)
                            }
                        })
                    }
                }

                val newMode = when (mode) {
                    XizumBattleMode.STANDARD -> StandardXizumMode(this)
                    XizumBattleMode.PVP_WITH_SOUP -> PvPWithSoupXizumMode(this)
                    XizumBattleMode.PVP_WITH_POTION -> PvPWithPotionXizumMode(this)
                    XizumBattleMode.COMPETITIVE -> CompetitiveXizumMode(this)
                    else -> null // should never happen
                }

                if (newMode != null) {
                    playerRequest.processed = true
                    otherRequest.processed = true

                    val battle = XizumBattle(this@DreamXizum, arena, newMode, playerRequest.player, otherRequest.player)
                    activeBattles.add(battle)
                    battle.preStart()

                    launchMainThread {
                        delayTicks(40L)

                        battle.start()
                        queue.removeAll(setOf(playerRequest, otherRequest))
                    }
                }
            }
        }
    }

    private fun findMatchRequests(mode: XizumBattleMode): Pair<XizumBattleRequest, XizumBattleRequest>? {
        when (mode) {
            XizumBattleMode.COMPETITIVE -> {
                // Players with a rating difference of 150 or less can be matched
                // If the user doesn't have this rating difference, the player will need to wait 120 seconds to be matched with anyone in the queue
                // If the player with more rating loses... well, that's their fault for being too cocky
                val ratingDifferenceThreshold = 150
                val availableRequests = queue.filter { it.mode.enum == mode && it.opponent == null }

                for (playerRequest in availableRequests) {
                    val player = transaction(Databases.databaseNetwork) {
                        XizumProfile.findOrCreate(playerRequest.player.uniqueId)
                    }

                    val matchingPlayer = availableRequests.firstOrNull {
                        val otherPlayer = transaction(Databases.databaseNetwork) {
                            XizumProfile.findOrCreate(it.player.uniqueId)
                        }

                        val timeInQueue = System.currentTimeMillis() - playerRequest.time
                        val relaxedThreshold = if (timeInQueue > MAX_TIME_IN_QUEUE) Int.MAX_VALUE else ratingDifferenceThreshold

                        it != playerRequest && abs(otherPlayer.rating - player.rating) <= relaxedThreshold
                    }

                    if (matchingPlayer != null) {
                        return Pair(playerRequest, matchingPlayer)
                    }
                }
            }

            else -> {
                val playerRequest = queue.filter { it.mode.enum == mode && it.opponent == null }.randomOrNull() ?: return null
                val otherRequest = queue.filter { it.mode.enum == mode && it.opponent == null && it.player != playerRequest.player }.randomOrNull() ?: return null

                return Pair(playerRequest, otherRequest)
            }
        }

        return null
    }
}