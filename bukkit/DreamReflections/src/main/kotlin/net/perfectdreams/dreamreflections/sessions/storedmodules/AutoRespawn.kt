package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamcore.utils.DreamUtils
import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class AutoRespawn(val session: ReflectionSession) {
    companion object {
        const val SUSPICIOUS_MILLISECONDS = 250
    }

    private val respawnTimes = ArrayDeque<Long>()

    fun trackTime(durationMs: Long) {
        DreamUtils.assertAsyncThread(true)

        respawnTimes.add(durationMs)

        if (SUSPICIOUS_MILLISECONDS >= durationMs) {
            session.m.notifyStaff(
                session.createCheckFailMessage(
                    "AutoRespawn",
                    "Respawnou em ${durationMs}ms"
                )
            )
        }
    }

    fun getSuspiciousRespawns(): List<Long> {
        return respawnTimes.filter { SUSPICIOUS_MILLISECONDS >= it }
    }
}