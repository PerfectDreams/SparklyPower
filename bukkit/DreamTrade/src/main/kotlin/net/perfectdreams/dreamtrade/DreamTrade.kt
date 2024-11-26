package net.perfectdreams.dreamtrade

import kotlinx.coroutines.Dispatchers
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendCommand
import net.perfectdreams.dreamcore.utils.adventure.lore
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.formatted
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamcorreios.DreamCorreios
import net.perfectdreams.dreamsonecas.SonecasUtils
import net.perfectdreams.dreamsonecas.tables.PlayerSonecas
import net.perfectdreams.dreamtrade.commands.TradeCommand
import net.perfectdreams.dreamtrade.listeners.InventoryListener
import net.perfectdreams.dreamtrade.listeners.PlayerListener
import net.perfectdreams.dreamtrade.structures.TradeSlots
import net.perfectdreams.exposedpowerutils.sql.transaction
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.exposed.sql.selectAll
import java.util.*

class DreamTrade : KotlinPlugin(), Listener {
    companion object {
        val prefix = textComponent {
            append("§8[")
            append("§6§lTrade")
            append("§8]")
        }

        val SONECAS_QUANTITY_KEY = SparklyNamespacedKeyWithType(SparklyNamespacedKey("sonecas_quantity"), PersistentDataType.DOUBLE)
    }

    val playersWithDisabledTrade = mutableSetOf<UUID>()
    val pendingTrades = mutableMapOf<UUID, UUID>()
    val activeTrades = mutableMapOf<Pair<UUID, UUID>, Inventory>()

    val correios = DreamCorreios.getInstance()

    override fun onEnable() {
        super.onEnable()

        registerCommand(TradeCommand(this))
        registerEvents(InventoryListener(this))
        registerEvents(PlayerListener(this))
    }

    override fun onDisable() {
        super.onDisable()
    }

    fun createTradeInventory(playerOne: Player, playerTwo: Player): Inventory {
        logger.info { "Creating trade inventory for ${playerOne.name} (${playerOne.uniqueId}) and ${playerTwo.name} (${playerTwo.uniqueId})!" }

        val inventory = Bukkit.createInventory(null, 54, "§9§l${playerOne.name} §7e §9§l${playerTwo.name}")

        for (slot in TradeSlots.DECORATION_SLOTS) {
            inventory.setItem(slot, ItemStack(Material.GRAY_STAINED_GLASS_PANE).meta<ItemMeta> {
                displayName(textComponent {
                    append("")
                })
            })
        }

        inventory.setItem(TradeSlots.PLAYER_ONE_CONFIRMATION, ItemStack(Material.RED_CONCRETE).meta<ItemMeta> {
            displayName(textComponent {
                color(NamedTextColor.RED)
                append("Não está pronto")
            })
        })

        inventory.setItem(TradeSlots.PLAYER_TWO_CONFIRMATION, ItemStack(Material.RED_CONCRETE).meta<ItemMeta> {
            displayName(textComponent {
                color(NamedTextColor.RED)
                append("Não está pronto")
            })
        })

        TradeSlots.PLAYER_ONE_SONECAS_ROW.forEachIndexed { index, it ->
            when (index) {
                // trade sonecas status
                0 -> {
                    inventory.setItem(it, ItemStack(Material.ORANGE_CONCRETE).meta<ItemMeta> {
                        displayName(textComponent {
                            append("§6Sonecas Inseridas: ")
                            append("§e0.00")
                        })

                        persistentDataContainer.set(SONECAS_QUANTITY_KEY, 0.0)
                    })
                }

                // add 1.000 sonecas
                1 -> {
                    inventory.setItem(it, ItemStack(Material.IRON_INGOT).meta<ItemMeta> {
                        displayName(textComponent {
                            append("§6Adicionar ")
                            append("§e1.000")
                            append("§6 sonecas")
                        })

                        lore {
                            textComponent {
                                color(NamedTextColor.GRAY)
                                append("Use shift para adicionar 10.000 sonecas")
                            }
                        }
                    })
                }

                // add 10.000 sonecas
                2 -> {
                    inventory.setItem(it, ItemStack(Material.GOLD_INGOT).meta<ItemMeta> {
                        displayName(textComponent {
                            append("§6Adicionar ")
                            append("§e10.000")
                            append("§6 sonecas")
                        })

                        lore {
                            textComponent {
                                color(NamedTextColor.GRAY)
                                append("Use shift para adicionar 100.000 sonecas")
                            }
                        }
                    })
                }

                // add 100.000 sonecas
                3 -> {
                    inventory.setItem(it, ItemStack(Material.DIAMOND).meta<ItemMeta> {
                        displayName(textComponent {
                            append("§6Adicionar ")
                            append("§e100.000")
                            append("§6 sonecas")
                        })

                        lore {
                            textComponent {
                                color(NamedTextColor.GRAY)
                                append("Use shift para adicionar 1.000.000 sonecas")
                            }
                        }
                    })
                }
            }
        }

        TradeSlots.PLAYER_TWO_SONECAS_ROW.forEachIndexed { index, it ->
            when (index) {
                // trade sonecas status
                0 -> {
                    inventory.setItem(it, ItemStack(Material.ORANGE_CONCRETE).meta<ItemMeta> {
                        displayName(textComponent {
                            append("§6Sonecas Inseridas: ")
                            append("§e0.00")
                        })

                        persistentDataContainer.set(SONECAS_QUANTITY_KEY, 0.0)
                    })
                }

                // add 1.000 sonecas
                1 -> {
                    inventory.setItem(it, ItemStack(Material.IRON_INGOT).meta<ItemMeta> {
                        displayName(textComponent {
                            append("§6Adicionar ")
                            append("§e1.000")
                            append("§6 sonecas")
                        })

                        lore {
                            textComponent {
                                color(NamedTextColor.GRAY)
                                append("Use shift para adicionar 10.000 sonecas")
                            }
                        }
                    })
                }

                // add 10.000 sonecas
                2 -> {
                    inventory.setItem(it, ItemStack(Material.GOLD_INGOT).meta<ItemMeta> {
                        displayName(textComponent {
                            append("§6Adicionar ")
                            append("§e10.000")
                            append("§6 sonecas")
                        })

                        lore {
                            textComponent {
                                color(NamedTextColor.GRAY)
                                append("Use shift para adicionar 100.000 sonecas")
                            }
                        }
                    })
                }

                // add 100.000 sonecas
                3 -> {
                    inventory.setItem(it, ItemStack(Material.DIAMOND).meta<ItemMeta> {
                        displayName(textComponent {
                            append("§6Adicionar ")
                            append("§e100.000")
                            append("§6 sonecas")
                        })

                        lore {
                            textComponent {
                                color(NamedTextColor.GRAY)
                                append("Use shift para adicionar 1.000.000 sonecas")
                            }
                        }
                    })
                }
            }
        }

        return inventory
    }

