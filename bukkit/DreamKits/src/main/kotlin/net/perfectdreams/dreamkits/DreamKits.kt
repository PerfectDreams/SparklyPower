package net.perfectdreams.dreamkits

import com.github.salomonbrys.kotson.fromJson
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.DreamUtils
import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamcorreios.DreamCorreios
import net.perfectdreams.dreamcorreios.utils.addItemIfPossibleOrAddToPlayerMailbox
import net.perfectdreams.dreamkits.commands.KitCommand
import net.perfectdreams.dreamkits.tables.Kits
import net.perfectdreams.dreamkits.utils.Kit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

class DreamKits : KotlinPlugin(), Listener {
	companion object {
		val PREFIX = textComponent {
			append("[") {
				color(NamedTextColor.DARK_GRAY)
			}

			append("Kits") {
				color(NamedTextColor.GREEN)
				decorate(TextDecoration.BOLD)
			}

			append("]") {
				color(NamedTextColor.DARK_GRAY)
			}
		}
	}

	var kits = mutableListOf<Kit>()

	override fun softEnable() {
		super.softEnable()

		transaction(Databases.databaseNetwork) {
			SchemaUtils.create(Kits)
		}

		registerCommand(KitCommand(this))
		registerEvents(this)

		loadKits()
	}

	override fun softDisable() {
		super.softDisable()
	}

	@EventHandler
	fun onJoin(e: PlayerJoinEvent) {
		if (e.player.hasPlayedBefore())
			return

		kits.filter { it.giveOnFirstJoin }.forEach {
			giveKit(e.player, it)
		}
	}

	fun giveKit(player: Player, kit: Kit) {
		// We need to clone because DreamCorreios is dumb sometimes
		// TODO: Properly implement potions, this is a hack!!!
		// TODO: Do we really need to clone?
		val items = kit.items.map { it.clone() }.toMutableList()
		if (kit.name.equals("gladiador", true)) {
			items.add(
				ItemStack(Material.POTION)
					.meta<PotionMeta> {
						this.addCustomEffect(PotionEffect(PotionEffectType.STRENGTH, 6000, 1), true)
					}
			)
			items.add(
				ItemStack(Material.POTION)
					.meta<PotionMeta> {
						this.addCustomEffect(PotionEffect(PotionEffectType.HASTE, 3000, 1), true)
					}
			)
		}

		player.addItemIfPossibleOrAddToPlayerMailbox(*items.toTypedArray())
	}

	fun loadKits() {
		val _kits = mutableListOf<Kit>()

		val folder = File(dataFolder, "kits/")

		for (file in folder.listFiles()) {
			_kits.add(DreamUtils.gson.fromJson(file.readText()))
		}

		kits = _kits
	}
}