package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class LBNoFallHoplite(session: ReflectionSession) : ViolationCounterModule(
    session,
    "NoFall (LiquidBounce / Hoplite)",
    "Detecta o exploit de NoFall no modo Hoplite do LiquidBounce, que ele envia um pacote de movimento falando \"eu me movi 0.0000001 blocos para cima, ok??\" causando o servidor resetar o dano de queda",
    false,
    10
) {
    var previousMovePacket: ServerboundMovePlayerPacket? = null
}