package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class BoatFly(session: ReflectionSession) : ViolationCounterModule(session, "BoatFly", "Detecta e corrige se o player tentou voar verticalmente com um veículo (barcos, cavalos, etc)", true, 5)