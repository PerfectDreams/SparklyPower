package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class LBNoFallForceJump(session: ReflectionSession) : ViolationCounterModule(session, "NoFall (LiquidBounce / Force Jump)", true, 1)