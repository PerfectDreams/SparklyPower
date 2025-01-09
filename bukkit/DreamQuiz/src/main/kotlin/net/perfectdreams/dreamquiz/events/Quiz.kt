package net.perfectdreams.dreamquiz.events

import com.destroystokyo.paper.Title
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import net.perfectdreams.dreamcash.utils.Cash
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.eventmanager.ServerEvent
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.extensions.isWithinRegion
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawn
import net.perfectdreams.dreamcore.utils.extensions.teleportToServerSpawnWithEffects
import net.perfectdreams.dreamquiz.DreamQuiz
import net.perfectdreams.dreamquiz.utils.QuizQuestion
import net.perfectdreams.dreamquiz.utils.prettyBoolean
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*

class Quiz(val m: DreamQuiz) : ServerEvent("Quiz", "") {
    val alreadyAskedQuestionsToPlayer = mutableMapOf<Player, MutableList<QuizQuestion>>()
    // val alreadyAskedQuestions = mutableListOf<QuizQuestion>()
    val winners = mutableListOf<UUID>()

    val players get() = DreamQuiz.CONFIG.spawn.toLocation().world.players
    var started = false

    override fun getWarmUpAnnouncementMessage(idx: Int): Any {
        return "${DreamQuiz.PREFIX} O Evento Quiz começará em $idx segundos! Use §6/quiz§e para entrar!"
    }

    init {
        this.requiredPlayers = 15
        this.discordAnnouncementRole = 555184377504464898L
        this.delayBetween = 3_600_000  // one hour
        this.command = "/quiz"
    }

    override fun preStart() {
        started = false
        running = true

        countdown()
    }

