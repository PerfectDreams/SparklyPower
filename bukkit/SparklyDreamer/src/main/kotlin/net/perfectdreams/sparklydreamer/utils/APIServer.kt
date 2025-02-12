package net.perfectdreams.sparklydreamer.utils

import com.destroystokyo.paper.profile.ProfileProperty
import com.google.common.util.concurrent.AtomicDouble
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcash.DreamCash
import net.perfectdreams.dreamcash.tables.Cashes
import net.perfectdreams.dreamcash.utils.Cash
import net.perfectdreams.dreamcore.dao.DiscordAccount
import net.perfectdreams.dreamcore.dao.User
import net.perfectdreams.dreamcore.tables.DiscordAccounts
import net.perfectdreams.dreamcore.tables.PlayerSkins
import net.perfectdreams.dreamcore.tables.Users
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.lore
import net.perfectdreams.dreamcore.utils.set
import net.perfectdreams.dreamcore.utils.skins.StoredDatabaseSkin
import net.perfectdreams.dreamcorreios.DreamCorreios
import net.perfectdreams.dreammapwatermarker.DreamMapWatermarker
import net.perfectdreams.dreammapwatermarker.DreamMapWatermarker.Companion.LOCK_MAP_CRAFT_KEY
import net.perfectdreams.dreammapwatermarker.DreamMapWatermarker.Companion.MAP_CUSTOM_OWNER_KEY
import net.perfectdreams.dreamsonecas.SonecasUtils
import net.perfectdreams.dreamsonecas.tables.PlayerSonecas
import net.perfectdreams.sparklydreamer.SparklyDreamer
import net.sparklypower.rpc.*
import net.sparklypower.tables.PlayerPantufaPrintShopCustomMaps
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

class APIServer(private val plugin: SparklyDreamer) {
    companion object {
        private const val PANTUFA_PRINT_SHOP_MAP_PESADELOS_COST = 15L
        private const val PANTUFA_PRINT_SHOP_MAP_COPY_PESADELOS_COST = 6L
    }
    private val logger = plugin.logger
    private var server: EmbeddedServer<*, *>? = null
    private val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val totalSonecasGauge: AtomicDouble? = appMicrometerRegistry.gauge("sparklypower.total_sonecas", AtomicDouble(0.0))
    private val totalCashGauge: AtomicDouble? = appMicrometerRegistry.gauge("sparklypower.total_cash", AtomicDouble(0.0))
    private val playersOnlineGauge: AtomicInteger? = appMicrometerRegistry.gauge("sparklypower.players_online", AtomicInteger(0))
    private val protocolVersionToCount = mutableMapOf<Pair<Int, Boolean>, AtomicInteger>()

