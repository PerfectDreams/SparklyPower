package net.perfectdreams.dreamsinuca.listeners

import io.papermc.paper.math.Position
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.displaced
import net.perfectdreams.dreamcore.utils.extensions.rightClick
import net.perfectdreams.dreamcore.utils.get
import net.perfectdreams.dreamcustomitems.items.SparklyItemsRegistry
import net.perfectdreams.dreamsinuca.DreamSinuca
import net.perfectdreams.dreamsinuca.DreamSinuca.Companion.POOL_TABLE_ENTITY
import net.perfectdreams.dreamsinuca.sinuca.FinishReason
import net.perfectdreams.dreamsinuca.sinuca.PoolTable
import net.perfectdreams.dreamsinuca.sinuca.PoolTableData
import net.perfectdreams.dreamsinuca.sinuca.PoolTableOrientation
import org.bukkit.Bukkit
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.event.world.EntitiesUnloadEvent
import org.bukkit.inventory.EquipmentSlot

class SinucaEntityListener(val m: DreamSinuca) : Listener {
    @EventHandler
    fun onEntityLoad(e: EntitiesLoadEvent) {
        for (entity in e.entities) {
            if (entity is ItemDisplay) {
                val poolTableDataSerialized = entity.persistentDataContainer.get(POOL_TABLE_ENTITY)

                if (poolTableDataSerialized != null) {
                    val poolTableData = Json.decodeFromString<PoolTableData>(poolTableDataSerialized)

                    // Setup the active PoolTable
                    val poolTable = PoolTable(m, entity, poolTableData.orientation)
                    poolTable.configure()
                    m.poolTables[entity] = poolTable
                }
            }
        }
    }

    @EventHandler
    fun onEntityUnload(e: EntitiesUnloadEvent) {
        for (entity in e.entities) {
            if (entity is ItemDisplay) {
                // The pool table should be torn down when unloaded, and any active sinucas should be cancelled
                val poolTable = m.poolTables.remove(entity)
                poolTable?.tearDown()
            }
        }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        if (!e.rightClick)
            return

        if (e.hand == EquipmentSlot.HAND)
            return

        val block = e.clickedBlock ?: return

        for (sinuca in m.poolTables) {
            if (sinuca.value.poolTableEntity.world != e.player.world)
                continue

            // Bukkit.broadcastMessage("${Position.block(block.location)}")
            if (sinuca.value.blocks.contains(Position.block(block.location))) {
                // e.player.sendMessage("Interacted with sinuca block!")
                e.isCancelled = true

                val activeSinuca = sinuca.value.activeSinuca
                if (activeSinuca != null) {
                    val isInCurrentSinuca = activeSinuca.player1 == e.player || activeSinuca.player2 == e.player

                    // Only send the message if the player is NOT in the current active sinuca
                    if (!isInCurrentSinuca) {
                        sinuca.value.sendSinucaMessageToPlayer(
                            e.player,
                            textComponent {
                                color(NamedTextColor.RED)
                                content("Já está acontecendo um jogo de sinuca nesta mesa! Você precisa esperar a sinuca atual acabar antes de tentar entrar.")
                            }
                        )
                    }
                    return
                }

                val pendingPlayer = sinuca.value.pendingPlayer

                if (pendingPlayer != null) {
                    if (pendingPlayer == e.player) {
                        sinuca.value.pendingPlayer = null

                        sinuca.value.sendSinucaMessageToPlayer(
                            e.player,
                            textComponent {
                                color(NamedTextColor.GREEN)
                                content("Você saiu da fila desta mesa de sinuca...")
                            }
                        )
                        return
                    }

                    val hasStick = m.checkIfPlayerHasCueStickInInventory(e.player)
                    if (!hasStick) {
                        sinuca.value.sendSinucaMessageToPlayer(
                            e.player,
                            textComponent {
                                color(NamedTextColor.RED)
                                content("Você precisa ter um taco de sinuca no seu inventário para entrar na fila da mesa de sinuca!")
                            }
                        )
                        return
                    }

                    // Does the pending player has a cue stick?
                    val hasStickPendingPlayer = m.checkIfPlayerHasCueStickInInventory(pendingPlayer)
                    if (!hasStickPendingPlayer) {
                        // Okay, so the pending player does NOT have a cue stick (did they remove it from their inventory?)
                        // Let's join the queue as is
                        sinuca.value.joinQueue(e.player)
                        return
                    }

                    // Start a sinuca between the two players!
                    // Let's shuffle it a bit
                    val bothPlayers = listOf(e.player, pendingPlayer).shuffled().toMutableList()

                    sinuca.value.start8BallPool(
                        bothPlayers.removeFirst(),
                        bothPlayers.removeFirst()
                    )
                } else {
                    sinuca.value.joinQueue(e.player)
                }
            }
        }
    }

