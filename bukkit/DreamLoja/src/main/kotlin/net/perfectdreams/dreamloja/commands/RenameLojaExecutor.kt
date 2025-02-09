package net.perfectdreams.dreamloja.commands

import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendCommand
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamloja.DreamLoja
import net.perfectdreams.dreamloja.dao.Shop
import net.perfectdreams.dreamloja.tables.Shops
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.transactions.transaction

class RenameLojaExecutor(m: DreamLoja) : LojaExecutorBase(m) {
    inner class Options : CommandOptions() {
        val newShopName = optionalGreedyString("new_shop_name")
    }

    override val options = Options()

    override fun execute(context: CommandContext, args: CommandArguments) {
        val player = context.requirePlayer()

        // use the existent parseLojaNameOrNull function to parse the new shop name
        val newShopName = m.parseLojaNameOrNull(args[options.newShopName])

        if (newShopName == null) {
            m.launchAsyncThread {
                val playerShops = transaction(Databases.databaseNetwork) {
                    Shop.find { (Shops.owner eq player.uniqueId) }
                        .toList()
                }.sortedBy { it.order ?: Int.MAX_VALUE }

                if (playerShops.isNotEmpty()) {
                    if (m.selectedShopsToRename[player.uniqueId] == null) {
                        onMainThread {
                            val menu = createMenu(InventoryUtils.roundToNearestMultipleOfNine(playerShops.size).coerceAtLeast(9), "§c§lSelecione a Loja!") {
                                // Map it down to our inventory maps, split over to 9 first tho
                                playerShops.chunked(9)
                                    .forEachIndexed { yIndex, shops ->
                                        val charMap = DreamLoja.INVENTORY_POSITIONS_MAPS[shops.size] ?: "XXXXXXXXX" // fallback

                                        var shopIndex = 0
                                        for ((xIndex, char) in charMap.withIndex()) {
                                            if (char == 'X') {
                                                val shop = shops.getOrNull(shopIndex++) ?: break // If there isn't enough shops, break out!
                                                slot(xIndex, yIndex) {
                                                    item = shop.iconItemStack?.let { ItemUtils.deserializeItemFromBase64(it) } ?: ItemStack(
                                                        Material.DIAMOND_BLOCK)
                                                        .rename("§a${shop.shopName}")

                                                    onClick {
                                                        player.closeInventory()
                                                        m.selectedShopsToRename[player.uniqueId] = shop
                                                        context.sendLojaMessage {
                                                            color(NamedTextColor.GREEN)
                                                            append("Você selecionou a loja §b${shop.shopName}§a! Agora para mudar o nome de acesso, digite: ")
                                                            appendCommand("/loja manage rename <novo_nome>")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                            }

                            menu.sendTo(player)
                        }
                        return@launchAsyncThread
                    } else {
                        onMainThread {
                            context.sendLojaMessage {
                                color(NamedTextColor.RED)
                                content("Você está com uma loja selecionada para renomear! Digite: ")
                                appendCommand("/loja manage rename <novo_nome>")
                                append(" para renomear a loja!")
                            }
                        }
                    }
                } else {
                    onMainThread {
                        context.sendLojaMessage {
                            color(NamedTextColor.RED)
                            content("Você não possui nenhuma loja!")
                        }
                    }
                }
            }
        } else {
            m.launchAsyncThread {
                val shop = m.selectedShopsToRename[player.uniqueId]

                if (shop == null) {
                    onMainThread {
                        context.sendLojaMessage {
                            color(NamedTextColor.RED)
                            content("Você não selecionou nenhuma loja para renomear!")
                        }
                    }
                    return@launchAsyncThread
                }

                transaction(Databases.databaseNetwork) {
                    shop.shopName = newShopName
                }

                m.selectedShopsToRename.remove(player.uniqueId)

                onMainThread {
                    context.sendLojaMessage {
                        color(NamedTextColor.GREEN)
                        content("Loja renomeada com sucesso!")
                    }
                }
            }
        }
    }
}