    fun getTradeDetails(playerId: UUID): Pair<Inventory?, Pair<UUID, UUID>?> {
        logger.info { "Trying to get an active trade with provided UUID: $playerId." }

        val tradeEntry = activeTrades.entries.firstOrNull { it.key.first == playerId || it.key.second == playerId }

        if (tradeEntry == null) {
            logger.warning { "It was not possible to get an active trade with this UUID! Returning null." }
        }

        return tradeEntry?.value to tradeEntry?.key
    }

    fun processTrade(playersUniqueId: Pair<UUID, UUID>, inventory: Inventory) {
        logger.info { "Processing trade (${playersUniqueId.first}, ${playersUniqueId.second})!" }

        val (playerOneUniqueId, playerTwoUniqueId) = playersUniqueId

        val playerOne = Bukkit.getPlayer(playerOneUniqueId) ?: run {
            return
        }
        val playerTwo = Bukkit.getPlayer(playerTwoUniqueId) ?: return

        val (tradeInventory, tradePair) = getTradeDetails(playerOneUniqueId)

        val playerOneSonecas = tradeInventory?.getItem(TradeSlots.PLAYER_ONE_SONECAS_ROW[0])?.itemMeta?.persistentDataContainer?.get(SONECAS_QUANTITY_KEY) ?: 0.0
        val playerTwoSonecas = tradeInventory?.getItem(TradeSlots.PLAYER_TWO_SONECAS_ROW[0])?.itemMeta?.persistentDataContainer?.get(SONECAS_QUANTITY_KEY) ?: 0.0

        activeTrades.remove(tradePair)

        playerOne.closeInventory()
        playerTwo.closeInventory()

        val playerOneItems = buildList {
            for (slot in TradeSlots.PLAYER_ONE_AVAILABLE_SLOTS) {
                add(inventory.getItem(slot) ?: continue)
            }
        }

        logger.info { "PlayerOneItems: ${playerOneItems.map { it.type }} (${playerOneItems.size})" }

        val playerTwoItems = buildList {
            for (slot in TradeSlots.PLAYER_TWO_AVAILABLE_SLOTS) {
                add(inventory.getItem(slot) ?: continue)
            }
        }

        logger.info { "PlayerTwoItems: ${playerTwoItems.map { it.type }} (${playerTwoItems.size})" }

        if (playerOneItems.isEmpty() && playerTwoItems.isEmpty()) {
            if (playerTwoSonecas != 0.0 || playerOneSonecas != 0.0) {
                logger.warning { "Tried to trade no items, only sonecas." }

                listOf(playerOne, playerTwo).forEach {
                    it.sendMessage(textComponent {
                        append(prefix)
                        appendSpace()
                        color(NamedTextColor.RED)
                        append("Se vocês querem trocar sonecas por sonecas, use o comando ")
                        appendCommand("/sonecas pagar <jogador> <quantia>")
                        append("!")
                    })
                }

                return
            }

            logger.warning { "Tried to trade nothing, not even sonecas." }

            listOf(playerOne, playerTwo).forEach {
                it.sendMessage(textComponent {
                    append(prefix)
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Bom... Se vocês tentaram trocar vento por vento, parabéns! Vocês conseguiram.")
                })
            }

            return
        }

        launchAsyncThread {
            if (playerOneSonecas != 0.0) {
                SonecasUtils.transferSonecasFromPlayerToPlayer(
                    playerOne.name,
                    playerOneUniqueId,
                    playerTwoUniqueId,
                    playerOneSonecas,
                    true
                )
            }

            if (playerTwoSonecas != 0.0) {
                SonecasUtils.transferSonecasFromPlayerToPlayer(
                    playerTwo.name,
                    playerTwoUniqueId,
                    playerOneUniqueId,
                    playerTwoSonecas,
                    true
                )
            }

            onMainThread {
                for (item in playerOneItems) {
                    if (playerTwo.inventory.canHoldItem(item)) {
                        playerTwo.inventory.addItem(item)
                    } else {
                        correios.addItem(playerTwo, item)

                        playerTwo.sendMessage(textComponent {
                            append(prefix)
                            appendSpace()
                            color(NamedTextColor.GOLD)
                            append("Como você não tinha espaço suficiente no inventário, os itens foram enviados para o seu correio! Digite ")
                            appendCommand("/warp correios")
                            append(" para buscá-los!")
                        })
                    }
                }

                for (item in playerTwoItems) {
                    if (playerOne.inventory.canHoldItem(item)) {
                        playerOne.inventory.addItem(item)
                    } else {
                        correios.addItem(playerOne, item)

                        playerOne.sendMessage(textComponent {
                            append(prefix)
                            appendSpace()
                            color(NamedTextColor.GOLD)
                            append("Como você não tinha espaço suficiente no inventário, os itens foram enviados para o seu correio! Digite ")
                            appendCommand("/warp correios")
                            append(" para buscá-los!")
                        })
                    }
                }

                playerOne.sendMessage(textComponent {
                    append(prefix)
                    appendSpace()
                    color(NamedTextColor.GREEN)
                    append("Troca efetuada com sucesso! Você e ")
                    append(NamedTextColor.GOLD, playerTwo.name)
                    append(" Finalizaram a troca!")
                })

                playerTwo.sendMessage(textComponent {
                    append(prefix)
                    appendSpace()
                    color(NamedTextColor.GREEN)
                    append("Troca efetuada com sucesso! Você e ")
                    append(NamedTextColor.GOLD, playerOne.name)
                    append(" Finalizaram a troca!")
                })

                listOf(playerOne, playerTwo).forEach {
                    it.playSound(it.location, "entity.player.levelup", 1f, 1f)
                }

                logger.info { "Trade processed successfully! Total sonecas: ${playerOneSonecas + playerTwoSonecas} (playerOne: $playerOneSonecas; playerTwo: ${playerTwoSonecas})" }
                logger.info { "playerOne Items: ${playerOneItems.map { it.type }} (${playerOneItems.size}); playerTwo: ${playerTwoItems.map { it.type }} (${playerTwoItems.size})" }
            }
        }
    }

