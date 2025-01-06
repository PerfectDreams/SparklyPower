package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamcore.utils.DreamUtils
import net.perfectdreams.dreamreflections.sessions.ReflectionSession

abstract class ViolationCounterModule(
    val session: ReflectionSession,
    val moduleName: String,
    val requiresMainThread: Boolean,
    val requiredViolationsUntilWarning: Int,
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

        if (this.violations >= requiredViolationsUntilWarning) {
            session.m.notifyStaff(
                session.createCheckFailMessage(
                    moduleName,
                    this.violations
                )
            )
        }
    }

    fun processDecay() {
        if (this.decayTicks >= 300) {
            this.decayTicks = 0
            this.violations = (this.violations - 1).coerceAtLeast(0)
        } else {
            this.decayTicks++
        }
    }
}