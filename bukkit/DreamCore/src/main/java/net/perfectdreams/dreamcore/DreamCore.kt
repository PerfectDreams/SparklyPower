package net.perfectdreams.dreamcore

import com.charleskorn.kaml.Yaml
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import kotlinx.serialization.decodeFromString
import me.lucko.spark.bukkit.BukkitSparkPlugin
import net.perfectdreams.dreamcore.commands.SkinCommand
import net.perfectdreams.dreamcore.commands.declarations.DreamCoreCommand
import net.perfectdreams.dreamcore.commands.declarations.MeninaCommand
import net.perfectdreams.dreamcore.commands.declarations.MeninoCommand
import net.perfectdreams.dreamcore.dao.User
import net.perfectdreams.dreamcore.eventmanager.DreamEventManager
import net.perfectdreams.dreamcore.listeners.PlayerLoginListener
import net.perfectdreams.dreamcore.listeners.SkinsListener
import net.perfectdreams.dreamcore.listeners.SocketListener
import net.perfectdreams.dreamcore.network.socket.SocketServer
import net.perfectdreams.dreamcore.tables.*
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.displays.SparklyDisplayManager
import net.perfectdreams.dreamcore.utils.extensions.*
import net.perfectdreams.dreamcore.utils.npc.SparklyNPCManager
import net.perfectdreams.dreamcore.utils.npc.user.SparklyNPCCommand
import net.perfectdreams.dreamcore.utils.npc.user.SparklyUserNPCManager
import net.perfectdreams.dreamcore.utils.packetevents.PacketPipelineRegisterListener
import net.perfectdreams.dreamcore.utils.players.PlayerVisibilityListener
import net.perfectdreams.dreamcore.utils.players.PlayerVisibilityManager
import net.perfectdreams.dreamcore.utils.scoreboards.SparklyScoreboardListener
import net.perfectdreams.dreamcore.utils.scoreboards.SparklyScoreboardManager
import net.perfectdreams.dreamcore.utils.skins.SkinUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.concurrent.thread

class DreamCore : KotlinPlugin() {
	companion object {
		lateinit var dreamConfig: DreamConfig
		val INSTANCE
			get() = Bukkit.getPluginManager().getPlugin("DreamCore") as DreamCore
	}

	val dataYaml by lazy {
		File(dataFolder, "data.yml")
	}
	var spawn: Location? = null

	val userData by lazy {
		if (!dataYaml.exists())
			dataYaml.writeText("")

		YamlConfiguration.loadConfiguration(dataYaml)
	}

	val dreamEventManager = DreamEventManager()
	val sparklyNPCManager = SparklyNPCManager(this)
	val sparklyUserNPCManager = SparklyUserNPCManager(this)
	val sparklyDisplayManager = SparklyDisplayManager(this)
	val scoreboardManager = SparklyScoreboardManager(this)
	val skinUtils = SkinUtils(this)
	val rpc = RPCUtils(this)
	var sparkSnap: SparkSnap? = null
	internal val playerVisibilityManagers = mutableMapOf<Player, PlayerVisibilityManager>()

	override fun onEnable() {
		saveDefaultConfig()

		loadConfig()

		dreamConfig.socket.let {
			logger.info { "Starting socket server at port ${it.port}" }
			thread { SocketServer(it.port).start() }
			Bukkit.getPluginManager().registerEvents(SocketListener(), this)
		}

		logger.info { "Starting Database..." }

		transaction(Databases.databaseNetwork) {
			SchemaUtils.createMissingTablesAndColumns(
				Users,
				EventVictories,
				Transactions,
				PreferencesTable,
				PlayerSkins,
				DiscordAccounts,
				TrackedOnlineHours
			)
		}

		logger.info { "Loading locales..." }
		TranslationUtils.loadLocale(dataFolder, "en_us")
		TranslationUtils.loadLocale(dataFolder, "pt_br")

		logger.info { "Creating average colors of materials..." }
		MaterialColors.initialize(this)

		if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null)
			SignGUIUtils.registerSignGUIListener()

		Bukkit.getPluginManager().registerEvents(DreamMenuListener(), this)

