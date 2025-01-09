package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class LBNoFallHoplite(session: ReflectionSession) : ViolationCounterModule(session, "NoFall (LiquidBounce / Hoplite)", false, 10) {
    var previousMovePacket: ServerboundMovePlayerPacket? = null
}