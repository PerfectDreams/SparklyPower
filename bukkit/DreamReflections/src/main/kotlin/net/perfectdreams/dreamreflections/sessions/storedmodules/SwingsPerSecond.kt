package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamcore.utils.DreamUtils
import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class SwingsPerSecond(val session: ReflectionSession) {
    companion object {
        private const val DELAY_BETWEEN_MESSAGES = 1_000
    }

    val swings = ArrayDeque<Long>()
    var lastSpeedWarn = 0L
    var lastConsistencyWarn = 0L

    fun addNewSwing() {
        DreamUtils.assertAsyncThread(true)

        val now = System.currentTimeMillis()

        swings.add(now)

        val oldest = now - 5_000

        while (swings.isNotEmpty()) {
            // peek!
            val value = swings.first()

            if (oldest > value) {
                swings.removeFirst() // it is EXPIRED!
            } else {
                break
            }
        }

        val cps = getClicksPerSecond()

        if (cps >= 25.0) {
            val diff = System.currentTimeMillis() - this.lastSpeedWarn

            if (diff >= DELAY_BETWEEN_MESSAGES) {
                this.lastSpeedWarn = now
                session.m.notifyStaff(
                    session.createCheckFailMessage(
                        "AutoClick (Velocidade)",
                        "$cps CPS"
                    )
                )
            }
        }

        // We will now split each epoch millis into "ticks"
        var currentTick = now / 50
        var currentStreak = 0

        for (swingEpochMillis in swings.asReversed()) {
            val swingTick = swingEpochMillis / 50

            if (swingTick == currentTick - 1) {
                currentStreak++
                currentTick = swingTick
            } else if (swingTick != currentTick) {
                break
            }
        }

        if (currentStreak >= 100) {
            val diff = System.currentTimeMillis() - this.lastConsistencyWarn
            if (diff >= this.lastConsistencyWarn) {
                this.lastConsistencyWarn = now
                session.m.notifyStaff(
                    session.createCheckFailMessage(
                        "AutoClick (Consistência)",
                        "Clicou por $currentStreak+ ticks seguidos"
                    )
                )
            }
        }
    }

    fun getClicksPerSecond(): Double {
        return swings.size.toDouble() / 5
    }
}