package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class WurstNoFall(session: ReflectionSession) : ViolationCounterModule(session, "NoFall (Wurst)", true, 5) {
    var receivedMovementPacketOnThisTick = false
    var receivedStatusOnlyPacketWithOnGroundTrueOnThisTick = false
    var ignoreStatusOnlyPacketsUntil = 0L
}