package net.sparklypower.sparklyneonvelocity

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Inject
import com.typesafe.config.ConfigFactory
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.InboundConnection
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import com.velocitypowered.api.util.Favicon
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.connection.client.LoginInboundConnection
import com.velocitypowered.proxy.connection.util.VelocityInboundConnection
import com.velocitypowered.proxy.util.AddressUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.slf4j.logger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.event.HoverEvent
import net.perfectdreams.minecraftmojangapi.MinecraftMojangAPI
import net.sparklypower.common.utils.fromLegacySectionToTextComponent
import net.sparklypower.common.utils.toLegacySection
import net.sparklypower.sparklyneonvelocity.commands.*
import net.sparklypower.sparklyneonvelocity.config.SparklyNeonVelocityConfig
import net.sparklypower.sparklyneonvelocity.dao.User
import net.sparklypower.sparklyneonvelocity.listeners.ChatListener
import net.sparklypower.sparklyneonvelocity.listeners.LoginListener
import net.sparklypower.sparklyneonvelocity.listeners.PingListener
import net.sparklypower.sparklyneonvelocity.listeners.ServerConnectListener
import net.sparklypower.sparklyneonvelocity.network.APIServer
import net.sparklypower.sparklyneonvelocity.tables.*
import net.sparklypower.sparklyneonvelocity.utils.ASNManager
import net.sparklypower.sparklyneonvelocity.utils.StaffColors
import net.sparklypower.sparklyneonvelocity.utils.emotes
import net.sparklypower.sparklyneonvelocity.utils.socket.SocketServer
import net.sparklypower.sparklyvelocitycore.SparklyVelocityCore
import net.sparklypower.sparklyvelocitycore.SparklyVelocityPlugin
import net.sparklypower.sparklyvelocitycore.utils.Pudding
import org.jetbrains.exposed.sql.SchemaUtils
import org.slf4j.Logger
import java.io.File
import java.nio.file.Path
import java.util.*
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.jvm.optionals.getOrNull

