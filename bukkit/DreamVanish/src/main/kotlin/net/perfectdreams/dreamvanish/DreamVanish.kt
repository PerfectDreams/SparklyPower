package net.perfectdreams.dreamvanish

import com.okkero.skedule.schedule
import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamvanish.commands.QueroTrabalharCommand
import net.perfectdreams.dreamvanish.commands.VanishCommand
import net.perfectdreams.dreamvanish.listeners.PlayerListener
import org.bukkit.Bukkit
import org.dynmap.DynmapAPI

class DreamVanish : KotlinPlugin() {
	companion object {
		lateinit var INSTANCE: DreamVanish
	}

	override fun softEnable() {
		super.softEnable()

		INSTANCE = this

		registerCommand(QueroTrabalharCommand(this))
		registerCommand(VanishCommand(this))

		registerEvents(PlayerListener(this))

		val dynmap = Bukkit.getServer().pluginManager.getPlugin("dynmap") as DynmapAPI?

		schedule {
			while (true) {
				Bukkit.getOnlinePlayers().forEach {
					val isVanished = DreamVanishAPI.isVanished(it)
					val isQueroTrabalhar = DreamVanishAPI.isQueroTrabalhar(it)

					if (isVanished && isQueroTrabalhar)
						it.sendActionBar("§aVocê está invisível e no modo quero trabalhar!")
					else if (isVanished)
						it.sendActionBar("§aVocê está invisível! Vanish Poder O2~")
					else if (isQueroTrabalhar)
						it.sendActionBar("§aVocê está no modo quero trabalhar! Apenas trabalho, sem diversão hein grrr")

					// Set player's visibility on dynmap
					try {
						if (isVanished || isQueroTrabalhar) {
							dynmap?.setPlayerVisiblity(it, false)
						} else {
							dynmap?.setPlayerVisiblity(it, true)
						}
					} catch (e: NullPointerException) {
						// If dynmap is actually disabled
						// java.lang.NullPointerException: Cannot invoke "org.dynmap.DynmapCore.setPlayerVisiblity(String, boolean)" because "this.core" is null
					}
				}

				waitFor(20)
			}
		}
	}
}