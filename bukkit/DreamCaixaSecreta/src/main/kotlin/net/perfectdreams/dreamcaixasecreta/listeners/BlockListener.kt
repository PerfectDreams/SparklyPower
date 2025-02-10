package net.perfectdreams.dreamcaixasecreta.listeners

import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import com.xxmicloxx.NoteBlockAPI.songplayer.RadioSongPlayer
import net.perfectdreams.dreamcaixasecreta.DreamCaixaSecreta
import net.perfectdreams.dreamcash.utils.Cash
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.extensions.isWithinRegion
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.time.ZoneId

class BlockListener(val m: DreamCaixaSecreta) : Listener {
	companion object {
		private const val DISCORD_COLOR = "§x§7§2§8§9§d§a"

		// TODO: We should add DreamPicaretaMonstra as a dependency, but that creates a cyclic dependency...
		//  So we do this crappy hack instead, maybe we should move DreamPicaretaMonstra to DreamCustomItems
		val IS_MONSTER_TOOL_KEY = SparklyNamespacedBooleanKey("is_monster_tool")
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	fun onPlace(e: PlayerInteractEvent) {
		if (e.action != Action.RIGHT_CLICK_BLOCK)
			return

		val item = e.item
		if (item?.type != Material.CHEST)
			return

		if (!item.hasItemMeta())
			return

		if (e.player.location.isWithinRegion("spawn") && !e.player.hasPermission("dreammini.bypassdropblock"))
			return

		val data = item.itemMeta.persistentDataContainer.get(DreamCaixaSecreta.CAIXA_SECRETA_LEVEL_KEY) ?: return
		val caixaSecretaWorld = item.itemMeta.persistentDataContainer.get(DreamCaixaSecreta.CAIXA_SECRETA_WORLD_KEY)

		val level = data.toInt()

		e.isCancelled = true
		e.item!!.amount -= 1

		val items = mutableListOf<ItemStack>()
		var amount = 0

		if (caixaSecretaWorld == "Resources") {
			val sonecasChance = chanceMultiplied(1.0, level)
			val pesadelosChance = chanceMultiplied(0.1, level)
			val nitroClassicChance = chanceMultiplied(0.008, level)

			if (chance(sonecasChance)) {
				val sonecas = DreamUtils.random.nextInt(25_000, 50_001)

				Bukkit.broadcastMessage("§b${e.player.displayName}§a conseguiu §2§l$sonecas sonecas§a pela caixa secreta! Parabéns!!")
				e.player.deposit(sonecas.toDouble(), TransactionContext(type = TransactionType.SECRET_BOXES))

				try {
					m.nitroNotifyWebhook.send("`${e.player.name}` conseguiu `$sonecas` sonecas pela caixa secreta!")
				} catch(e: Exception) {
					e.printStackTrace()
				}

			} else if (chance(pesadelosChance)) {
				val pesadelos = DreamUtils.random.nextInt(25, 51)

				Bukkit.broadcastMessage("§b${e.player.displayName}§a conseguiu §c§l$pesadelos Pesadelos§a pela caixa secreta! Parabéns!!")

				scheduler().schedule(m, SynchronizationContext.ASYNC) {
					Cash.giveCash(e.player, pesadelos.toLong(), TransactionContext(type = TransactionType.SECRET_BOXES))
				}

				try {
					m.nitroNotifyWebhook.send("`${e.player.name}` conseguiu `$pesadelos` pela caixa secreta!")
				} catch(e: Exception) {
					e.printStackTrace()
				}

			} else if (chance(nitroClassicChance)) {
				Bukkit.broadcastMessage("§b${e.player.displayName}§a conseguiu ${DISCORD_COLOR}Um Nitro no Discord§a pela caixa secreta! Parabéns!!")

				val now = Instant.now()
					.atZone(ZoneId.of("America/Sao_Paulo"))

				val year = now.year
				val month = now.monthValue.toString().padStart(2, '0')
				val day = now.dayOfMonth.toString().padStart(2, '0')

				items.add(
					ItemStack(Material.TRIPWIRE_HOOK)
						.rename("${DISCORD_COLOR}Nitro Classic")
						.lore("§aPara receber o seu prêmio, abra um ticket no", "§anosso Discord!", "§a", "§7Prêmio de §b${e.player.name}", "§a", "§aData: §2$day/$month/$year")
				)

				try {
					m.nitroNotifyWebhook.send("@everyone :money_with_wings: `${e.player.name}` conseguiu um Nitro pela caixa secreta!")
				} catch(e: Exception) {
					e.printStackTrace()
				}
			}
		}

		for (item in m.prizes) {
			val chance = chanceMultiplied(item.chance, level)

			if (chance(chance)) {
				val itemStack = item.itemStack.clone()

				if (item.randomEnchant) {
					Enchantment.values()
						.filter { it.canEnchantItem(itemStack) }
						.forEach {
							if (chance(25 * chance)) {
								itemStack.addEnchantment(it, DreamUtils.random.nextInt(1, it.maxLevel + 1))
							}
						}
				}

				items.add(item.itemStack)
				amount += item.itemStack.amount
			}
		}

		val location = e.clickedBlock!!.location.add(0.5, 1.0, 0.5)
		if (items.isNotEmpty()) {
			items.forEach {
				e.player.world.dropItemNaturally(location, it)
			}

			val sp = RadioSongPlayer(m.itemReceived)
			sp.autoDestroy = true
			sp.addPlayer(e.player)
			sp.isPlaying = true

			InstantFirework.spawn(location, FireworkEffect.builder().withColor(Color.GREEN).flicker(true).trail(true).withFade(Color.YELLOW).with(FireworkEffect.Type.STAR).build())
			e.player.sendTitle("§aParabéns!", "§aVocê ganhou §9" + amount + " ite" + (if (items.size == 1) "m" else "ns") + "§a!", 10, 100, 10)
		} else {
			e.player.world.spawnParticle(Particle.ANGRY_VILLAGER, location, 5, 0.5, 0.5, 0.5);
			e.player.sendTitle("§cQue pena...", "§cVocê não ganhou nada...", 10, 100, 10);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	fun onBlock(e: BlockBreakEvent) {
		if (e.block.type != Material.STONE && e.block.type != Material.DEEPSLATE && e.block.type != Material.TUFF)
			return

		if (e.player.inventory.itemInMainHand.containsEnchantment(Enchantment.SILK_TOUCH))
			return

		// Do not drop if it is a monster pickaxe
		if (e.player.inventory.itemInMainHand.hasItemMeta() && e.player.inventory.itemInMainHand.itemMeta.persistentDataContainer.get(IS_MONSTER_TOOL_KEY))
			return

		val chance = 0.8

		if (chance(chance)) {
			val random = DreamUtils.random.nextInt(0, 100)

			val level = when (random) {
				in 99..99 -> 4
				in 90..98 -> 3
				in 75..89 -> 2
				in 60..74 -> 1
				else -> 0
			}

			val item = m.generateCaixaSecreta(
				level,
				e.player.world.name
			)

			e.player.world.dropItemNaturally(e.block.location, item)
		}
	}

	private fun chanceMultiplied(value: Double, level: Int): Double {
		var chance = value

		when (level) {
			4 -> chance *= 2
			3 -> chance *= 1.75
			2 -> chance *= 1.5
			1 -> chance *= 1.25
		}

		return chance
	}
}