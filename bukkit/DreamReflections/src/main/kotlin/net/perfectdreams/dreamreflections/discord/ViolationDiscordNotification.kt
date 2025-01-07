package net.perfectdreams.dreamreflections.discord

import net.perfectdreams.dreamreflections.sessions.storedmodules.ViolationCounterModule

sealed class ViolationDiscordNotification {
    class ViolationCounterDiscordNotification(val module: ViolationCounterModule) : ViolationDiscordNotification()
}