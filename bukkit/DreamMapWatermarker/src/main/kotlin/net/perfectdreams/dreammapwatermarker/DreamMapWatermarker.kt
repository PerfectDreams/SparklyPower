package net.perfectdreams.dreammapwatermarker

import com.charleskorn.kaml.Yaml
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.decodeFromString
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.commands.command
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.scheduler.onAsyncThread
import net.perfectdreams.dreamcorreios.DreamCorreios
import net.perfectdreams.dreammapwatermarker.commands.DreamMapMakerCommand
import net.perfectdreams.dreammapwatermarker.commands.LoriCoolCardsAdminCommand
import net.perfectdreams.dreammapwatermarker.commands.LoriCoolCardsCommand
import net.perfectdreams.dreammapwatermarker.commands.PhotocopyCommand
import net.perfectdreams.dreammapwatermarker.loricoolcards.LoriCoolCardsHandler
import net.perfectdreams.dreammapwatermarker.map.ImgRenderer
import net.perfectdreams.dreammapwatermarker.tables.LoriCoolCardsClaimedAlbums
import net.perfectdreams.dreammapwatermarker.tables.LoriCoolCardsGeneratedMaps
import net.sparklypower.sparklypaper.event.inventory.CraftItemRecipeEvent
import org.bukkit.Bukkit
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.MapInitializeEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapPalette
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

class DreamMapWatermarker : KotlinPlugin(), Listener {
	companion object {
		val LOCK_MAP_CRAFT_KEY = SparklyNamespacedKey("lock_map_craft")
		val MAP_CUSTOM_OWNER_KEY = SparklyNamespacedKey("map_custom_owner")
		val PRINT_SHOP_REQUEST_ID_KEY = SparklyNamespacedKey("print_shop_request_id", PersistentDataType.LONG)

		val pendingCopyRequests = mutableMapOf<UUID, CustomMapCopyRequest>()
		val donePendingRequests = mutableSetOf<CustomMapCopyRequest>()

		fun watermarkMap(itemStack: ItemStack, customOwner: UUID?) {
			itemStack.meta<MapMeta> {
				persistentDataContainer.set(LOCK_MAP_CRAFT_KEY, PersistentDataType.BYTE, 1)
				if (customOwner != null)
					persistentDataContainer.set(MAP_CUSTOM_OWNER_KEY, PersistentDataType.STRING, customOwner.toString())
			}
		}
	}

	val imageFolder = File(dataFolder, "img")
	val loriCoolCardsHandler = LoriCoolCardsHandler(this)
	lateinit var dreamCorreios: DreamCorreios
	lateinit var config: DreamMapWatermarkerConfig
	val semaphore = Semaphore(32)

	override fun softEnable() {
		super.softEnable()
		config = Yaml.default.decodeFromString<DreamMapWatermarkerConfig>(this.getConfig().saveToString())
		dreamCorreios = DreamCorreios.getInstance()

		imageFolder.mkdirs()

		registerCommand(DreamMapMakerCommand(this))
		registerCommand(PhotocopyCommand(this))

		if (config.generateLorittaFigurittas) {
			registerCommand(LoriCoolCardsCommand(this))
			registerCommand(LoriCoolCardsAdminCommand(this))
		}

		registerEvents(this)

		transaction(Databases.databaseNetwork) {
			SchemaUtils.create(
				LoriCoolCardsGeneratedMaps,
				LoriCoolCardsClaimedAlbums
			)
		}

		// restoreMaps()

		if (config.generateLorittaFigurittas) {
			loriCoolCardsHandler.startLoriCoolCardsMapGenerator()
		}

		registerCommand(
			command("DreamWatermarkMap", listOf("watermarkmap")) {
				permission = "dreamwatermarkmap.watermark"

				executes {
					val playerName = args.getOrNull(0) ?: run {
						player.sendMessage("§cVocê precisa colocar o nome do player!")
						return@executes
					}

					schedule(SynchronizationContext.ASYNC) {
						val uniqueId = DreamUtils.retrieveUserUniqueId(playerName)

						switchContext(SynchronizationContext.SYNC)

						val item = player.inventory.itemInMainHand

						player.inventory.setItemInMainHand(
							item.lore(
								"§7Diretamente da §dGráfica da Gabriela§7...",
								"§7(temos os melhores preços da região!)",
								"§7§oUm incrível mapa para você!",
								"§7",
								"§7Mapa feito para §a${playerName} §e(◠‿◠✿)"
							).apply {
								this.addUnsafeEnchantment(Enchantment.INFINITY, 1)
								this.addItemFlags(ItemFlag.HIDE_ENCHANTS)
								this.meta<ItemMeta> {
									this.persistentDataContainer.set(LOCK_MAP_CRAFT_KEY, PersistentDataType.BYTE, 1)
									this.persistentDataContainer.set(MAP_CUSTOM_OWNER_KEY, PersistentDataType.STRING, uniqueId.toString())
								}
							}
						)
					}
				}
			}
		)
	}