    @EventHandler
    fun onMove(e: PlayerMoveEvent) {
        if (!e.displaced)
            return

        // If we get too far from the pool table, we should automatically cancel any sinucas
        for (sinuca in m.poolTables) {
            if (sinuca.value.pendingPlayer == e.player) {
                val isTableNearMe = sinuca.value.isNearTable(e.player)
                if (!isTableNearMe) {
                    sinuca.value.pendingPlayer = null

                    sinuca.value.sendSinucaMessageToPlayer(
                        e.player,
                        textComponent {
                            color(NamedTextColor.YELLOW)
                            content("Você saiu da fila da sinuca pois você está muito longe da mesa!")
                        }
                    )
                }
            }

            val activeSinuca = sinuca.value.activeSinuca

            if (activeSinuca != null) {
                if (activeSinuca.player1 == e.player) {
                    val isTableNearMe = sinuca.value.isNearTable(e.player)
                    if (!isTableNearMe) {
                        sinuca.value.sendSinucaMessageToPlayer(
                            e.player,
                            textComponent {
                                color(NamedTextColor.YELLOW)
                                content("A sinuca foi cancelada pois você está muito longe dela!")
                            }
                        )

                        sinuca.value.sendSinucaMessageToPlayersNearIt(
                            textComponent {
                                color(NamedTextColor.YELLOW)
                                appendTextComponent {
                                    content("A sinuca foi cancelada pois ")
                                }
                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(e.player.name)
                                }
                                appendTextComponent {
                                    content(" está muito longe dela!")
                                }
                            }
                        )

                        sinuca.value.cancelActive8BallPool(activeSinuca.player2, e.player, FinishReason.PLAYER_TOO_FAR_AWAY)
                    }
                }

                if (activeSinuca.player2 == e.player) {
                    val isTableNearMe = sinuca.value.isNearTable(e.player)
                    if (!isTableNearMe) {
                        sinuca.value.sendSinucaMessageToPlayer(
                            e.player,
                            textComponent {
                                color(NamedTextColor.YELLOW)
                                content("A sinuca foi cancelada pois você está muito longe dela!")
                            }
                        )

                        sinuca.value.sendSinucaMessageToPlayersNearIt(
                            textComponent {
                                color(NamedTextColor.YELLOW)
                                appendTextComponent {
                                    content("A sinuca foi cancelada pois ")
                                }
                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(e.player.name)
                                }
                                appendTextComponent {
                                    content(" está muito longe dela!")
                                }
                            }
                        )

                        sinuca.value.cancelActive8BallPool(activeSinuca.player1, e.player, FinishReason.PLAYER_TOO_FAR_AWAY)
                    }
                }
            }
        }
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        // If we leave, we should automatically cancel any sinucas that we are in
        for (sinuca in m.poolTables) {
            if (sinuca.value.pendingPlayer == e.player) {
                sinuca.value.pendingPlayer = null
            }

            val activeSinuca = sinuca.value.activeSinuca

            if (activeSinuca != null) {
                if (activeSinuca.player1 == e.player || activeSinuca.player2 == e.player) {
                    val winner = if (activeSinuca.player1 == e.player) {
                        activeSinuca.player2
                    } else {
                        activeSinuca.player1
                    }

                    val loser = if (activeSinuca.player2 == e.player) {
                        activeSinuca.player1
                    } else {
                        activeSinuca.player2
                    }

                    sinuca.value.cancelActive8BallPool(winner, loser, FinishReason.PLAYER_QUIT)

                    sinuca.value.sendSinucaMessageToPlayersNearIt(
                        textComponent {
                            color(NamedTextColor.YELLOW)
                            appendTextComponent {
                                content("A sinuca foi cancelada pois ")
                            }
                            appendTextComponent {
                                color(NamedTextColor.AQUA)
                                content(e.player.name)
                            }
                            appendTextComponent {
                                content(" saiu do SparklyPower!")
                            }
                        }
                    )
                }
            }
        }
    }
}