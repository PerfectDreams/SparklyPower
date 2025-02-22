package net.perfectdreams.dreamloja.commands

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.perfectdreams.dreambedrockintegrations.DreamBedrockIntegrations
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcore.dao.User
import net.perfectdreams.dreamcore.tables.Users
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.exposed.ilike
import net.perfectdreams.dreamcore.utils.extensions.isUnsafe
import net.perfectdreams.dreamcore.utils.extensions.teleportWithEffects
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamloja.DreamLoja
import net.perfectdreams.dreamloja.dao.Shop
import net.perfectdreams.dreamloja.tables.Shops
import net.perfectdreams.dreamloja.tables.UserShopVotes
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.response.result.ValidFormResponseResult
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class LojaExecutor(m: DreamLoja) : LojaExecutorBase(m) {
    inner class Options : CommandOptions() {
        val playerName = optionalWord("player_name") { context, builder ->
            transaction(Databases.databaseNetwork) {
                Shops.innerJoin(Users, { Shops.owner }, { Users.id })
                    .slice(Shops.owner, Users.username)
                    .select { Users.username ilike builder.remaining.replace("%", "") + "%" }
                    .limit(10)
                    .map { it[Users.username] }
                    .distinct()
                    .forEach {
                        builder.suggest(it)
                    }
            }
        }

        val shopName = optionalGreedyString("shop_name")
    }

    override val options = Options()

    override fun execute(context: CommandContext, args: CommandArguments) {
        val player = context.requirePlayer()

        val ownerName = args[options.playerName]
        val shopName = m.parseLojaNameOrNull(args[options.shopName])

        if (ownerName != null) {
            m.launchAsyncThread {
                val user = transaction(Databases.databaseNetwork) {
                    User.find { Users.username eq ownerName }.firstOrNull()
                }

                if (user == null) {
                    context.sendLojaMessage {
                        color(NamedTextColor.RED)

                        append("Usuário não existe!")
                    }
                    return@launchAsyncThread
                }

                val playerShops = transaction(Databases.databaseNetwork) {
                    Shop.find { (Shops.owner eq user.id.value) }
                        .toList()
                }.sortedBy { it.order ?: Int.MAX_VALUE }

                if (playerShops.size > 1 && shopName == null) {
                    onMainThread {
                        if (!player.isBedrockClient) {
                            createAndSendMenuJava(
                                player,
                                ownerName,
                                playerShops
                            )
                        } else {
                            createAndSendMenuBedrock(
                                player,
                                ownerName,
                                playerShops
                            )
                        }
                    }
                    return@launchAsyncThread
                }

                // All shop names are in lowercase
                val trueShopName = shopName?.lowercase() ?: "loja"

                val shop = transaction(Databases.databaseNetwork) {
                    if (playerShops.size != 1)
                        Shop.find { (Shops.owner eq user.id.value) and (Shops.shopName eq trueShopName) }.firstOrNull()
                    else
                        Shop.find { (Shops.owner eq user.id.value) }.firstOrNull()
                }

                if (shop == null) {
                    context.sendLojaMessage {
                        color(NamedTextColor.RED)

                        append("Usuário não possui loja ou você colocou o nome da loja errada!")
                    }
                    return@launchAsyncThread
                }

                val votes = transaction(Databases.databaseNetwork) {
                    UserShopVotes.select {
                        UserShopVotes.receivedBy eq user.id.value
                    }.count()
                }

                onMainThread {
                    val location = shop.getLocation()
                    if (location.isUnsafe || location.blacklistedTeleport) {
                        val isOwner = shop.owner == player.uniqueId || player.hasPermission("dreamloja.bypass")

                        if (!isOwner) {
                            context.sendLojaMessage {
                                color(NamedTextColor.RED)

                                append("Loja do usuário não é segura!")
                            }
                            return@onMainThread
                        } else {
                            context.sendLojaMessage {
                                color(NamedTextColor.RED)

                                append("Sua loja não é segura! Verifique se existe água, lava ou buracos em volta do spawn dela!")
                            }
                        }
                    }

                    player.teleportWithEffects(location)

                    val fancyName = Bukkit.getPlayer(user.id.value)?.displayName ?: user.username

                    player.sendTitle(
                        "§bLoja d${MeninaAPI.getArtigo(user.id.value)} $fancyName",
                        "§bVotos: §e$votes",
                        10,
                        100,
                        10
                    )
                }
            }
        } else {
            m.openMenu(player)
        }
    }

    private fun createAndSendMenuJava(player: Player, ownerName: String, playerShops: List<Shop>) {
        val menu = createMenu(
            InventoryUtils.roundToNearestMultipleOfNine(playerShops.size).coerceAtLeast(9),
            "§a§lLojas de $ownerName"
        ) {
            // Map it down to our inventory maps, split over to 9 first tho
            playerShops.chunked(9)
                .forEachIndexed { yIndex, shops ->
                    val charMap = DreamLoja.INVENTORY_POSITIONS_MAPS[shops.size] ?: "XXXXXXXXX" // fallback

                    var shopIndex = 0
                    for ((xIndex, char) in charMap.withIndex()) {
                        if (char == 'X') {
                            val shop = shops.getOrNull(shopIndex++) ?: break // If there isn't enough shops, break out!
                            slot(xIndex, yIndex) {
                                item = shop.iconItemStack?.let { ItemUtils.deserializeItemFromBase64(it) } ?: ItemStack(Material.DIAMOND_BLOCK)
                                    .rename("§a${shop.shopName}")

                                onClick {
                                    player.closeInventory()
                                    Bukkit.dispatchCommand(
                                        player,
                                        "loja $ownerName ${shop.shopName}"
                                    )
                                }
                            }
                        }
                    }
                }
        }

        menu.sendTo(player)
    }

    private fun createAndSendMenuBedrock(player: Player, ownerName: String, playerShops: List<Shop>) {
        val bedrockIntegrations = Bukkit.getPluginManager().getPlugin("DreamBedrockIntegrations") as DreamBedrockIntegrations

        data class ShopButton(
            val name: String,
            val shopName: String
        )

        val buttons = playerShops.map {
            // For some reason calling it.displayName() wraps the item name in [], that's why we access the item meta
            val metaShopName = it.iconItemStack
                ?.let { ItemUtils.deserializeItemFromBase64(it) }
                ?.itemMeta
                ?.displayName()
                ?.let { LegacyComponentSerializer.legacySection().serialize(it) }

            ShopButton(
                metaShopName ?: it.shopName,
                it.shopName
            )
        }

        val formBuilder = SimpleForm
            .builder()
            .title("§a§lLojas de $ownerName")

        for (button in buttons) {
            formBuilder.button(button.name)
        }

        formBuilder.resultHandler { t, u ->
            if (u is ValidFormResponseResult) {
                val selected = buttons[u.response().clickedButtonId()]
                Bukkit.dispatchCommand(
                    player,
                    "loja $ownerName ${selected.shopName}"
                )
            }
        }

        bedrockIntegrations.sendSimpleForm(
            player,
            formBuilder.build()
        )
    }
}