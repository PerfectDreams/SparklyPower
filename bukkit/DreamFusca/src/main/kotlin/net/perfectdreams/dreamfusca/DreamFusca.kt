package net.perfectdreams.dreamfusca

import com.comphenix.packetwrapper.WrapperPlayClientSteerVehicle
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketEvent
import com.github.salomonbrys.kotson.fromJson
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import me.ryanhamshire.GriefPrevention.GriefPrevention
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.commands.*
import net.perfectdreams.dreamcore.utils.extensions.canPlaceAt
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamfusca.utils.CarInfo
import net.perfectdreams.dreamfusca.utils.CarType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.EntityType
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.vehicle.VehicleDestroyEvent
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.event.vehicle.VehicleExitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.io.File
import java.util.*

class DreamFusca : KotlinPlugin(), Listener {
	companion object {
		val FUSCA_INFO_KEY = SparklyNamespacedKey("fusca_info", PersistentDataType.STRING)
		val IS_FUSCA_CHECK_KEY = SparklyNamespacedBooleanKey("is_fusca")
	}

	val blocks = listOf(
		Material.BLACK_CONCRETE,
		Material.STONE_SLAB,
		Material.BLACK_WOOL,
		Material.BLACK_TERRACOTTA,
		Material.COAL_BLOCK,
		Material.BLACK_CONCRETE_POWDER,
		Material.GRAY_CONCRETE,
		Material.SPARKLYPOWER_ASPHALT_SERVER,
		Material.SPARKLYPOWER_ASPHALT_PLAYER,
		Material.SPARKLYPOWER_ASPHALT_SERVER_SLAB,
		Material.SPARKLYPOWER_ASPHALT_PLAYER_SLAB,
	)

	var cars = mutableMapOf<UUID, CarInfo>()

	override fun softEnable() {
		super.softEnable()

		val file = File(dataFolder, "cars.json")

		if (file.exists()) {
			cars = DreamUtils.gson.fromJson(file.readText())
		}

		registerEvents(this)

		scheduler().schedule(this, SynchronizationContext.ASYNC) {
			while (true) {
				waitFor(20 * 900)
				file.writeText(DreamUtils.gson.toJson(cars))
			}
		}

		registerCommand(command("DreamFuscaCommand", listOf("dreamfusca")) {
			permission = "dreamfusca.spawncarro"

			executes {
				player.sendMessage("§e/dreamfusca give")
			}
		})

		registerCommand(command("DreamFuscaCommand", listOf("dreamfusca give")) {
			permission = "dreamfusca.spawncarro"

			executes {
				var target: Player? = player

				args.getOrNull(0)?.let {
					target = Bukkit.getPlayer(it)
				}

				if (target == null) {
					player.sendMessage("§cPlayer inexistente!")
					return@executes
				}

				val carInfo = CarInfo(
					target!!.uniqueId,
					target!!.name,
					CarType.FUSCA
				)

				val itemStack = ItemStack(Material.MINECART)
					.rename("§3§lFusca")
					.lore("§7Fusca de §b${target?.name}")
					.meta<ItemMeta> {
						persistentDataContainer.set(FUSCA_INFO_KEY, DreamUtils.gson.toJson(carInfo))
						persistentDataContainer.set(IS_FUSCA_CHECK_KEY, true)
					}

				player.inventory.addItem(itemStack)
			}
		})

		launchMainThread {
			while (true) {
				for (player in Bukkit.getOnlinePlayers()) {
					val vehicle = player.vehicle ?: continue

					if (player.isInsideVehicle && vehicle.type == EntityType.MINECART && cars.contains(vehicle.uniqueId)) {
						val input = player.currentInput

						val minecart = vehicle as Minecart
						minecart.maxSpeed = 100.0
						var velocity = player.location.direction.setY(0)
						val blockBelow = minecart.location.block.getRelative(BlockFace.DOWN)

						val isRoad = isRoad(blockBelow)

						if (input.isForward) {
							if (isRoad) {
								velocity = velocity.multiply(1.20)
							} else {
								velocity = velocity.multiply(0.25)
							}
						} else if (input.isBackward) {
							velocity = velocity.multiply(-0.25)
						} else {
							velocity = vehicle.velocity // Resetar velocidade
						}

						// Players are normallyshifted a lil bit to the ground when inside a minecart
						// So we are going to shift it a lil bit
						val playerLocationShiftedALittleBitAboveTheGround = player.location.clone().add(0.0, 0.5, 0.0)

						val blockFace = yawToFace(playerLocationShiftedALittleBitAboveTheGround.yaw)
						val inFrontOf = playerLocationShiftedALittleBitAboveTheGround.block.getRelative(blockFace).type

						if (inFrontOf.name.contains("SLAB")) {
							velocity = velocity.setY(velocity.y + 0.5)
						} else if (blockBelow.type == Material.AIR) {
							velocity = velocity.setY(velocity.y - 0.5)
						}

						val sendParticles = vehicle.velocity != velocity
						vehicle.velocity = velocity

						if (sendParticles) {
							vehicle.world.spawnParticle(
								Particle.CAMPFIRE_COSY_SMOKE,
								vehicle.location.clone().add(
									velocity.multiply(-2)
								),
								0,
								0.0,
								0.01,
								0.0
							)
						}
					}
				}
				delayTicks(1L)
			}
		}
	}

