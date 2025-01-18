package net.perfectdreams.dreamsinuca

import io.papermc.paper.datacomponent.DataComponentTypes
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.extensions.rightClick
import net.perfectdreams.dreamcustomitems.items.SparklyItemsRegistry
import net.perfectdreams.dreamsinuca.commands.DreamSinucaCommand
import net.perfectdreams.dreamsinuca.listeners.SinucaEntityListener
import net.perfectdreams.dreamsinuca.sinuca.ChalkColor
import net.perfectdreams.dreamsinuca.sinuca.PoolTable
import net.perfectdreams.dreamsinuca.sinuca.PoolTableData
import net.perfectdreams.dreamsinuca.sinuca.PoolTableOrientation
import net.perfectdreams.dreamsinuca.tables.EightBallPoolGameMatches
import net.sparklypower.sparklypaper.event.entity.EntityGetProjectileForWeaponEvent
import net.sparklypower.sparklypaper.event.entity.PreEntityShootBowEvent
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class DreamSinuca : KotlinPlugin(), Listener {
	companion object {
		val POOL_TABLE_ENTITY = SparklyNamespacedKey("pool_table_entity", PersistentDataType.STRING)
		private val CUE_STICK_CHALK_LEVEL = SparklyNamespacedKey("cue_stick_chalk_level", PersistentDataType.INTEGER)
		private val CUE_STICK_CHALK_COLOR = SparklyNamespacedKey("cue_stick_chalk_color", PersistentDataType.STRING)

		val PREFIX = textComponent {
			append("[") {
				color(NamedTextColor.DARK_GRAY)
			}

			append("Sinuca") {
				color(NamedTextColor.DARK_GREEN)
				decorate(TextDecoration.BOLD)
			}

			append("]") {
				color(NamedTextColor.DARK_GRAY)
			}
		}

		const val MAX_CHALK_COLOR_LEVEL = 20

		val ITEM_ID_TO_CHALK_COLOR = mapOf(
			"cue_stick_chalk_blue" to ChalkColor.BLUE,
			"cue_stick_chalk_green" to ChalkColor.GREEN,
			"cue_stick_chalk_orange" to ChalkColor.ORANGE,
			"cue_stick_chalk_red" to ChalkColor.RED
		)
	}

	val poolTables = mutableMapOf<ItemDisplay, PoolTable>()

	private fun easeLinear(start: Float, end: Float, percent: Float): Float {
		return start + (end - start) * percent
	}

	private fun getNewCueStickChalkColor(chalkColor: ChalkColor, level: Int): Color {
		val hsbVals = java.awt.Color.RGBtoHSB(chalkColor.color.red, chalkColor.color.green, chalkColor.color.blue, null)

		val hue = hsbVals[0]
		val saturation = easeLinear(0.0f, hsbVals[1], (level / MAX_CHALK_COLOR_LEVEL.toFloat()))
		val brightness = easeLinear(1f, hsbVals[2], (level / MAX_CHALK_COLOR_LEVEL.toFloat()))

		// Yes it must be ARGB because HSBtoRGB returns the value with alpha
		return Color.fromARGB(java.awt.Color.HSBtoRGB(hue, saturation, brightness))
	}

	override fun softEnable() {
		transaction(Databases.databaseNetwork) {
			SchemaUtils.createMissingTablesAndColumns(
				EightBallPoolGameMatches
			)
		}

		registerCommand(DreamSinucaCommand(this))
		registerEvents(this)
		registerEvents(SinucaEntityListener(this))

		// Start any sinucas in the world
		for (world in Bukkit.getWorlds()) {
			for (entity in world.entities) {
				if (entity is ItemDisplay) {
					val poolTableDataSerialized = entity.persistentDataContainer.get(POOL_TABLE_ENTITY)

					if (poolTableDataSerialized != null) {
						val poolTableData = Json.decodeFromString<PoolTableData>(poolTableDataSerialized)

						// Bukkit.broadcastMessage("Creating entity $entity")
						// Setup the active PoolTable
						val poolTable = PoolTable(this, entity, poolTableData.orientation)
						poolTable.configure()
						this.poolTables[entity] = poolTable
					}
				}
			}
		}
	}

	override fun softDisable() {
		// Stop all pool tables
		for (poolTable in poolTables) {
			poolTable.value.tearDown()
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	fun onInteract(e: EntityGetProjectileForWeaponEvent) {
		val matchedItemInHand = SparklyItemsRegistry.getMatchedItem(e.bow)
		if (matchedItemInHand?.data?.id == "cue_stick") {
			e.setArrow(ItemStack.of(Material.ARROW))
		}
	}

	// This should ALWAYS be lower than the cueball place event
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	fun onInteract(e: PlayerInteractEvent) {
		// This is a hack due to the "stopUsingItem" in "handleUseItemOn", fixes bug when attempting to pull the bow with no item in hand
		// If we triggered the OFF_HAND in interact, we attempt to trigger the main hand item
		// This allows us to successfully let the player pull the bow, even if they don't have any arrows AND even if they are looking at a block
		// (with the EntityGetProjectileForWeaponEvent event)
		// Bukkit.broadcastMessage("PlayerInteractEvent ${e.hand} ${e.useInteractedBlock()} ${e.useItemInHand()}")

		val itemInMainHand = e.player.inventory.itemInMainHand
		val itemInOffHand = e.player.inventory.itemInOffHand
		val matchedItemInHand = SparklyItemsRegistry.getMatchedItem(itemInMainHand)
		val isHoldingCueStick = matchedItemInHand?.data?.id == "cue_stick"
		val matchedItemInOffHand = SparklyItemsRegistry.getMatchedItem(itemInOffHand)

		// This is an exception: This handles the chalk in the cue stick
		if (matchedItemInOffHand != null && e.hand == EquipmentSlot.HAND && isHoldingCueStick) {
			val newChalk = ITEM_ID_TO_CHALK_COLOR[matchedItemInOffHand.data.id]

			if (newChalk != null) {
				e.isCancelled = true

				val currentChalkLevel = itemInMainHand.persistentDataContainer.getOrDefault(
					CUE_STICK_CHALK_LEVEL.key,
					PersistentDataType.INTEGER,
					0
				)
				val currentChalk = itemInMainHand.persistentDataContainer.get(CUE_STICK_CHALK_COLOR)?.let { ChalkColor.valueOf(it) }
				if (currentChalkLevel >= MAX_CHALK_COLOR_LEVEL && currentChalk == newChalk) {
					e.player.sendMessage(
						textComponent {
							color(NamedTextColor.RED)
							append(PREFIX)
							append(" ")

							appendTextComponent {
								content("O seu taco já tem muito giz!")
							}
						}
					)
					return
				}

				val newChalkLevel = MAX_CHALK_COLOR_LEVEL

				e.player.sendMessage(
					textComponent {
						color(NamedTextColor.GREEN)
						append(PREFIX)
						append(" ")

						appendTextComponent {
							content("Você passou giz na ponta do seu taco de sinuca! Será que agora você jogará bem?")
						}
					}
				)

				e.player.world.playSound(
					e.player,
					"sparklypower:snooker.cue_stick_chalk",
					1f,
					DreamUtils.random.nextFloat(0.9f, 1.1f)
				)

				// We use the "map_color" component because the "dyed_color", for some reason, the default value does NOT work
				val newChalkColor = getNewCueStickChalkColor(newChalk, MAX_CHALK_COLOR_LEVEL)
				itemInMainHand.setData(
					DataComponentTypes.MAP_COLOR,
					io.papermc.paper.datacomponent.item.MapItemColor.mapItemColor().color(newChalkColor)
				)

				itemInMainHand.meta<ItemMeta> {
					persistentDataContainer.set(CUE_STICK_CHALK_COLOR, newChalk.name)
					persistentDataContainer.set(CUE_STICK_CHALK_LEVEL, newChalkLevel)
				}

				val maxDamage = itemInOffHand.getData(DataComponentTypes.MAX_DAMAGE) ?: 0
				val newDamage = (itemInOffHand.getData(DataComponentTypes.DAMAGE) ?: 0) + 1
				itemInOffHand.setData(DataComponentTypes.DAMAGE, newDamage)

				if (newDamage > maxDamage) {
					itemInOffHand.amount -= 1
				}
				return
			}
		}

		// This is where the hack actually happens
		// If the current equipment slot is offhand AND we are holding a cue stick...
		if (e.hand == EquipmentSlot.OFF_HAND && isHoldingCueStick) {
			// AND we don't have any active items...
			if (!e.player.hasActiveItem()) {
				// We attempt to start using the item in our main hand!
				// There is only ONE EXCEPTION: If we placed the cue ball in this same tick, we ignore the request to avoid any "omg I shooted it when placing the cueball" mistakes
				// We do it like that instead of checking if it is in hand, because the HAND call is always BEFORE the OFF_HAND call
				for (poolTable in poolTables.values) {
					val sinuca = poolTable.activeSinuca ?: continue

					if (sinuca.playerTurn == e.player && sinuca.lastCueballInHandTick == Bukkit.getCurrentTick()) {
						return
					}
				}

				e.player.startUsingItem(EquipmentSlot.HAND)
			}
		}
	}

	@EventHandler
	fun onShoot(e: PreEntityShootBowEvent) {
		val player = e.entity as? Player ?: return
		// Can you even shoot a null bow?
		val itemInHand = e.bow ?: return

		val matchedItemInHand = SparklyItemsRegistry.getMatchedItem(itemInHand)
		val isHoldingCueStick = matchedItemInHand?.data?.id == "cue_stick"
		// Not a cue stick!
		if (!isHoldingCueStick)
			return

		e.isCancelled = true

		val force = e.force
		if (force == 0f) // Possible but very rare (mostly happens when right clicking a item)
			return

		for (poolTable in poolTables.values) {
			val sinuca = poolTable.activeSinuca ?: continue

			if (sinuca.player1 == player || sinuca.player2 == player) {
				if (sinuca.playerTurn == player && !sinuca.isWaitingForPhysicsToEnd && !sinuca.cueballInHand) {
					val direction = player.location.clone().apply {
						this.pitch = 0f

						when (poolTable.orientation) {
							PoolTableOrientation.SOUTH -> {
								this.yaw -= 90f
							}

							PoolTableOrientation.EAST -> {}
						}
					}.direction.normalize()

					// in 8 ball pool, max power causes the ball to bounce ~5 times

					sinuca.gameOriginLocation.world.playSound(
						sinuca.playerTurn.location,
						"sparklypower:snooker.cue_stick_hit",
						1f,
						DreamUtils.random.nextFloat(0.98f, 1.02f)
					)

					player.sendActionBar(
						textComponent {
							color(NamedTextColor.GREEN)
							content("Força na Bola: $force")
						}
					)

					// Update the chalk level
					val chalkColor = itemInHand.persistentDataContainer.get(CUE_STICK_CHALK_COLOR)?.let { ChalkColor.valueOf(it) }
					if (chalkColor != null) {
						val newChalkLevel = (itemInHand.persistentDataContainer.getOrDefault(CUE_STICK_CHALK_LEVEL.key, PersistentDataType.INTEGER, 0) - 1)
							.coerceAtLeast(0)

						// We use the "map_color" component because the "dyed_color", for some reason, the default value does NOT work
						val newChalkColor = getNewCueStickChalkColor(chalkColor, newChalkLevel)
						itemInHand.setData(
							DataComponentTypes.MAP_COLOR,
							io.papermc.paper.datacomponent.item.MapItemColor.mapItemColor().color(newChalkColor)
						)

						itemInHand.meta<ItemMeta> {
							persistentDataContainer.set(CUE_STICK_CHALK_LEVEL, newChalkLevel)
						}
					}

					val velocity = direction.multiply(force).multiply(11f)
					// Bukkit.broadcastMessage("Force: ${e.force} - Velocity: $velocity")

					sinuca.cueball.body.isAtRest = false
					sinuca.cueball.body.setLinearVelocity(velocity.x, velocity.z)
					sinuca.processPhysics()
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	fun onRightClick(e: PlayerInteractEvent) {
		if (!e.rightClick)
			return

		if (e.hand != EquipmentSlot.HAND)
			return

		for (poolTable in poolTables.values) {
			val sinuca = poolTable.activeSinuca ?: continue

			if (sinuca.playerTurn == e.player && sinuca.cueballInHand) {
				// We cancel the event to avoid the player mistakenly shooting the bow with zero force
				e.isCancelled = true

				val raytraceResult = e.player.rayTraceBlocks(7.0) ?: return // Couldn't ray trace, abort!
				val position = raytraceResult.hitPosition

				val (cueballX, cueballY) = sinuca.calculateCueballCoordinates(position.x, position.z)

				if (!sinuca.checkIfCoordinateIsInsidePlayfield(cueballX.toFloat(), cueballY.toFloat())) {
					// If outside of playfield, just ignore it and don't attempt to translate
					poolTable.sendSinucaMessageToPlayer(
						e.player,
						textComponent {
							color(NamedTextColor.RED)
							content("Você não pode colocar a bola branca aí!")
						}
					)
					return
				}

				// Unpocket that thang
				// We need to calculate where are we looking by using *raytracing* (omg)
				sinuca.cueball.body.translateToOrigin()

				sinuca.cueball.body.translate(cueballX, cueballY)
				val cueballAABB = sinuca.cueball.body.createAABB()

				for (body in sinuca.playfield.bodies) {
					if (body == sinuca.cueball.body)
						continue

					val bodyAABB = body.createAABB()

					if (cueballAABB.overlaps(bodyAABB)) {
						poolTable.sendSinucaMessageToPlayer(
							e.player,
							textComponent {
								color(NamedTextColor.RED)
								content("Você não pode colocar a bola branca aí!")
							}
						)
						return
					}
				}

				sinuca.playfield.addBody(sinuca.cueball.body)
				sinuca.cueball.pocketed = false
				sinuca.cueballInHand = false
				sinuca.lastCueballInHandTick = Bukkit.getCurrentTick()
				sinuca.updateGameObjects()
				sinuca.previewBallTrajectory = true

				e.player.playSound(
					e.player.location,
					"sparklypower:snooker.ball_wall",
					1f,
					2f
				)

				poolTable.sendSinucaMessageToPlayer(
					e.player,
					textComponent {
						color(NamedTextColor.GREEN)
						content("A bola foi colocada! Faça a sua tacada!")
					}
				)
			}
		}
	}

	/**
	 * Checks if [player] has a cue stick in the inventory
	 *
	 * @return if the player has a cue stick in their inventory
	 */
	fun checkIfPlayerHasCueStickInInventory(player: Player): Boolean {
		var hasStick = false
		for (item in player.inventory) {
			if (item == null)
				continue

			val matchedItem = SparklyItemsRegistry.getMatchedItem(item) ?: continue
			if (matchedItem.data.id == "cue_stick") {
				hasStick = true
				break
			}
		}

		return hasStick
	}
}