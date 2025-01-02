package net.perfectdreams.dreamxizum.structures

import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.tables.dao.XizumProfile
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import net.perfectdreams.dreamxizum.utils.XizumRank
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.jetbrains.exposed.sql.transactions.transaction

class XizumRequestHolder(
    val m: DreamXizum,
    val custom: Boolean,
    val target: Player? = null
) : InventoryHolder {
    companion object {
        const val STANDARD = 10
        const val PVP_WITH_SOUP = 12
        const val PVP_WITH_POTION = 14
        const val COMPETITIVE = 16
        const val SWITCH = 22
        const val PLAYER_WEAPON = 29
        const val PLAYER_HELMET = 10
        const val PLAYER_CHESTPLATE = 19
        const val PLAYER_LEGGINGS = 28
        const val PLAYER_BOOTS = 37
        const val OPPONENT_WEAPON = 33
        const val OPPONENT_HELMET = 16
        const val OPPONENT_CHESTPLATE = 25
        const val OPPONENT_LEGGINGS = 34
        const val OPPONENT_BOOTS = 43
        const val CONFIRM = 48
        const val DENY = 50
        const val WINS = 37
        const val LOSSES = 39
        const val KDR = 41
        const val RATING = 43
        const val SWITCH_DROP_HEAD = 4

        val CONCRETES = listOf(
            PLAYER_WEAPON,
            PLAYER_HELMET,
            PLAYER_CHESTPLATE,
            PLAYER_LEGGINGS,
            PLAYER_BOOTS,
            OPPONENT_BOOTS,
            OPPONENT_LEGGINGS,
            OPPONENT_CHESTPLATE,
            OPPONENT_HELMET,
            OPPONENT_WEAPON
        )

        fun build(m: DreamXizum, player: Player, target: Player? = null, custom: Boolean = false): Inventory {
            val holder = XizumRequestHolder(m, custom, target)
            val inv = Bukkit.createInventory(holder, 54, holder.title)

            val playerProfile = transaction(Databases.databaseNetwork) {
                XizumProfile.findOrCreate(player.uniqueId)
            }

            inv.setItem(SWITCH_DROP_HEAD, ItemStack(Material.PLAYER_HEAD).meta<ItemMeta> {
                if (playerProfile.canDropHead) {
                    displayName(textComponent {
                        color(NamedTextColor.GREEN)
                        append("Dropar cabeça")
                    })
                } else {
                    displayName(textComponent {
                        color(NamedTextColor.RED)
                        append("Não dropar cabeça")
                    })
                }
            })

            if (custom) {
                inv.setItem(SWITCH, ItemStack(Material.RED_CONCRETE).meta<ItemMeta> {
                    displayName(textComponent {
                        color(NamedTextColor.GOLD)
                        append("Apenas mão.")
                    })
                })

                CONCRETES.forEach {
                    inv.setItem(it, ItemStack(Material.RED_CONCRETE).meta<ItemMeta> {
                        displayName(textComponent {
                            color(NamedTextColor.GOLD)
                            append("")
                        })
                    })
                }

                inv.setItem(CONFIRM, ItemStack(Material.EMERALD_BLOCK).meta<ItemMeta> {
                    displayName(textComponent {
                        color(NamedTextColor.GREEN)
                        append("Confirmar")
                    })
                })

                inv.setItem(DENY, ItemStack(Material.REDSTONE_BLOCK).meta<ItemMeta> {
                    displayName(textComponent {
                        color(NamedTextColor.RED)
                        append("Cancelar")
                    })
                })
            } else {
                inv.setItem(STANDARD, ItemStack(Material.IRON_SWORD).apply {
                    itemMeta = itemMeta.apply {
                        if (m.queue.any { it.player == player && it.mode.enum == XizumBattleMode.STANDARD }) {
                            displayName(textComponent {
                                append("§6Padrão")
                                append(" §7(§cJá está na fila§7)")
                            })
                        } else {
                            displayName(textComponent {
                                append("§6Padrão")
                            })
                        }
                        lore = listOf(
                            "§7Clique para entrar nessa fila!",
                            "§7Você batalhará com itens que estiverem no seu inventário!"
                        )
                    }
                })

                inv.setItem(PVP_WITH_SOUP, ItemStack(Material.MUSHROOM_STEW).apply {
                    itemMeta = itemMeta.apply {
                        if (m.queue.any { it.player == player && it.mode.enum == XizumBattleMode.PVP_WITH_SOUP }) {
                            displayName(textComponent {
                                append("§6PvP com Sopa")
                                append(" §7(§cJá está na fila§7)")
                            })
                        } else {
                            displayName(textComponent {
                                append("§6PvP com Sopa")
                            })
                        }
                        lore = listOf(
                            "§7Clique para entrar nessa fila!"
                        )
                    }
                })

                inv.setItem(PVP_WITH_POTION, ItemStack(Material.POTION).apply {
                    itemMeta = itemMeta.apply {
                        if (m.queue.any { it.player == player && it.mode.enum == XizumBattleMode.PVP_WITH_POTION }) {
                            displayName(textComponent {
                                append("§6PvP com Poção")
                                append(" §7(§cJá está na fila§7)")
                            })
                        } else {
                            displayName(textComponent {
                                append("§6PvP com Poção")
                            })
                        }
                        lore = listOf(
                            "§7Clique para entrar nessa fila!"
                        )
                    }
                })

                inv.setItem(COMPETITIVE, ItemStack(Material.DIAMOND_SWORD).apply {
                    itemMeta = itemMeta.apply {
                        if (m.queue.any { it.player == player && it.mode.enum == XizumBattleMode.COMPETITIVE }) {
                            displayName(textComponent {
                                append("§6Competitivo (Ranqueado)")
                                append(" §7(§cJá está na fila§7)")
                            })
                        } else {
                            displayName(textComponent {
                                append("§6Competitivo (Ranqueado)")
                            })
                        }
                        lore = listOf(
                            "§7Clique para entrar nessa fila!",
                            "§7Você tem §b${playerProfile.rating} §7de RP (Rating Points)"
                        )
                    }
                })

                inv.setItem(WINS, ItemStack(Material.GREEN_CONCRETE).apply {
                    itemMeta = itemMeta.apply {
                        displayName(textComponent {
                            append("§6Vitórias")
                        })
                        lore = listOf(
                            "§7Você tem §b${playerProfile.wins} §7vitórias!"
                        )
                    }
                })

                inv.setItem(LOSSES, ItemStack(Material.RED_CONCRETE).apply {
                    itemMeta = itemMeta.apply {
                        displayName(textComponent {
                            append("§6Derrotas")
                        })
                        lore = listOf(
                            "§7Você tem §b${playerProfile.losses} §7derrotas!"
                        )
                    }
                })

                val kdr = if (playerProfile.losses == 0) {
                    playerProfile.wins
                } else {
                    playerProfile.wins / playerProfile.losses
                }

                inv.setItem(KDR, ItemStack(Material.YELLOW_CONCRETE).apply {
                    itemMeta = itemMeta.apply {
                        displayName(textComponent {
                            append("§6KDR")
                        })
                        lore = listOf(
                            "§7Seu KDR é de §b$kdr§7!"
                        )
                    }
                })

                inv.setItem(RATING, ItemStack(Material.BLUE_CONCRETE).apply {
                    itemMeta = itemMeta.apply {
                        displayName(textComponent {
                            append("§6Pontos de Combate (${XizumRank.getTextByRating(playerProfile.rating)}§6)")
                        })
                        lore = listOf(
                            "§7Você tem §b${playerProfile.rating} §7de PdC (Pontos de Combate)!"
                        )
                    }
                })
            }

            return inv
        }
    }

    val title = if (custom) "§6§lCustomize sua batalha" else "§e§lSelecione o modo de batalha"

    override fun getInventory(): Inventory {
        TODO("Not yet implemented")
    }
}