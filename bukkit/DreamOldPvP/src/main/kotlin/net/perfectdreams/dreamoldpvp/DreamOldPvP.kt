package net.perfectdreams.dreamoldpvp

import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamoldpvp.listeners.CombatListener

class DreamOldPvP : KotlinPlugin() {
    override fun softEnable() {
        super.softEnable()

        registerEvents(CombatListener(this))
    }

    override fun softDisable() {
        super.softDisable()
    }
}