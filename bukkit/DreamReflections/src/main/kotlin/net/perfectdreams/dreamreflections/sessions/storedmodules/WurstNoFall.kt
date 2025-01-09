package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class WurstNoFall(session: ReflectionSession) : ViolationCounterModule(session, "NoFall (Wurst)", false, 10) {
    var receivedStatusOnlyPacketWithOnGroundTrue = false
}