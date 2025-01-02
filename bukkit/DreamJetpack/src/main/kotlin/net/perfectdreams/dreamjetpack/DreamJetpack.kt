package net.perfectdreams.dreamjetpack

import com.okkero.skedule.schedule
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.perfectdreams.commands.annotation.Subcommand
import net.perfectdreams.commands.bukkit.SparklyCommand
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.displayNameWithoutDecorations
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamjetpack.events.PlayerJetpackCheckEvent
import net.perfectdreams.dreamxizum.DreamXizum
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.command.CommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class DreamJetpack : KotlinPlugin(), Listener {
	companion object {
		lateinit var INSTANCE: DreamJetpack
		val PREFIX = "§8[§a§lJetpack§8]§e"
		val IS_JETPACK_KEY = SparklyNamespacedBooleanKey("is_jetpack")
	}

	val flyingPlayers = mutableSetOf<Player>()
	val bossBars = mutableMapOf<Player, BossBar>()

	val jetpackType = Material.CHAINMAIL_CHESTPLATE
	var durabilityTicks = 0

	override fun softEnable() {
		super.softEnable()

		INSTANCE = this

		flyingPlayers.addAll(Bukkit.getOnlinePlayers().filter { player ->
			val chestplate = player.inventory.chestplate
			isJetpack(player, chestplate)
		})

		registerCommand(object: SparklyCommand(arrayOf("dreamjetpack"), permission = "dreamjetpack.setup") {
			@Subcommand
			fun root(sender: CommandSender) {
				reloadConfig()
				sender.sendMessage("§aReload concluído!")
			}

			@Subcommand(["give"])
			fun root(sender: Player) {
				sender.inventory.addItem(
					ItemStack(Material.CHAINMAIL_CHESTPLATE)
						.meta<ItemMeta> {
							displayNameWithoutDecorations {
								color(NamedTextColor.GOLD)
								decorate(TextDecoration.BOLD)
								content("Jetpack")
							}

							persistentDataContainer.set(IS_JETPACK_KEY, true)
						}

				)
				sender.sendMessage("§aVocê recebeu uma Jetpack!")
			}
		})

		// We spawn the particles on a separate thread because it looks smoother because we can change how many times it is triggered every tick :3
		launchMainThread {
			while (true) {
				for (player in flyingPlayers) {
					if (player.isFlying) {
						// Okay this may seem weird but Bedrock's particles are BORKED as all hell
						// Instead of the smoke floating down in a beautiful way, it goes up, and that's VERY annoying
						// So we won't send these particles to Bedrock players
						val targetParticleSpawnLocation = player.location.clone()
							.add(0.0, 0.7, 0.0)
							.add(player.location.direction.multiply(-0.3))

						for (playerInWorld in player.world.players) {
							if (!playerInWorld.isBedrockClient) {
								// Don't send jetpack particles to the current player if the player is looking down to avoid the particles being in their way
								// Helps if they are placing blocks or something
								if (playerInWorld == player && player.pitch >= 20.0)
									continue

								playerInWorld.spawnParticle(
									Particle.SMOKE,
									targetParticleSpawnLocation,
									0,
									0.0,
									-0.25,
									0.0,
									0.5
								)
							}
						}
					}
				}

				delayTicks(2L)
			}
		}

		scheduler().schedule(this) {
			while (true) {
				val toBeRemoved = mutableSetOf<Player>()

				for (player in flyingPlayers) {
					if (!player.isValid) {
						toBeRemoved.add(player)
						bossBars[player]?.removeAll()
						bossBars.remove(player)

						player.allowFlight = false
						continue
					}

					val chestplate = player.inventory.chestplate
					val isJetpack = isJetpack(player, chestplate)

					if (!isJetpack || chestplate == null) {
						toBeRemoved.add(player)
						bossBars[player]?.removeAll()
						bossBars.remove(player)

						player.sendMessage("$PREFIX §cVocê não está mais usando a Jetpack!")
						player.allowFlight = false
						continue
					}

					val blacklistedWorlds = config.getStringList("blacklisted-worlds")

					if (blacklistedWorlds.contains(player.world.name)) {
						toBeRemoved.add(player)
						bossBars[player]?.removeAll()
						bossBars.remove(player)

						player.sendMessage("$PREFIX §cVocê não pode voar aqui")
						player.allowFlight = false
						continue
					}

					if (!player.isFlying)
						continue

					if (chestplate.containsEnchantment(Enchantment.MENDING))
						chestplate.removeEnchantment(Enchantment.MENDING)

					val jetpackCheckEvent = PlayerJetpackCheckEvent(player)
					val success = jetpackCheckEvent.callEvent()
					if (!success) {
						// Event was cancelled! This means that the player should stop flying
						toBeRemoved.add(player)
						bossBars[player]?.removeAll()
						bossBars.remove(player)

						player.allowFlight = false
						continue
					}

					val meta = chestplate.itemMeta as org.bukkit.inventory.meta.Damageable

					val applyDamage = when {
						player.hasPermission("dreamjetpack.vip++") -> 0
						player.hasPermission("dreamjetpack.vip+") -> 1
						player.hasPermission("dreamjetpack.vip") -> 1
						else -> 1
					}

					// Before this was 180 for every rank, but now we will control exactly when the damage is applied
					// VIP++: Unlimited
					// VIP+: 8 hours
					// VIP: 4 hours
					// Normal players: One hour
					val takeDamageEvery = when {
						player.hasPermission("dreamjetpack.vip++") -> 120 // Unused
						player.hasPermission("dreamjetpack.vip+") -> 120
						player.hasPermission("dreamjetpack.vip") -> 60
						else -> 15
					}

					if (durabilityTicks % takeDamageEvery == 0) {
						if (applyDamage == 0) {
							meta.damage = 0
						} else {
							meta.damage += applyDamage
						}
					}

					if (meta.damage > 240) {
						toBeRemoved.add(player)
						bossBars[player]?.removeAll()
						bossBars.remove(player)

						player.inventory.chestplate = ItemStack(Material.AIR)
						player.sendMessage("$PREFIX §cSua Jetpack está toda detonada e explodiu!")
						player.allowFlight = false
						continue
					}

					if (!bossBars.contains(player)) {
						val bossBar = Bukkit.createBossBar("...", BarColor.GREEN, BarStyle.SEGMENTED_20)
						bossBars[player] = bossBar
						bossBar.addPlayer(player)
					}

					val bossBar = bossBars[player]
					if (bossBar != null) {
						val percentageRemaining = (240 - meta.damage).toDouble() / 240
						bossBar.progress = percentageRemaining

						bossBar.color = when {
							player.hasPermission("dreamjetpack.vip++") -> BarColor.PINK
							percentageRemaining >= 0.75 -> BarColor.GREEN
							percentageRemaining >= 0.25 -> BarColor.YELLOW
							else -> BarColor.RED
						}

						val timeRemaining = if (applyDamage == 0)
							-1
						else ((takeDamageEvery * (240 - meta.damage) / applyDamage) - durabilityTicks % takeDamageEvery)

						val minutes = timeRemaining / 60
						val seconds = timeRemaining % 60

						if (player.isBedrockClient) {
							bossBar.setTitle(
								if (applyDamage == 0) {
									"§dWoosh! §aVocê pode voar por mais §e∞§a!"
								} else {
									"§dWoosh! §aVocê pode voar por mais §e$minutes minutos e $seconds segundos§a!"
								}
							)
						} else {
							bossBar.setTitle(
								if (applyDamage == 0) {
									"§dWoosh! §aVocê pode voar por mais §e∞§a!"
								} else {
									"§dWoosh! §aVocê pode voar por mais §e$minutes minutos e $seconds segundos§a!"
								}
							)
						}
					}

					chestplate.itemMeta = meta
				}

				flyingPlayers.removeAll(toBeRemoved)

				durabilityTicks++

				waitFor(20)
			}
		}

		registerEvents(this)
	}

	private fun isInXizum(player: Player): Boolean {
		return DreamXizum.INSTANCE.activeBattles.any { it.player == player || it.opponent == player }
	}

	@EventHandler(priority = EventPriority.LOWEST)
	fun onTeleport(e: PlayerTeleportEvent) {
		val blacklistedWorlds = config.getStringList("blacklisted-worlds")

		if (flyingPlayers.contains(e.player) && e.to.world.name in blacklistedWorlds || isInXizum(e.player)) {
			bossBars[e.player]?.removeAll()
			bossBars.remove(e.player)

			flyingPlayers.remove(e.player)
			e.player.allowFlight = false
			e.player.sendMessage("$PREFIX §cVocê não pode voar aqui")
		}
	}

	@EventHandler
	fun onShift(e: PlayerToggleSneakEvent) {
		val chestplate = e.player.inventory.chestplate

		if (e.player.isOnGround && e.isSneaking && chestplate?.type == jetpackType) {
			val isJetpack = isJetpack(e.player, chestplate)

			if (!isJetpack) {
				e.player.sendMessage("$PREFIX §cQue isso, comprou essa Jetpack no barzinho da esquina? Essa Jetpack é um risco a sua vida! Você apenas pode voar com jetpacks compradas na §6/loja§c!")
			} else {
				if (!e.player.allowFlight) {
					val blacklistedWorlds = config.getStringList("blacklisted-worlds")

					if (blacklistedWorlds.contains(e.player.world.name) || isInXizum(e.player)) {
						e.player.sendMessage("$PREFIX §cVocê não pode voar aqui")
						return
					}

					val jetpackCheckEvent = PlayerJetpackCheckEvent(e.player)
					val success = jetpackCheckEvent.callEvent()
					if (!success) {
						// Check event wasn't a success, bail out!
						return
					}

					e.player.allowFlight = true

					flyingPlayers.add(e.player)

					e.player.sendActionBar(
						textComponent {
							color(NamedTextColor.GREEN)
							content("Woosh! Você ativou a sua Jetpack!")
						}
					)
				} else {
					e.player.allowFlight = false

					flyingPlayers.remove(e.player)
					bossBars[e.player]?.removeAll()
					bossBars.remove(e.player)

					e.player.sendActionBar(
						textComponent {
							color(NamedTextColor.GREEN)
							content("Você desativou a sua Jetpack!")
						}
					)
				}
			}
		}
	}

	fun isJetpack(player: Player, chestplate: ItemStack?): Boolean {
		if (chestplate == null)
			return false

		if (chestplate.hasItemMeta()) {
			val itemMeta = chestplate.itemMeta
			if (itemMeta.persistentDataContainer.get(IS_JETPACK_KEY))
				return true

			if (chestplate.itemMeta.displayName == "§6§lJetpack") {
				itemMeta.persistentDataContainer.set(IS_JETPACK_KEY, true)
				chestplate.itemMeta = itemMeta
				return true
			}
		}

		return false
	}
}