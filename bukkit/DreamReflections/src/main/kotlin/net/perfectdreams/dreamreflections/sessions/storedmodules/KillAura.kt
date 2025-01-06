package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class KillAura(session: ReflectionSession) : ViolationCounterModule(session, "KillAura (Players Invisíveis)", true, 25) {
    var lastAutomaticKillAuraCheck = 0L
}