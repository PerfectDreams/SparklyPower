package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamcore.utils.DreamUtils
import net.perfectdreams.dreamreflections.discord.ViolationDiscordNotification
import net.perfectdreams.dreamreflections.sessions.ReflectionSession

abstract class ViolationCounterModule(
    val session: ReflectionSession,
    val moduleName: String,
    val requiresMainThread: Boolean,
    val requiredViolationsUntilWarning: Int,
    val decayAfterTicks: Int = 300,
    val discordNotificationEveryXWarnings: Int = 10
) {
    var violations = 0
    var decayTicks = 0

    fun increaseViolationLevel(amount: Int = 1) {
        if (requiresMainThread) {
            DreamUtils.assertMainThread(true)
        } else {
            DreamUtils.assertAsyncThread(true)
        }

        this.violations += amount

        if (this.violations >= this.requiredViolationsUntilWarning) {
            session.m.notifyStaff(
                session.createCheckFailMessage(
                    moduleName,
                    this.violations
                )
            )

            val baseRel = if (this.requiredViolationsUntilWarning == 0)
                1
            else
                this.requiredViolationsUntilWarning

            val relative = this.violations - baseRel

            if (relative % this.discordNotificationEveryXWarnings == 0) {
                session.m.notifyDiscord(ViolationDiscordNotification.ViolationCounterDiscordNotification(this))
            }
        }
    }

    fun processDecay() {
        if (this.decayTicks >= decayAfterTicks) {
            this.decayTicks = 0
            this.violations = (this.violations - 1).coerceAtLeast(0)
        } else {
            this.decayTicks++
        }
    }
}