	override fun softDisable() {
		super.softDisable()
		val file = File(dataFolder, "cars.json")
		file.writeText(DreamUtils.gson.toJson(cars))
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	fun onMount(e: VehicleEnterEvent) {
		if (e.vehicle.type != EntityType.MINECART)
			return

		if (!cars.containsKey(e.vehicle.uniqueId))
			return

		val carInfo = cars[e.vehicle.uniqueId]!!

		val owner = carInfo.owner

		if (e.entered.uniqueId != owner) {
			e.isCancelled = true
			e.entered.sendMessage("§cVocê não pode entrar no carro de §b${carInfo.owner}§c!")
			return
		}

		e.entered.sendMessage("§aVocê entrou no seu carro, não se esqueça de colocar o cinto de segurança e, é claro, se beber não dirija!")
	}

	// Avoid duplicate minecarts if someone breaks a minecart while you are mounted on it
	/* @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	fun onExit(e: VehicleExitEvent) {
		if (e.vehicle.type != EntityType.MINECART)
			return

		if (!cars.containsKey(e.vehicle.uniqueId))
			return

		// If inside a protected claim, kill the minecart and drop it
		GriefPrevention.instance.dataStore.getClaimAt(e.vehicle.location, false, null)
			?: return

		val carInfo = cars[e.vehicle.uniqueId]!!

		val itemStack = ItemStack(Material.MINECART)
			.rename("§3§lFusca")
			.lore("§7Fusca de §b${carInfo.playerName}")
			.storeMetadata(CAR_INFO_KEY, DreamUtils.gson.toJson(carInfo))
			.storeMetadata(FUSCA_CHECK_KEY, "true")

		// TODO: Would this cause issues?
		e.vehicle.world.dropItemNaturally(e.vehicle.location, itemStack)

		e.vehicle.remove()
	} */

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	fun onDestroy(e: VehicleDestroyEvent) {
		val attacker = e.attacker ?: return

		if (e.vehicle.type != EntityType.MINECART)
			return

		val carInfo = cars[e.vehicle.uniqueId] ?: return

		e.isCancelled = true

		if (carInfo.owner == attacker.uniqueId) {
			e.vehicle.remove()

			val itemStack = ItemStack(Material.MINECART)
				.rename("§3§lFusca")
				.lore("§7Fusca de §b${carInfo.playerName}")
				.meta<ItemMeta> {
					persistentDataContainer.set(FUSCA_INFO_KEY, DreamUtils.gson.toJson(carInfo))
					persistentDataContainer.set(IS_FUSCA_CHECK_KEY, true)
				}

			attacker.world.dropItemNaturally(e.vehicle.location, itemStack)
			cars.remove(e.vehicle.uniqueId)
		} else {
			// The player permission check is kinda "iffy", I'm not sure if this is the best place to do this
			val canBreak = attacker.hasPermission("dreamfusca.overridecarbreak") || (attacker as? Player)?.canPlaceAt(e.vehicle.location, Material.MINECART) == true

			if (canBreak) {
				attacker.sendMessage("§7Você quebrou o carro de §b${carInfo.owner}§7!")
				e.vehicle.remove()

				val itemStack = ItemStack(Material.MINECART)
					.rename("§3§lFusca")
					.lore("§7Fusca de §b${carInfo.playerName}")
					.meta<ItemMeta> {
						persistentDataContainer.set(FUSCA_INFO_KEY, DreamUtils.gson.toJson(carInfo))
						persistentDataContainer.set(IS_FUSCA_CHECK_KEY, true)
					}

				attacker.world.dropItemNaturally(e.vehicle.location, itemStack)
				cars.remove(e.vehicle.uniqueId)
				return
			}

			attacker.sendMessage("§cVocê não pode quebrar o carro de §b${carInfo.owner}§c!")
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	fun onInteract(e: PlayerInteractEvent) {
		val item = e.item ?: return
		val clickedBlock = e.clickedBlock ?: return

		val type = item.type

		if (type != Material.MINECART && !item.hasItemMeta())
			return

		val isFusca = item.itemMeta.persistentDataContainer.get(IS_FUSCA_CHECK_KEY)
		if (!isFusca)
			return

		val storedInfo = item.itemMeta.persistentDataContainer.get(FUSCA_INFO_KEY)?.let { DreamUtils.gson.fromJson<CarInfo>(it) }
			?: CarInfo(
				e.player.uniqueId,
				e.player.name,
				CarType.FUSCA
			)

		if (e.useItemInHand() == Event.Result.DENY && e.clickedBlock?.type !in blocks)
			return

		// Let players place cars anywhere, as long as it is in a valid block
		e.isCancelled = true

		val minecart = e.player.world.spawnEntity(clickedBlock.location.add(0.0, 1.0, 0.0), EntityType.MINECART)

		cars[minecart.uniqueId] = storedInfo

		e.player.sendMessage("§aOlha o seu carrão! vroom vroom")

		e.player.playSound(e.player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f)

		item.amount -= 1
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	fun onClick(e: com.Acrobot.ChestShop.Events.ItemParseEvent) {
		val cleanItemString = org.bukkit.ChatColor.stripColor(e.itemString)!!

		if (cleanItemString == "Fusca") {
			val itemStack = ItemStack(Material.MINECART)
				.rename("§3§lFusca")
				.meta<ItemMeta> {
					persistentDataContainer.set(IS_FUSCA_CHECK_KEY, true)
				}

			e.item = itemStack
		}
	}

	fun yawToFace(yaw: Float): BlockFace {
		val roundedYaw = (yaw.toInt() + 360) % 360
		return if (roundedYaw in 45..134)
			BlockFace.WEST
		else if (roundedYaw in 135..224)
			BlockFace.NORTH
		else if (roundedYaw in 225..315)
			BlockFace.EAST
		else BlockFace.SOUTH
	}

	fun isRoad(block: Block) = block.type in blocks ||
			block.getRelative(BlockFace.SOUTH).type in blocks ||
			block.getRelative(BlockFace.NORTH).type in blocks ||
			block.getRelative(BlockFace.EAST).type in blocks ||
			block.getRelative(BlockFace.WEST).type in blocks
}