    override fun start() {
        winners.clear()
        alreadyAskedQuestionsToPlayer.clear()
        started = true

        players.forEach {
            for (player in Bukkit.getOnlinePlayers()) {
                it.hidePlayer(m, player)
            }
        }

        scheduler().schedule(m) {
            repeat(7) { index ->
                val map = mutableMapOf<Player, BossBar>()
                val playerQuestions = mutableMapOf<Player, QuizQuestion>()

                for (player in players) {
                    val alreadyAskedQuestions = alreadyAskedQuestionsToPlayer.getOrPut(player) { mutableListOf() }
                    val question = DreamQuiz.CONFIG.questions.filter { it !in alreadyAskedQuestions }.random()
                    playerQuestions[player] = question

                    player.sendMessage("${DreamQuiz.PREFIX} §2Verdadeiro §eou §4Falso§e? ${question.question}")
                    player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20 * 15, 0))

                    alreadyAskedQuestions.add(question)

                    val bar = Bukkit.createBossBar("§6§l${question.question}", BarColor.YELLOW, BarStyle.SEGMENTED_20)
                    bar.addPlayer(player)

                    map[player] = bar
                }

                /* val question = DreamQuiz.CONFIG.questions.filter { it !in alreadyAskedQuestions }.random()
                for (player in players) {
                    player.sendMessage("${DreamQuiz.PREFIX} §2Verdadeiro §eou §4Falso§e? ${question.question}")
                    player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20 * 15, 0))
                }

                val bar = Bukkit.createBossBar("§6§l${question.question}", BarColor.YELLOW, BarStyle.SEGMENTED_20)
                players.forEach { bar.addPlayer(it) } */

                for (i in 7 downTo 1) {
                    players.forEach {
                        it.sendActionBar("§7O resultado será revelado em $i segundo${if (i != 1) "s" else ""}...")
                    }

                    map.values.forEach { bar ->
                        bar.progress -= 0.13
                    }

                    waitFor(20)
                }

                map.forEach { (player, bar) ->
                    bar.removePlayer(player)
                }

                players.forEach {
                    val question = playerQuestions[it] ?: DreamQuiz.CONFIG.questions.first()

                    it.sendMessage("${DreamQuiz.PREFIX} E o resultado é... ${question.answer.prettyBoolean().toUpperCase()}")
                    it.sendTitle(Title("§7E o resultado é...", question.answer.prettyBoolean().toUpperCase(), 0, 60, 0))
                }

                players.forEach {
                    val question = playerQuestions[it] ?: DreamQuiz.CONFIG.questions.first()

                    val isRight = isPlayerIsOnRightAnswer(it, question.answer)
                    val isWrong = isPlayerIsOnWrongAnswer(it, question.answer)
                    val isUndefined = isPlayerIsOnUndefined(it, question.answer)

                    if (isRight) {
                        val balance = (index + 1) * 268

                        it.sendMessage("${DreamQuiz.PREFIX} Yay! Você acertou! Parabéns! §a+$balance sonecas")
                        it.deposit(balance.toDouble(), TransactionContext(type = TransactionType.EVENTS, extra = "Quiz"))

                        it.playSound(it.location, "perfectdreams.sfx.mizeravi", 10.0f, 1.0f)
                    } else if (isWrong) {
                        it.sendMessage("${DreamQuiz.PREFIX} Que pena... você errou! :(")
                        it.teleportToServerSpawnWithEffects()

                        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                            it.showPlayer(m, onlinePlayer)
                        }

                        it.playSound(it.location, "perfectdreams.sfx.errou", 10.0f, 1.0f)
                    } else {
                        it.sendMessage("${DreamQuiz.PREFIX} Você estava indeciso e não soube escolher nenhum dos dois... Que pena.")
                        it.teleportToServerSpawnWithEffects()

                        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                            it.showPlayer(m, onlinePlayer)
                        }

                        it.playSound(it.location, "perfectdreams.sfx.errou", 10.0f, 1.0f)
                    }
                }

                if (players.isEmpty()) {
                    Bukkit.broadcastMessage("${DreamQuiz.PREFIX} O Evento Quiz acabou! Ninguém ganhou o Evento Quiz! Eu achava que vocês eram mais inteligentes...")
                    running = false
                    started = false
                    lastTime = System.currentTimeMillis()

                    return@schedule
                }

                waitFor(20 * 2) // smol 2s delay

                players.forEach {
                    it.teleport(DreamQuiz.CONFIG.spawn.toLocation())
                }
            }

            running = false
            started = false

            Bukkit.broadcastMessage("${DreamQuiz.PREFIX} O Evento Quiz acabou!")

            val moreThanOne = players.size != 1
            val message = if (players.isNotEmpty()) {
                "${DreamQuiz.PREFIX} Parabéns ao${if (moreThanOne) "s" else ""} player${if (moreThanOne) "s" else ""} ${players.joinToString(", " , transform = { "§b${it.name}§e" })}! Eles ganharam §c2 pesadelos§e!"
            } else {
                "${DreamQuiz.PREFIX} Ninguém ganhou o Evento Quiz! Eu achava que vocês eram mais inteligentes..."
            }

            winners.addAll(players.map { it.uniqueId })

            alreadyAskedQuestionsToPlayer.clear()

            Bukkit.broadcastMessage(message)
    
            lastTime = System.currentTimeMillis()

            switchContext(SynchronizationContext.SYNC)
            val finalPlayersInTheQuiz = players.toMutableList()
            finalPlayersInTheQuiz.forEach {
                it.teleportToServerSpawnWithEffects()

                for (player in Bukkit.getOnlinePlayers()) {
                    it.showPlayer(m, player)
                }
            }

            scheduler().schedule(m, SynchronizationContext.ASYNC) {
                val wonAt = System.currentTimeMillis()

                finalPlayersInTheQuiz.forEach {
                    DreamCore.INSTANCE.dreamEventManager.addEventVictory(
                        it,
                        "Quiz",
                        wonAt
                    )

                    Cash.giveCash(it, 2, TransactionContext(type = TransactionType.EVENTS, extra = "Quiz"))
                }
            }
        }
    }

    fun isPlayerIsOnRightAnswer(player: Player, answer: Boolean) = player.location.isWithinRegion("$answer")
    fun isPlayerIsOnWrongAnswer(player: Player, answer: Boolean) = player.location.isWithinRegion("${!answer}")
    fun isPlayerIsOnUndefined(player: Player, answer: Boolean) = !player.location.isWithinRegion("true") && !player.location.isWithinRegion("false")

    fun getRightPlayers(answer: Boolean): List<Player> {
        return players.filter { it.location.isWithinRegion("$answer") }
    }

    fun getWrongPlayers(answer: Boolean): List<Player> {
        return players.filter { it.location.isWithinRegion("${!answer}") }
    }
}
