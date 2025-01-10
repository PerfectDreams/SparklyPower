package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class LBNoFallForceJump(session: ReflectionSession) : ViolationCounterModule(session,
    "NoFall (LiquidBounce / Force Jump)",
    "Detecta e corrige o exploit de NoFall no modo Force Jump do LiquidBounce, que faz o player pular na hora que ele encosta no chão para resetar o dano de queda",
    true,
    5
)