    fun start() {
        logger.info { "Starting HTTP Server..." }

        val server = embeddedServer(Netty, port = 9999) {
            routing {
                get("/") {
                    call.respondText("SparklyPower API Web Server")
                }

                get("/metrics") {
                    val totalSonecasSum = PlayerSonecas.money.sum()

                    plugin.launchMainThreadDeferred {
                        val onlinePlayers = Bukkit.getOnlinePlayers()
                        playersOnlineGauge?.set(onlinePlayers.size)

                        protocolVersionToCount.forEach { t, u ->
                            u.set(0)
                        }

                        onlinePlayers.forEach {
                            val viaVersion = Via.getAPI()
                            val playerVersionId = viaVersion.getPlayerVersion(it)
                            val versionPlusBedrock = Pair(playerVersionId, it.isBedrockClient)
                            val currentAtomicInteger = protocolVersionToCount[versionPlusBedrock]

                            if (currentAtomicInteger == null) {
                                val atomicInteger = AtomicInteger(1)
                                appMicrometerRegistry.gauge("sparklypower.player_online_versions", listOf(Tag.of("java_protocol_version", playerVersionId.toString()), Tag.of("java_version_name", ProtocolVersion.getProtocol(playerVersionId).name), Tag.of("is_bedrock", versionPlusBedrock.second.toString())), atomicInteger)
                                protocolVersionToCount[versionPlusBedrock] = atomicInteger
                            } else {
                                currentAtomicInteger.incrementAndGet()
                            }
                        }
                    }.await()

                    val totalSonecas = transaction(Databases.databaseNetwork) {
                        PlayerSonecas.select(totalSonecasSum)
                            .first()[totalSonecasSum] ?: BigDecimal.ZERO
                    }

                    val totalCash = transaction(Databases.databaseNetwork) {
                        Cashes.selectAll()
                            .sumByDouble { it[Cashes.cash].toDouble() }
                    }

                    totalSonecasGauge?.set(totalSonecas.toDouble())
                    totalCashGauge?.set(totalCash)

                    call.respond(appMicrometerRegistry.scrape())
                }

                post("/rpc") {
                    val jsonPayload = call.receiveText()
                    logger.info { "${call.request.userAgent()} sent a RPC request: $jsonPayload" }

                    val request = Json.decodeFromString<SparklySurvivalRPCRequest>(jsonPayload)

                    val response = when (request) {
                        is SparklySurvivalRPCRequest.GetSonecasRequest -> {
                            val playerUniqueId = UUID.fromString(request.playerUniqueId)

                            val (money, ranking) = net.perfectdreams.exposedpowerutils.sql.transaction(Dispatchers.IO, Databases.databaseNetwork) {
                                val money = PlayerSonecas.selectAll()
                                    .where { PlayerSonecas.id eq playerUniqueId }
                                    .firstOrNull()
                                    ?.get(PlayerSonecas.money)
                                    ?.toDouble() ?: 0.0

                                val ranking = if (money > 0.0) {
                                    PlayerSonecas.selectAll().where { PlayerSonecas.money greaterEq money.toBigDecimal() }
                                        .count()
                                } else null

                                Pair(money, ranking)
                            }

                            SparklySurvivalRPCResponse.GetSonecasResponse.Success(money, ranking)
                        }

                        is SparklySurvivalRPCRequest.TransferSonecasRequest -> TODO()
                    }

                    call.respondText(
                        Json.encodeToString<SparklySurvivalRPCResponse>(response),
                        ContentType.Application.Json
                    )
                }

                post("/pantufa/prestart-pantufa-print-shop-maps") {
                    val dreamCashPlugin = Bukkit.getPluginManager().getPlugin("DreamCash")?.let { if (it.isEnabled) it else null }

                    // First we need to check if the plugin is enabled
                    if (dreamCashPlugin == null) {
                        call.respondText(
                            Json.encodeToString<PrestartPantufaPrintShopCustomMapsResponse>(PrestartPantufaPrintShopCustomMapsResponse.PluginUnavailable),
                            ContentType.Application.Json
                        )
                        return@post
                    }

                    // Okay, so the plugin is enabled and ready to rock!
                    val request = Json.decodeFromString<PrestartPantufaPrintShopCustomMapsRequest>(call.receiveText())

                    // Check if the user has enough pesadelos
                    val response = transaction(Databases.databaseNetwork) {
                        val requesterUniqueId = UUID.fromString(request.requestedById)
                        val priceOfAllTheMaps = PANTUFA_PRINT_SHOP_MAP_PESADELOS_COST * request.amountOfMapsToBeGenerated
                        val priceOfCopies = if (request.copies > 0) (PANTUFA_PRINT_SHOP_MAP_COPY_PESADELOS_COST * request.amountOfMapsToBeGenerated) * request.copies else 0
                        val totalPrice = priceOfAllTheMaps + priceOfCopies

                        // Do we have enough money?
                        val pesadelos = Cashes.selectAll()
                            .where { Cashes.uniqueId eq requesterUniqueId }
                            .limit(1)
                            .firstOrNull()
                            ?.get(Cashes.cash)
                            ?: return@transaction PrestartPantufaPrintShopCustomMapsResponse.NotEnoughPesadelos(
                                PANTUFA_PRINT_SHOP_MAP_PESADELOS_COST,
                                totalPrice
                            )


                        if (totalPrice > pesadelos)
                            return@transaction PrestartPantufaPrintShopCustomMapsResponse.NotEnoughPesadelos(
                                PANTUFA_PRINT_SHOP_MAP_PESADELOS_COST,
                                totalPrice
                            )

                        return@transaction PrestartPantufaPrintShopCustomMapsResponse.Success(
                            PANTUFA_PRINT_SHOP_MAP_PESADELOS_COST,
                            totalPrice
                        )
                    }

                    call.respondText(
                        Json.encodeToString<PrestartPantufaPrintShopCustomMapsResponse>(response),
                        ContentType.Application.Json
                    )
                }

                post("/pantufa/create-pantufa-print-shop-maps") {
                    val dreamMapWatermarkerPlugin = Bukkit.getPluginManager().getPlugin("DreamMapWatermarker")?.let { if (it.isEnabled) it else null }
                    val dreamCorreiosPlugin = Bukkit.getPluginManager().getPlugin("DreamCorreios")?.let { if (it.isEnabled) it else null }
                    val dreamCashPlugin = Bukkit.getPluginManager().getPlugin("DreamCash")?.let { if (it.isEnabled) it else null }

                    if (dreamMapWatermarkerPlugin != null && dreamCorreiosPlugin != null && dreamCashPlugin != null) {
                        dreamMapWatermarkerPlugin as DreamMapWatermarker
                        dreamCorreiosPlugin as DreamCorreios
                        dreamCashPlugin as DreamCash

                        // Okay, so the plugin is enabled and ready to rock!
                        val request = Json.decodeFromString<GeneratePantufaPrintShopCustomMapsRequest>(call.receiveText())

                        logger.info { "Processing map request ID ${request.requestId}, approved by ${request.approvedById}" }

                        val response = transaction(Databases.databaseNetwork) {
                            val mapsRequestId = request.requestId
                            // Get the map data from the database
                            val customMap = PlayerPantufaPrintShopCustomMaps.selectAll()
                                .where {
                                    PlayerPantufaPrintShopCustomMaps.id eq mapsRequestId
                                }
                                .limit(1)
                                .firstOrNull()

                            if (customMap == null)
                                return@transaction GeneratePantufaPrintShopCustomMapsResponse.UnknownMapRequest

                            if (customMap[PlayerPantufaPrintShopCustomMaps.approvedBy] != null)
                                return@transaction GeneratePantufaPrintShopCustomMapsResponse.RequestAlreadyApproved

                            val requesterUniqueId = customMap[PlayerPantufaPrintShopCustomMaps.requestedBy]
                            val requesterUserName = Users.selectAll()
                                .where {
                                    Users.id eq requesterUniqueId
                                }
                                .limit(1)
                                .firstOrNull()
                                ?.get(Users.username)

                            if (requesterUserName == null)
                                return@transaction GeneratePantufaPrintShopCustomMapsResponse.UnknownPlayer

                            // Do we have enough money?
                            val pesadelos = Cashes.selectAll()
                                .where { Cashes.uniqueId eq customMap[PlayerPantufaPrintShopCustomMaps.requestedBy] }
                                .limit(1)
                                .firstOrNull()
                                ?.get(Cashes.cash) ?: return@transaction GeneratePantufaPrintShopCustomMapsResponse.NotEnoughPesadelos

                            val images = customMap[PlayerPantufaPrintShopCustomMaps.mapImages].split(",")
                                .map { ImageIO.read(Base64.getDecoder().decode(it).inputStream()) }

                            val priceOfAllTheMaps = PANTUFA_PRINT_SHOP_MAP_PESADELOS_COST * images.size

                            val copies: Int = if (customMap[PlayerPantufaPrintShopCustomMaps.copies] == null)
                                0
                            else
                                customMap[PlayerPantufaPrintShopCustomMaps.copies]!!

                            val priceOfCopies = if (copies > 0) {
                                (PANTUFA_PRINT_SHOP_MAP_COPY_PESADELOS_COST * images.size) * copies
                            } else {
                                0
                            }

                            val totalPrice = priceOfAllTheMaps + priceOfCopies

                            if (totalPrice > pesadelos)
                                return@transaction GeneratePantufaPrintShopCustomMapsResponse.NotEnoughPesadelos

                            // Ouch, main thread access, this will freeze the transaction for a bit
                            val generateAndGiveMapsJob = plugin.launchMainThreadDeferred {
                                val mapsToBeGiven = mutableListOf<ItemStack>()
                                val mapIds = mutableSetOf<Int>()

                                for (image in images) {
                                    val mapView = dreamMapWatermarkerPlugin.createImageOnMap(image)

                                    mapIds.add(mapView.id)
                                    mapsToBeGiven.add(
                                        ItemStack(Material.FILLED_MAP)
                                            .lore(
                                                "§7Diretamente da §dGráfica da Gabriela§7...",
                                                "§7(temos os melhores preços da região!)",
                                                "§7§oUm incrível mapa para você!",
                                                "§7",
                                                "§7Mapa feito para §a$requesterUserName §e(◠‿◠✿)",
                                                "§7",
                                                "§7ID do Pedido: §6${request.requestId}"
                                            ).apply {
                                                this.addUnsafeEnchantment(Enchantment.INFINITY, 1)
                                                this.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                                                this.meta<MapMeta> {
                                                    this.mapView = mapView
                                                    this.persistentDataContainer.set(LOCK_MAP_CRAFT_KEY, PersistentDataType.BYTE, 1)
                                                    this.persistentDataContainer.set(MAP_CUSTOM_OWNER_KEY, PersistentDataType.STRING, requesterUniqueId.toString())
                                                    this.persistentDataContainer.set(DreamMapWatermarker.PRINT_SHOP_REQUEST_ID_KEY, request.requestId)
                                                }
                                            }
                                    )
                                }

                                // Add the items to the user's caixa postal
                                if (copies > 0) {
                                    // add the original
                                    dreamCorreiosPlugin.addItem(
                                        requesterUniqueId,
                                        *mapsToBeGiven.toTypedArray()
                                    )

                                    // and the copies
                                    for (i in 0 until copies) {
                                        dreamCorreiosPlugin.addItem(
                                            requesterUniqueId,
                                            *mapsToBeGiven.toTypedArray()
                                        )
                                    }
                                } else {
                                    dreamCorreiosPlugin.addItem(
                                        requesterUniqueId,
                                        *mapsToBeGiven.toTypedArray()
                                    )
                                }

                                mapIds
                            }

                            val mapIds = runBlocking { generateAndGiveMapsJob.await() }

                            // Approve the maps
                            PlayerPantufaPrintShopCustomMaps.update({
                                PlayerPantufaPrintShopCustomMaps.id eq mapsRequestId
                            }) {
                                it[PlayerPantufaPrintShopCustomMaps.approvedAt] = Instant.now()
                                it[PlayerPantufaPrintShopCustomMaps.approvedBy] = UUID.fromString(request.approvedById)
                                it[PlayerPantufaPrintShopCustomMaps.mapIds] = buildJsonArray {
                                    for (mapId in mapIds) {
                                        add(mapId)
                                    }
                                }.toString()
                            }

                            // Charge the player!
                            Cashes.update({ Cashes.uniqueId eq customMap[PlayerPantufaPrintShopCustomMaps.requestedBy] }) {
                                with(SqlExpressionBuilder) {
                                    it[Cashes.cash] = Cashes.cash - totalPrice
                                }
                            }

                            // TODO: Transaction Logs

                            return@transaction GeneratePantufaPrintShopCustomMapsResponse.Success(
                                customMap[PlayerPantufaPrintShopCustomMaps.requestedBy].toString(),
                                totalPrice
                            )
                        }

                        logger.info { "Successfully processed map request ID ${request.requestId}, approved by ${request.approvedById}! Result: $response" }

                        call.respondText(
                            Json.encodeToString<GeneratePantufaPrintShopCustomMapsResponse>(response),
                            ContentType.Application.Json
                        )
                    } else {
                        call.respondText(
                            Json.encodeToString<GeneratePantufaPrintShopCustomMapsResponse>(GeneratePantufaPrintShopCustomMapsResponse.PluginUnavailable),
                            ContentType.Application.Json
                        )
                    }
                }

                post("/pantufa/update-player-skin") {
                    val request = Json.decodeFromString<UpdatePlayerSkinRequest>(call.receiveText())

                    // Set the current player's skin in the database
                    transaction(Databases.databaseNetwork) {
                        PlayerSkins.upsert(PlayerSkins.id) {
                            it[PlayerSkins.id] = UUID.fromString(request.requestedById)
                            it[PlayerSkins.data] = Json.encodeToString<StoredDatabaseSkin>(
                                StoredDatabaseSkin.CustomMojangSkin(
                                    request.skinId,
                                    Clock.System.now(),
                                    request.playerTextureValue,
                                    request.playerTextureSignature
                                )
                            )
                        }
                    }

                    val player = Bukkit.getPlayer(UUID.fromString(request.requestedById))

                    if (player != null) {
                        val playerProfileUpdate = plugin.launchMainThreadDeferred {
                            // Update the player's profile
                            // This works, and it is WAY simpler than the whatever hacky hack SkinsRestorer is doing
                            val playerProfile = player.playerProfile
                            playerProfile.removeProperty("textures")
                            playerProfile.setProperty(
                                ProfileProperty(
                                    "textures",
                                    request.playerTextureValue,
                                    request.playerTextureSignature
                                )
                            )
                            player.playerProfile = playerProfile

                            player.sendMessage("§aSkin atualizada!")
                        }

                        playerProfileUpdate.await()
                    }

                    call.respondText(
                        Json.encodeToString<UpdatePlayerSkinResponse>(UpdatePlayerSkinResponse.Success(player != null)),
                        ContentType.Application.Json
                    )
                }

                post("/pantufa/transfer-sonecas") {
                    val request = Json.decodeFromString<TransferSonecasRequest>(call.receiveText())

                    val transferResult = SonecasUtils.transferSonecasFromPlayerToPlayer(
                        request.giverName,
                        UUID.fromString(request.requestedById),
                        request.receiverName,
                        request.quantity,
                        request.bypassLastActiveTime
                    )

                    val response = when (transferResult) {
                        SonecasUtils.TransferSonhosResult.CannotTransferSonecasToSelf -> TransferSonecasResponse.CannotTransferSonecasToSelf
                        SonecasUtils.TransferSonhosResult.PlayerHasNotJoinedRecently -> TransferSonecasResponse.PlayerHasNotJoinedRecently
                        SonecasUtils.TransferSonhosResult.UserDoesNotExist -> TransferSonecasResponse.UserDoesNotExist
                        SonecasUtils.TransferSonhosResult.YouAreBanned -> TransferSonecasResponse.YouAreBanned
                        SonecasUtils.TransferSonhosResult.YouAreTryingToTransferToABannedUser -> TransferSonecasResponse.YouAreTryingToTransferToABannedUser
                        is SonecasUtils.TransferSonhosResult.NotEnoughSonecas -> TransferSonecasResponse.NotEnoughSonecas(transferResult.currentUserMoney)
                        is SonecasUtils.TransferSonhosResult.Success -> TransferSonecasResponse.Success(
                            transferResult.receiverName,
                            transferResult.receiverId.toString(),
                            transferResult.quantityGiven,
                            transferResult.selfMoney,
                            transferResult.selfRanking,
                            transferResult.receiverMoney,
                            transferResult.receiverRanking
                        )
                    }

                    call.respondText(
                        Json.encodeToString<TransferSonecasResponse>(response),
                        ContentType.Application.Json
                    )
                }

                post("/pantufa/transfer-cash") {
                    val request = Json.decodeFromString<TransferCashRequest>(call.receiveText())

                    val transferResult = Cash.transferCashFromPlayerToPlayer(
                        request.giverName,
                        UUID.fromString(request.requestedById),
                        request.receiverName,
                        request.quantity,
                        request.bypassLastActiveTime
                    )

                    val response = when (transferResult) {
                        Cash.TransferCashResult.CannotTransferCashToSelf -> TransferCashResponse.CannotTransferCashToSelf
                        Cash.TransferCashResult.PlayerHasNotJoinedRecently -> TransferCashResponse.PlayerHasNotJoinedRecently
                        Cash.TransferCashResult.UserDoesNotExist -> TransferCashResponse.UserDoesNotExist
                        Cash.TransferCashResult.YouAreBanned -> TransferCashResponse.YouAreBanned
                        Cash.TransferCashResult.YouAreTryingToTransferToABannedUser -> TransferCashResponse.YouAreTryingToTransferToABannedUser
                        is Cash.TransferCashResult.NotEnoughCash -> TransferCashResponse.NotEnoughCash(transferResult.currentUserMoney)
                        is Cash.TransferCashResult.Success -> TransferCashResponse.Success(
                            transferResult.receiverName,
                            transferResult.receiverId.toString(),
                            transferResult.quantityGiven,
                            transferResult.selfMoney,
                            transferResult.receiverMoney,
                        )
                    }

                    println(response)

                    call.respondText(
                        Json.encodeToString<TransferCashResponse>(response),
                        ContentType.Application.Json
                    )
                }

                get("/loritta/{userId}/sonecas") {
                    val userId = call.parameters.getOrFail("userId").toLong()

                    val discordAccount = transaction(Databases.databaseNetwork) {
                        DiscordAccount.find { DiscordAccounts.discordId eq userId and (DiscordAccounts.isConnected eq true) }.firstOrNull()
                    }

                    if (discordAccount != null) {
                        val result = net.perfectdreams.exposedpowerutils.sql.transaction(Dispatchers.IO, Databases.databaseNetwork) {
                            val user = User.findById(discordAccount.minecraftId) ?: return@transaction null

                            val money = PlayerSonecas.selectAll()
                                .where { PlayerSonecas.id eq discordAccount.minecraftId }
                                .firstOrNull()
                                ?.get(PlayerSonecas.money)
                                ?.toDouble() ?: 0.0

                            return@transaction LorittaGetSonecasResult(
                                user.id.value.toString(),
                                user.username,
                                money
                            )
                        }

                        if (result == null) {
                            call.respondText(
                                "",
                                ContentType.Application.Json,
                                status = HttpStatusCode.NotFound
                            )
                        } else {
                            call.respondText(
                                buildJsonObject {
                                    put("userUniqueId", result.userUniqueId)
                                    put("username", result.username)
                                    put("sonecas", result.sonecas)
                                }.toString(),
                                ContentType.Application.Json,
                                status = HttpStatusCode.OK
                            )
                        }
                    } else {
                        call.respondText(
                            "",
                            ContentType.Application.Json,
                            status = HttpStatusCode.NotFound
                        )
                    }
                }
            }
        }

        // If set to "wait = true", the server hangs
        this.server = server.start(wait = false)
    }

    fun stop() {
        val server = server
        if (server != null) {
            logger.info { "Shutting down HTTP Server..." }
            server.stop(0L, 5_000) // 5s for timeout
            logger.info { "Successfully shut down HTTP Server!" }
        } else {
            logger.warning { "HTTP Server wasn't started, so we won't stop it..." }
        }
    }

    data class LorittaGetSonecasResult(
        val userUniqueId: String,
        val username: String,
        val sonecas: Double
    )
}