@Plugin(
    id = "sparklyneonvelocity",
    name = "SparklyNeonVelocity",
    version = "1.0.0-SNAPSHOT",
    url = "https://sparklypower.net",
    description = "I did it!", // Yay!!!
    authors = ["MrPowerGamerBR"],
    dependencies = arrayOf(
        Dependency(id = "sparklyvelocitycore", optional = false)
    )
)
class SparklyNeonVelocity @Inject constructor(
    private val server: ProxyServer,
    _logger: Logger,
    @DataDirectory dataDirectory: Path
) : SparklyVelocityPlugin() {
    val logger = KotlinLogging.logger(_logger)

    val dataFolder = dataDirectory.toFile()
    val favicons = mutableMapOf<String, Favicon>()
    val isMaintenance = File(dataDirectory.toFile(), "maintenance").exists()

    // We keep these things in here to avoid getting lost when reloading the SparklyNeonVelocity plugin
    val loggedInPlayers
        get() = SparklyVelocityCore.INSTANCE.loggedInPlayers
    val pingedByAddresses
        get() = SparklyVelocityCore.INSTANCE.pingedByAddresses

    val minecraftMojangApi = MinecraftMojangAPI()
    val asnManager = ASNManager(this)
    val punishmentManager = PunishmentManager(this, server)
    val apiServer = APIServer(this, server)
    val config: SparklyNeonVelocityConfig
    val punishmentWebhook: WebhookClient
    val adminChatWebhook: WebhookClient
    val discordAccountAssociationsWebhook: WebhookClient
    val survivalLogInWebhook: WebhookClient
    val lockedAdminChat = mutableSetOf<UUID>()
    var socketServer: SocketServer? = null
    val pudding: Pudding
        get() = SparklyVelocityCore.INSTANCE.pudding

    init {
        logger.info { "Hello there! I made my first plugin with Velocity. SparklyNeonVelocity~" }
        config = Hocon.decodeFromConfig(ConfigFactory.parseFile(File(dataFolder, "plugin.conf")).resolve())
        punishmentWebhook = WebhookClient.withUrl(config.discord.webhooks.punishmentWebhook)
        adminChatWebhook = WebhookClient.withUrl(config.discord.webhooks.adminChatWebhook)
        discordAccountAssociationsWebhook = WebhookClient.withUrl(config.discord.webhooks.discordAccountAssociationsWebhook)
        survivalLogInWebhook = WebhookClient.withUrl(config.discord.webhooks.survivalLogInWebhook)
    }

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        // Do some operation demanding access to the Velocity API here.
        // For instance, we could register an event:
        // We don't need to register the main class because it is already registered!
        // The plugin main instance is automatically registered.

        // Register our custom listeners
        // THIS REQUIRES SPARKLYVELOCITY!!!
        val proxyVersion = server.version
        if (proxyVersion.name == "SparklyVelocity") {
            val velocityServer = server as VelocityServer
            for (listener in config.listeners) {
                velocityServer.cm.bind(
                    listener.name,
                    AddressUtil.parseAndResolveAddress(listener.bind),
                    listener.proxyProtocol
                )
            }
        } else {
            logger.warn { "You aren't using SparklyVelocity! We aren't going to attempt to register listeners then..." }
        }
    }

    override fun onEnable() {
        // onEnable is handled by SparklyVelocityCore, onEnable and onDisable are called on plugin load/unload/reload
        loadFavicons()
        asnManager.load()

        // Fakegate - Using Floodgate features without actually using Floodgate
        // There are some Floodgate features that we want to use, but we don't want to use Floodgate's authentication, and
        // Floodgate does not work on SparklyVelocity (due to the multiple listeners feature)
        //
        // To let us use Floodgate's features on the backend, we need to manually register the channels here!
        server.channelRegistrar.register(MinecraftChannelIdentifier.from("floodgate:form"))

        if (config.socketPort != null) {
            val socketServer = SocketServer(this, server, config.socketPort)
            this.socketServer = socketServer
            thread { socketServer.start() }
        }

        runBlocking {
            pudding.transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    Bans,
                    IpBans,
                    Warns,
                    GeoLocalizations,
                    ConnectionLogEntries,
                    PremiumUsers,
                    BlockedASNs,
                    WhitelistedIps
                )
            }
        }

        server.eventManager.register(this, PingListener(this, this.server))
        server.eventManager.register(this, ServerConnectListener(this))
        server.eventManager.register(this, LoginListener(this, this.server))
        server.eventManager.register(this, ChatListener(this))

        registerCommand(server, PremiumCommand(this))
        registerCommand(server, WhitelistIpCommand(this, this.server))
        registerCommand(server, AdminChatCommand(this, this.server))
        registerCommand(server, AdvancedDupeIpCommand(this, this.server))
        registerCommand(server, BanCommand(this, this.server))
        registerCommand(server, CheckBanCommand(this, this.server))
        registerCommand(server, ConnectionLogCommand(this, this.server))
        registerCommand(server, DiscordCommand(this))
        registerCommand(server, DupeIpCommand(this, this.server))
        registerCommand(server, selfCommandMetaBuilder("geoip"), GeoIpCommand(this))
        registerCommand(server, selfCommandMetaBuilder("ipban", "banip", "baniripwildcard"), IpBanCommand(this, this.server))
        registerCommand(server, selfCommandMetaBuilder("ipwildcardban", "banipwildcard", "baniripwildcard"), IpBanCommand(this, this.server))
        registerCommand(server, selfCommandMetaBuilder("ipreport"), IpReportCommand(this, this.server))
        registerCommand(server, selfCommandMetaBuilder("ipunban", "desbanirip", "unbanip", "ipdesbanir"), IpUnbanCommand(this, this.server))
        registerCommand(server, selfCommandMetaBuilder("kick"), KickCommand(this))
        registerCommand(server, selfCommandMetaBuilder("unban"), UnbanCommand(this, this.server))
        registerCommand(server, selfCommandMetaBuilder("unwarn"), UnwarnCommand(this, this.server))
        registerCommand(server, WarnCommand(this, this.server))
        registerCommand(server, selfCommandMetaBuilder("banasn"), BanASNCommand(this, this.server))
        registerCommand(server, selfCommandMetaBuilder("unbanasn"), UnbanASNCommand(this, this.server))
        registerCommand(server, CheckAndKickCommand(this, this.server))

        apiServer.start()
    }

    override fun onDisable() {
        this.apiServer.stop()
        this.socketServer?.stop()
    }

    private fun selfCommandMetaBuilder(alias: String, vararg otherAliases: String): CommandMeta {
        return server.commandManager.metaBuilder(alias).aliases(*otherAliases).plugin(this).build()
    }

    private fun loadFavicons() {
        favicons.clear()
        File(dataFolder, "server-icons").listFiles().filter { it.extension == "png" } .forEach {
            this.logger.info { "Loading ${it.name}..." }
            val icon = ImageIO.read(it)
            favicons[it.nameWithoutExtension] = Favicon.create(icon)
        }
    }

    fun broadcastAdminChatMessage(sender: Player, text: String) {
        val staff = server.allPlayers
            .filter { it.hasPermission("sparklyneonvelocity.adminchat") && it.uniqueId in loggedInPlayers }

        val message = sender.let { player ->
            // The last color is a fallback, it checks for "group.default", so everyone should, hopefully, have that permission
            val role = StaffColors.values().first { player.hasPermission(it.permission) }

            val isGirl = runBlocking {
                pudding.transaction {
                    User.findById(player.uniqueId)?.isGirl ?: false
                }
            }

            val colors = role.colors
            val prefix = with (role.prefixes) { if (isGirl && size == 2) get(1) else get(0) }
            val emote = emotes[player.username] ?: ""

            // Using different colors for each staff group is bad, because it is harder to track admin chat messages since all groups have different colors
            var colorizedText = "$text"

            staff.forEach {
                val regex = Regex(".*\\b${it.username}\\b.*")
                if (!text.matches(regex)) return@forEach

                it.sendActionBar("${colors.chat.toLegacySection()}${player.username}${colors.nick.toLegacySection()} te mencionou no chat staff!".fromLegacySectionToTextComponent())
                it.playSound(
                    Sound.sound()
                        .type(Key.key("perfectdreams.sfx.msn"))
                        .volume(1f)
                        .pitch(1f)
                        .build()
                )

                colorizedText = colorizedText.replace(Regex("\\b${it.username}\\b"), colors.mention(it.username))
            }

            "$prefix $emote ${colors.nick.toLegacySection()}${player.username}${AdminChatCommand.adminChatColor.toLegacySection()}: $colorizedText".fromLegacySectionToTextComponent().run {
                hoverEvent(HoverEvent.showText("§3Servidor: §b${player.currentServer.getOrNull()?.server?.serverInfo?.name}".fromLegacySectionToTextComponent()))
            }
        } ?: "\ue252 §x§a§8§a§8§a§8Mensagem do console: §x§c§6§b§f§c§3$text".fromLegacySectionToTextComponent()

        staff.forEach { it.sendMessage(message) }

        adminChatWebhook.send(
            WebhookMessageBuilder()
                .setUsername(sender.username)
                .setAvatarUrl("https://sparklypower.net/api/v1/render/avatar?name=${sender.username}&scale=16")
                .setContent(text)
                .build()
        )
    }

    fun isGeyser(connection: InboundConnection): Boolean {
        val minecraftConnection = if (connection is LoginInboundConnection) {
            connection.delegatedConnection()
        } else if (connection is VelocityInboundConnection) {
            connection.connection
        } else error("I don't know how to get a MinecraftConnection from a ${connection}!")

        val listenerName = minecraftConnection.listenerName
        logger.info { "${connection.remoteAddress} listener name: $listenerName" }

        // To detect and keep player IPs correctly, we use a separate Bungee listener that uses the PROXY protocol
        // To check if the user is connecting thru Geyser, we will check if the listener name matches what we would expect
        return listenerName == "geyser"
    }
}