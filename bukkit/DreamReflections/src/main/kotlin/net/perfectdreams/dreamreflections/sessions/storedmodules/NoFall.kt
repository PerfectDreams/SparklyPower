package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class NoFall(session: ReflectionSession) : ViolationCounterModule(session, "NoFall", true, 10)