	override fun softDisable() {
		super.softDisable()

		pendingCopyRequests.clear()
		donePendingRequests.clear()
	}

	@EventHandler
	fun onCraft(event: CraftItemRecipeEvent) {
		val inventoryMatrix = event.craftingMatrix ?: return

		val hasCustomMap = inventoryMatrix.filterNotNull().any {
			it.itemMeta?.persistentDataContainer?.get(MAP_CUSTOM_OWNER_KEY, PersistentDataType.STRING) != null || it.lore?.lastOrNull() == "§a§lObrigado por votar! ^-^" || it.itemMeta?.displayName?.endsWith("Players Online!") == true || it.itemMeta?.persistentDataContainer?.has(LOCK_MAP_CRAFT_KEY, PersistentDataType.BYTE) == true
		}

		if (hasCustomMap)
			event.isCancelled = true
	}

	@EventHandler
	fun onCartography(event: InventoryClickEvent) {
		// We could use "clickedInventory" but that does disallow dragging from the bottom to the top
		val clickedInventory = event.whoClicked.openInventory
		val currentItem = event.currentItem ?: return

		if (clickedInventory.type != InventoryType.CARTOGRAPHY) // el gambiarra
			return

		if (currentItem.itemMeta?.persistentDataContainer?.get(MAP_CUSTOM_OWNER_KEY, PersistentDataType.STRING) != null || currentItem.lore?.lastOrNull() == "§a§lObrigado por votar! ^-^" || currentItem.itemMeta?.displayName?.endsWith("Players Online!") == true || currentItem.itemMeta?.persistentDataContainer?.has(LOCK_MAP_CRAFT_KEY, PersistentDataType.BYTE) == true) {
			event.isCancelled = true
		}

		// Bukkit.broadcastMessage("Moveu item ${event.destination}")
	}

	@EventHandler
	fun onQuit(event: PlayerQuitEvent) {
		pendingCopyRequests.remove(event.player.uniqueId)
	}

	/**
	 * Converts a given Image into a BufferedImage
	 *
	 * @param img The Image to be converted
	 * @return The converted BufferedImage
	 */
	fun toBufferedImage(img: Image): BufferedImage {
		if (img is BufferedImage) {
			return img
		}

		// Create a buffered image with transparency
		val bimage = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB)

		// Draw the image on to the buffered image
		val bGr = bimage.createGraphics()
		bGr.drawImage(img, 0, 0, null)
		bGr.dispose()

		// Return the buffered image
		return bimage
	}

	// This is just a smol API for other plugins to hook up into DreamMapWatermarker
	suspend fun createImageOnMap(image: BufferedImage): MapView {
		DreamUtils.assertMainThread(true)

		// Create map
		val map = Bukkit.createMap(Bukkit.getWorlds().first { it.name == "world" })

		map.isLocked = true // Optimizes the map because the server doesn't attempt to get the world data when the player is holding the map in their hand
		val renderers: List<MapRenderer> = map.renderers

		for (r in renderers) {
			map.removeRenderer(r)
		}

		map.addRenderer(ImgRenderer(MapPalette.imageToBytes(image)))

		// Save map
		onAsyncThread {
			withContext(Dispatchers.IO) {
				ImageIO.write(image, "png", File(imageFolder, "${map.id}.png"))
			}
		}

		return map
	}

	@EventHandler
	fun onInitialize(event: MapInitializeEvent) {
		// Instead of preloading everything on startup (which is VERY slow if you have a lot of maps) we will do "async" preloading of the maps
		// This way, we only need to load the maps that are ACTUALLY loaded and used in the server
		// [08:00:44] [DefaultDispatcher-worker-1/INFO]: [DreamMapWatermarker] Restored map 20576!
		// [08:00:56] [DefaultDispatcher-worker-27/INFO]: [DreamMapWatermarker] Restored map 26197!

		val mapView = event.map
		val file = File(imageFolder, "${event.map.id}.png")

		if (file.exists()) {
			logger.info { "Attempting to restore map ${event.map.id} asynchronously..." }

			// It exists! Lock it already!!
			mapView.isLocked = true // Optimizes the map because the server doesn't attempt to get the world data when the player is holding the map in their hand

			// And remove all renderers too, we won't need those
			val renderers: List<MapRenderer> = mapView.renderers

			for (r in renderers) {
				mapView.removeRenderer(r)
			}

			launchAsyncThread {
				semaphore.withPermit {
					val image = ImageIO.read(file)

					mapView.addRenderer(ImgRenderer(MapPalette.imageToBytes(image)))

					logger.info { "Restored map ${event.map.id} asynchronously!" }
				}
			}
		}
	}

	data class CustomMapCopyRequest(
		val player: UUID,
		val requestId: Long,
		val copies: Int,
		var requestInMillis: Long,
		val price: Long,
		val mapIds: List<Int>,
	)
}