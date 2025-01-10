package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class WurstCreativeFlight(session: ReflectionSession) : ViolationCounterModule(
    session,
    "CreativeFlight [Toggle] (Wurst)",
    "Detecta quando um player tenta ativar o modo de voo do criativo quando ele não tem a habilidade de voar",
    false,
    0,
    43_200,
    1
)