package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class NoFall(session: ReflectionSession) : ViolationCounterModule(
    session,
    "NoFall",
    "Detecta e corrige o status de \"no chão\" enviados por players, para confiar no servidor e não no player, assim corrigindo exploits de NoFall",
    true,
    10
)