		// Iniciar funções do Vault dentro de um try ... catch
		// É necessário ficar dentro de um try ... catch para caso o servidor não tenha algum
		// hook do Vault (por exemplo, não possuir um hook para o chat)
		try { VaultUtils.setupChat() } catch (e: NoClassDefFoundError) {}
		try { VaultUtils.setupEconomy() } catch (e: NoClassDefFoundError) {}
		try { VaultUtils.setupPermissions() } catch (e: NoClassDefFoundError) {}

		sparklyCommandManager.register(DreamCoreCommand(this))
		sparklyCommandManager.register(MeninoCommand(this))
		sparklyCommandManager.register(MeninaCommand(this))
		// Test command, should not be registered!
		// sparklyCommandManager.register(TestCommand, HelloWorldCommandExecutor(), HelloLoriCommandExecutor(), HelloCommandExecutor(), DoYouLikeCommandExecutor(), TellExecutor())

		// SparklySkinRestorer
		if (DreamCore.dreamConfig.features.sparklySkinsRestorer) {
			Bukkit.getPluginManager().registerEvents(SkinsListener(this), this)
			sparklyCommandManager.register(SkinCommand(this))
		}

		// This is not required for SparklyPower because the data is inserted on the proxy side (Velocity)
		// But we have this as an option for easier development or for private servers without a proxy
		if (DreamCore.dreamConfig.features.insertPlayerUsersDataOnLogin) {
			Bukkit.getPluginManager().registerEvents(PlayerLoginListener(this), this)
		}

		// Scoreboards
		Bukkit.getPluginManager().registerEvents(SparklyScoreboardListener(this), this)

		// SparklyNPC
		sparklyNPCManager.start()
		sparklyUserNPCManager.start()
		registerCommand(SparklyNPCCommand(this))

		// SparklyDisplays
		sparklyDisplayManager.start()

		// SparklyPacketEvents
		Bukkit.getPluginManager().registerEvents(PacketPipelineRegisterListener(this), this)
		Bukkit.getPluginManager().registerEvents(PlayerVisibilityListener(this), this)

		val scheduler = Bukkit.getScheduler()

		scheduler.schedule(this, SynchronizationContext.ASYNC) {
			while (true) {
				val newGirls = transaction(Databases.databaseNetwork) {
					User.find { Users.isGirl eq true }
						.map { it.id.value }
						.toMutableSet()
				}

				MeninaAPI.girls = newGirls
				waitFor(6000)
			}
		}

		dreamEventManager.startEventsTask()
		val sparkPlugin = Bukkit.getPluginManager().getPlugin("spark") as BukkitSparkPlugin?
		if (sparkPlugin != null) {
			logger.info { "Spark detected, enabling SparkSnap..." }
			val sparkSnap = SparkSnap(this, sparkPlugin)
			sparkSnap.startTask()
			this.sparkSnap = sparkSnap
		} else {
			logger.info { "Spark not detected!" }
		}
	}

	fun loadConfig() {
		if (!config.contains("serverName")) {
			logger.severe { "Você esqueceu de colocar o \"serverName\" na configuração! Desligando servidor... :(" }
			Bukkit.shutdown()
			return
		}

		// Carregar configuração
		dreamConfig = Yaml.default.decodeFromString(config.saveToString())

		spawn = userData.getLocation("spawnLocation") ?: Bukkit.getWorlds().first().spawnLocation

		logger.info { "Let's make the world a better place, one plugin at a time. :3" }
		logger.info { "Server Name: ${dreamConfig.serverName}" }
		logger.info { "Bungee Server Name: ${dreamConfig.bungeeName}" }
	}

	// We SHOULD NOT override onDisable here to avoid our async tasks not being cancelled (not a HUGE deal but it helps during dev)
	override fun softDisable() {
		sparklyUserNPCManager.save()
		playerInventories.keys.forEach { it.restoreInventory() }
		Databases.dataSource.close()
	}

	fun getPlayerVisibilityManager(player: Player): PlayerVisibilityManager? {
		DreamUtils.assertMainThread(true)
		return playerVisibilityManagers[player]
	}

	fun getOrCreatePlayerVisibilityManager(player: Player): PlayerVisibilityManager {
		DreamUtils.assertMainThread(true)
		return playerVisibilityManagers.getOrPut(player) { PlayerVisibilityManager(player) }
	}

	fun removePlayerVisibilityManager(player: Player) {
		DreamUtils.assertMainThread(true)
		playerVisibilityManagers.remove(player)
	}
}