    fun handleSonecas(e: InventoryClickEvent, isPlayerOne: Boolean) {
        e.isCancelled = true

        launchAsyncThread {
            val player = e.whoClicked as Player
            val databasePlayerMoney = transaction(Dispatchers.IO, Databases.databaseNetwork) {
                PlayerSonecas.selectAll()
                    .where {
                        PlayerSonecas.id eq player.uniqueId
                    }
                    .firstOrNull()
                    ?.get(PlayerSonecas.money)
                    ?.toDouble() ?: 0.0
            }

            onMainThread {
                val slots = if (isPlayerOne) TradeSlots.PLAYER_ONE_SONECAS_ROW else TradeSlots.PLAYER_TWO_SONECAS_ROW

                var sonecas = e.inventory.getItem(slots[0])?.itemMeta?.persistentDataContainer?.get(SONECAS_QUANTITY_KEY) ?: 0.0

                when (e.slot) {
                    slots[1] -> sonecas += if (e.isShiftClick) 10000.0 else 1000.0
                    slots[2] -> sonecas += if (e.isShiftClick) 100000.0 else 10000.0
                    slots[3] -> sonecas += if (e.isShiftClick) 1000000.0 else 100000.0
                }

                if (sonecas > databasePlayerMoney) {
                    player.sendMessage(textComponent {
                        append(prefix)
                        appendSpace()
                        color(NamedTextColor.RED)
                        append("Você não tem sonecas suficiente para adicionar!")
                    })

                    return@onMainThread
                }

                updateSonecasSlot(e.inventory, slots[0], sonecas)
            }
        }
    }

    private fun updateSonecasSlot(inventory: Inventory, slot: Int, sonecas: Double) {
        inventory.setItem(slot, ItemStack(Material.ORANGE_CONCRETE).meta<ItemMeta> {
            displayName(textComponent {
                append("§6Quantidade de Sonecas: ")
                append("§e${sonecas.formatted}")
            })

            persistentDataContainer.set(SONECAS_QUANTITY_KEY, sonecas)
        })
    }
}