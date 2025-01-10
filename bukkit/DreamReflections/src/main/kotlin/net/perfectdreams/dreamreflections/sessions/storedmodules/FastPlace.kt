package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class FastPlace(session: ReflectionSession) : ViolationCounterModule(session, "FastPlace", null, false, 5) {
    var lastPlacedBlockTime = 0L
}