package net.perfectdreams.dreamxizum.listeners

import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendCommand
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.modes.vanilla.CustomXizumMode
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import net.perfectdreams.dreamxizum.structures.XizumBattleRequest
import net.perfectdreams.dreamxizum.structures.XizumRequestHolder
import net.perfectdreams.dreamxizum.tables.dao.XizumProfile
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.jetbrains.exposed.sql.transactions.transaction

class XizumCustomRequestInventoryListener(val m: DreamXizum) : Listener {

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.inventory.holder !is XizumRequestHolder)
            return

        val holder = e.inventory.holder as XizumRequestHolder

        e.isCancelled = true

        if (holder.custom) {
            when (e.slot) {
                XizumRequestHolder.SWITCH -> toggleSwitch(e)
                XizumRequestHolder.PLAYER_WEAPON -> toggleWeapon(e)
                XizumRequestHolder.PLAYER_HELMET -> toggleArmor(e, "helmet")
                XizumRequestHolder.PLAYER_CHESTPLATE -> toggleArmor(e, "chestplate")
                XizumRequestHolder.PLAYER_LEGGINGS -> toggleArmor(e, "leggings")
                XizumRequestHolder.PLAYER_BOOTS -> toggleArmor(e, "boots")
                XizumRequestHolder.OPPONENT_WEAPON -> toggleWeapon(e)
                XizumRequestHolder.OPPONENT_HELMET -> toggleArmor(e, "helmet")
                XizumRequestHolder.OPPONENT_CHESTPLATE -> toggleArmor(e, "chestplate")
                XizumRequestHolder.OPPONENT_LEGGINGS -> toggleArmor(e, "leggings")
                XizumRequestHolder.OPPONENT_BOOTS -> toggleArmor(e, "boots")
                XizumRequestHolder.CONFIRM -> handleConfirm(e, holder)
                XizumRequestHolder.DENY -> {
                    e.whoClicked.sendMessage(textComponent {
                        append(DreamXizum.prefix())
                        appendSpace()
                        color(NamedTextColor.RED)
                        append("Você cancelou o convite!")
                    })

                    e.whoClicked.closeInventory()
                }
                XizumRequestHolder.SWITCH_DROP_HEAD -> {
                    transaction(Databases.databaseNetwork) {
                        val playerProfile = XizumProfile.findOrCreate(e.whoClicked.uniqueId)

                        playerProfile.canDropHead = !playerProfile.canDropHead

                        if (playerProfile.canDropHead) {
                            e.whoClicked.sendMessage(textComponent {
                                append(DreamXizum.prefix())
                                appendSpace()
                                color(NamedTextColor.GREEN)
                                append("Você ativou a opção de dropar cabeças!")
                            })
                        } else {
                            e.whoClicked.sendMessage(textComponent {
                                append(DreamXizum.prefix())
                                appendSpace()
                                color(NamedTextColor.RED)
                                append("Você desativou a opção de dropar cabeças!")
                            })
                        }
                    }
                }
            }
        }
    }

    private fun toggleSwitch(e: InventoryClickEvent) {
        val naked = ItemStack(Material.RED_CONCRETE).meta<ItemMeta> {
            displayName(textComponent {
                color(NamedTextColor.GOLD)
                append("§cApenas mão")
            })
        }

        val withItems = ItemStack(Material.STONE_SWORD).meta<ItemMeta> {
            displayName(textComponent {
                color(NamedTextColor.GOLD)
                append("§aCom Itens")
            })
        }

        when (e.inventory.getItem(e.slot)?.type) {
            Material.STONE_SWORD -> {
                e.inventory.setItem(e.slot, naked)
            }

            Material.RED_CONCRETE -> {
                e.inventory.setItem(e.slot, withItems)
            }

            else -> return
        }
    }
    private fun toggleWeapon(e: InventoryClickEvent) {
        if (e.inventory.getItem(XizumRequestHolder.SWITCH)?.type == Material.RED_CONCRETE) {
            return
        }

        when (e.inventory.getItem(e.slot)?.type) {
            Material.RED_CONCRETE -> {
                e.inventory.setItem(e.slot, ItemStack(Material.WOODEN_SWORD))
            }

            Material.WOODEN_SWORD -> {
                e.inventory.setItem(e.slot, ItemStack(Material.STONE_SWORD))
            }

            Material.STONE_SWORD -> {
                e.inventory.setItem(e.slot, ItemStack(Material.IRON_SWORD))
            }

            Material.IRON_SWORD -> {
                e.inventory.setItem(e.slot, ItemStack(Material.DIAMOND_SWORD))
            }

            Material.DIAMOND_SWORD -> {
                e.inventory.setItem(e.slot, ItemStack(Material.RED_CONCRETE))
            }

            else -> return
        }
    }
    private fun toggleArmor(e: InventoryClickEvent, type: String) {
        if (e.inventory.getItem(XizumRequestHolder.SWITCH)?.type == Material.RED_CONCRETE) {
            return
        }

        when (type) {
            "helmet" -> {
                when (e.inventory.getItem(e.slot)?.type) {
                    Material.RED_CONCRETE -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.LEATHER_HELMET))
                    }

                    Material.LEATHER_HELMET -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.CHAINMAIL_HELMET))
                    }

                    Material.CHAINMAIL_HELMET -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.IRON_HELMET))
                    }

                    Material.IRON_HELMET -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.DIAMOND_HELMET))
                    }

                    Material.DIAMOND_HELMET -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.RED_CONCRETE))
                    }

                    else -> return
                }
            }
            "chestplate" -> {
                when (e.inventory.getItem(e.slot)?.type) {
                    Material.RED_CONCRETE -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.LEATHER_CHESTPLATE))
                    }

                    Material.LEATHER_CHESTPLATE -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.CHAINMAIL_CHESTPLATE))
                    }

                    Material.CHAINMAIL_CHESTPLATE -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.IRON_CHESTPLATE))
                    }

                    Material.IRON_CHESTPLATE -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.DIAMOND_CHESTPLATE))
                    }

                    Material.DIAMOND_CHESTPLATE -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.RED_CONCRETE))
                    }

                    else -> return
                }
            }
            "leggings" -> {
                when (e.inventory.getItem(e.slot)?.type) {
                    Material.RED_CONCRETE -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.LEATHER_LEGGINGS))
                    }

                    Material.LEATHER_LEGGINGS -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.CHAINMAIL_LEGGINGS))
                    }

                    Material.CHAINMAIL_LEGGINGS -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.IRON_LEGGINGS))
                    }

                    Material.IRON_LEGGINGS -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.DIAMOND_LEGGINGS))
                    }

                    Material.DIAMOND_LEGGINGS -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.RED_CONCRETE))
                    }

                    else -> return
                }
            }
            "boots" -> {
                when (e.inventory.getItem(e.slot)?.type) {
                    Material.RED_CONCRETE -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.LEATHER_BOOTS))
                    }

                    Material.LEATHER_BOOTS -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.CHAINMAIL_BOOTS))
                    }

                    Material.CHAINMAIL_BOOTS -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.IRON_BOOTS))
                    }

                    Material.IRON_BOOTS -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.DIAMOND_BOOTS))
                    }

                    Material.DIAMOND_BOOTS -> {
                        e.inventory.setItem(e.slot, ItemStack(Material.RED_CONCRETE))
                    }

                    else -> return
                }
            }
        }
    }
    private fun handleConfirm(e: InventoryClickEvent, holder: XizumRequestHolder) {
        val target = holder.target ?: return

        val playerHelmet = if (e.inventory.getItem(XizumRequestHolder.PLAYER_HELMET)?.type == Material.RED_CONCRETE) null else e.inventory.getItem(XizumRequestHolder.PLAYER_HELMET)
        val playerChestplate = if (e.inventory.getItem(XizumRequestHolder.PLAYER_CHESTPLATE)?.type == Material.RED_CONCRETE) null else e.inventory.getItem(XizumRequestHolder.PLAYER_CHESTPLATE)
        val playerLeggings = if (e.inventory.getItem(XizumRequestHolder.PLAYER_LEGGINGS)?.type == Material.RED_CONCRETE) null else e.inventory.getItem(XizumRequestHolder.PLAYER_LEGGINGS)
        val playerBoots = if (e.inventory.getItem(XizumRequestHolder.PLAYER_BOOTS)?.type == Material.RED_CONCRETE) null else e.inventory.getItem(XizumRequestHolder.PLAYER_BOOTS)
        val playerWeapon = if (e.inventory.getItem(XizumRequestHolder.PLAYER_WEAPON)?.type == Material.RED_CONCRETE) null else e.inventory.getItem(XizumRequestHolder.PLAYER_WEAPON)

        val opponentHelmet = if (e.inventory.getItem(XizumRequestHolder.OPPONENT_HELMET)?.type == Material.RED_CONCRETE) null else e.inventory.getItem(XizumRequestHolder.OPPONENT_HELMET)
        val opponentChestplate = if (e.inventory.getItem(XizumRequestHolder.OPPONENT_CHESTPLATE)?.type == Material.RED_CONCRETE) null else e.inventory.getItem(XizumRequestHolder.OPPONENT_CHESTPLATE)
        val opponentLeggings = if (e.inventory.getItem(XizumRequestHolder.OPPONENT_LEGGINGS)?.type == Material.RED_CONCRETE) null else e.inventory.getItem(XizumRequestHolder.OPPONENT_LEGGINGS)
        val opponentBoots = if (e.inventory.getItem(XizumRequestHolder.OPPONENT_BOOTS)?.type == Material.RED_CONCRETE) null else e.inventory.getItem(XizumRequestHolder.OPPONENT_BOOTS)
        val opponentWeapon = if (e.inventory.getItem(XizumRequestHolder.OPPONENT_WEAPON)?.type == Material.RED_CONCRETE) null else e.inventory.getItem(XizumRequestHolder.OPPONENT_WEAPON)

        val playerItems = if (e.inventory.getItem(XizumRequestHolder.SWITCH)?.type == Material.STONE_SWORD) arrayOf(
            playerHelmet,
            playerChestplate,
            playerLeggings,
            playerBoots,
            playerWeapon
        ) else arrayOf()

        val opponentItems = if (e.inventory.getItem(XizumRequestHolder.SWITCH)?.type == Material.STONE_SWORD) arrayOf(
            opponentHelmet,
            opponentChestplate,
            opponentLeggings,
            opponentBoots,
            opponentWeapon
        ) else arrayOf()


        val newRequest = XizumBattleRequest(
            e.whoClicked as Player,
            target,
            CustomXizumMode(playerItems, opponentItems, m),
            processed = false
        )

        m.queue.add(newRequest)

        e.whoClicked.sendMessage(textComponent {
            append(DreamXizum.prefix())
            appendSpace()
            color(NamedTextColor.GREEN)
            append("Você convidou §b${target.displayName} para uma batalha customizada, espere-o aceitar.")
        })

        target.sendMessage(textComponent {
            append(DreamXizum.prefix())
            appendSpace()
            color(NamedTextColor.GREEN)
            append("§aVocê foi convidado por §b${e.whoClicked.name}§a para uma batalha customizada, para aceitar, use: ")
            appendCommand("/x1 aceitar")
            append(" §aou caso queira negar: ")
            appendCommand("/x1 recusar")
            append("§a.")
            color(NamedTextColor.BLUE)
            append("\n\nItens do jogador: (§b${(e.whoClicked as Player).displayName}§9)")
            append("\n- Capacete: ${playerHelmet?.type?.name ?: "Nada"}")
            append("\n- Peitoral: ${playerChestplate?.type?.name ?: "Nada"}")
            append("\n- Calça: ${playerLeggings?.type?.name ?: "Nada"}")
            append("\n- Botas: ${playerBoots?.type?.name ?: "Nada"}")
            append("\n- Arma: ${playerWeapon?.type?.name ?: "Nada"}")
            append("\n\nItens do oponente: (§b${target.displayName}§9)")
            append("\n- Capacete: ${opponentHelmet?.type?.name ?: "Nada"}")
            append("\n- Peitoral: ${opponentChestplate?.type?.name ?: "Nada"}")
            append("\n- Calça: ${opponentLeggings?.type?.name ?: "Nada"}")
            append("\n- Botas: ${opponentBoots?.type?.name ?: "Nada"}")
            append("\n- Arma: ${opponentWeapon?.type?.name ?: "Nada"}")
        })

        e.whoClicked.closeInventory()
    }

}