package net.perfectdreams.dreamreflections.sessions.storedmodules

import net.perfectdreams.dreamreflections.sessions.ReflectionSession

class WurstNoFall(session: ReflectionSession) : ViolationCounterModule(session, "NoFall (Wurst)", "Detecta o exploit de NoFall do Wurst, que ele envia um pacote de status de movimento indicando que o player está no chão logo após mandar um pacote de \"estou voando\"", false, 10) {
    var receivedStatusOnlyPacketWithOnGroundTrue = false
}