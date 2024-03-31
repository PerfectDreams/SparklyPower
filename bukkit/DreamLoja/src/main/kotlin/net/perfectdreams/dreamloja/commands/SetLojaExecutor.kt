package net.perfectdreams.dreamloja.commands

import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendCommand
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.extensions.canPlaceAt
import net.perfectdreams.dreamcore.utils.extensions.isUnsafe
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamloja.DreamLoja
import net.perfectdreams.dreamloja.dao.Shop
import net.perfectdreams.dreamloja.tables.ShopWarpUpgrades
import net.perfectdreams.dreamloja.tables.Shops
import org.bukkit.Material
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class SetLojaExecutor(m: DreamLoja) : LojaExecutorBase(m) {
    inner class Options : CommandOptions() {
            // Needs to be a greedy string because Minecraft doesn't allow special characters on a optionalWord
            val shopName = optionalGreedyString("shop_name")
        }

        override val options = Options()

    override fun execute(context: CommandContext, args: CommandArguments) {
        val player = context.requirePlayer()

        val shopName = m.parseLojaName(args[options.shopName])

        val location = player.location
        if (location.isUnsafe) {
            context.sendLojaMessage {
                color(NamedTextColor.RED)
                content("A sua localização atual é insegura! Vá para um lugar mais seguro antes de marcar a sua loja!")
            }
            return
        }

        if (!player.canPlaceAt(location, Material.DIRT)) {
            context.sendLojaMessage {
                color(NamedTextColor.RED)
                content("Você não pode marcar uma loja, pois você não tem permissão para construir neste local!")
            }
            return
        }

        var createdNew = false
        var valid = true

        m.launchAsyncThread {
            val shopCountForPlayer = getMaxAllowedShops(player)

            transaction(Databases.databaseNetwork) {
                val shop = Shop.find {
                    (Shops.owner eq player.uniqueId) and (Shops.shopName eq shopName)
                }.firstOrNull()

                val isNew = shop == null

                val shopCount = transaction(Databases.databaseNetwork) {
                    Shop.find { (Shops.owner eq player.uniqueId) }.count()
                }

                if (isNew) {
                    if (shopCount + 1 > shopCountForPlayer) {
                        context.sendLojaMessage {
                            color(NamedTextColor.RED)

                            append("Você já tem muitas lojas! Delete algumas usando ")
                            appendCommand("/loja manage delete")
                            append("!")
                        }

                        valid = false
                        return@transaction
                    }
                    createdNew = true
                    Shop.new {
                        this.owner = player.uniqueId
                        this.shopName = shopName
                        setLocation(location)
                    }
                } else {
                    shop!!.setLocation(location)
                }
            }

            onMainThread {
                if (!valid)
                    return@onMainThread

                if (shopName == "loja") {
                    if (createdNew) {
                        context.sendLojaMessage {
                            color(NamedTextColor.GREEN)

                            append("Sua loja foi criada com sucesso! Outros jogadores podem ir até ela utilizando ")
                            appendCommand("/loja ${player.name}")
                            append("!")
                        }
                    } else {
                        context.sendLojaMessage {
                            color(NamedTextColor.GREEN)

                            append("Sua loja foi atualizada com sucesso! Outros jogadores podem ir até ela utilizando ")
                            appendCommand("/loja ${player.name}")
                            append("!")
                        }
                    }
                } else {
                    if (createdNew) {
                        context.sendLojaMessage {
                            color(NamedTextColor.GREEN)

                            append("Sua loja foi criada com sucesso! Outros jogadores podem ir até ela utilizando ")
                            appendCommand("/loja ${player.name} $shopName")
                            append("!")
                        }
                    } else {
                        context.sendLojaMessage {
                            color(NamedTextColor.GREEN)

                            append("Sua loja foi atualizada com sucesso! Outros jogadores podem ir até ela utilizando ")
                            appendCommand("/loja ${player.name} $shopName")
                            append("!")
                        }
                    }
                }

                if (shopCountForPlayer != 1L) {
                    context.sendLojaMessage {
                        color(NamedTextColor.YELLOW)

                        append("Sabia que é possível alterar o ícone da sua loja na ")
                        appendCommand("/loja ${player.name}")
                        append("? Use ")
                        appendCommand("/loja manage icon $shopName")
                        append(" com o item na mão!")
                    }
                }
            }
        }
    }

    /**
     * Gets the max allowed homes for the [player]
     */
    suspend fun getMaxAllowedShops(player: Player): Long {
        val baseSlots = when {
            player.hasPermission("dreamloja.lojaplusplusplus") -> DreamLoja.VIP_PLUS_PLUS_MAX_SLOTS
            player.hasPermission("dreamloja.lojaplusplus") -> DreamLoja.VIP_PLUS_MAX_SLOTS
            player.hasPermission("dreamloja.lojaplus") -> DreamLoja.VIP_MAX_SLOTS
            else -> DreamLoja.MEMBER_MAX_SLOTS
        }

        val upgradeCount = transaction(Databases.databaseNetwork) {
            ShopWarpUpgrades.select {
                ShopWarpUpgrades.playerId eq player.uniqueId
            }.count()
        }

        return baseSlots + upgradeCount
    }
}