package net.perfectdreams.dreamxizum.listeners

import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.modes.AbstractXizumBattleMode
import net.perfectdreams.dreamxizum.modes.vanilla.*
import net.perfectdreams.dreamxizum.structures.XizumBattleRequest
import net.perfectdreams.dreamxizum.structures.XizumRequestHolder
import net.perfectdreams.dreamxizum.tables.dao.XizumProfile
import net.perfectdreams.dreamxizum.utils.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.jetbrains.exposed.sql.transactions.transaction

class XizumRequestInventoryListener(val m: DreamXizum) : Listener {
    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.inventory.holder !is XizumRequestHolder)
            return

        e.isCancelled = true
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.clickedInventory?.holder !is XizumRequestHolder)
            return

        val holder = e.clickedInventory!!.holder as XizumRequestHolder

        if (holder.custom) {
            return
        }

        val request = m.queue.firstOrNull { it.player == e.whoClicked }

        e.isCancelled = true

        when (e.slot) {
            XizumRequestHolder.STANDARD -> changeQueue(e, XizumBattleMode.STANDARD, request, request != null)

            XizumRequestHolder.PVP_WITH_SOUP -> changeQueue(e,XizumBattleMode.PVP_WITH_SOUP, request, request != null)

            XizumRequestHolder.PVP_WITH_POTION -> changeQueue(e,XizumBattleMode.PVP_WITH_POTION, request, request != null)

            XizumRequestHolder.COMPETITIVE -> changeQueue(e, XizumBattleMode.COMPETITIVE, request, request != null)

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

                    e.whoClicked.closeInventory()
                }
            }
        }
    }

    private fun createModeInstance(mode: XizumBattleMode): AbstractXizumBattleMode {
        return when (mode) {
            XizumBattleMode.STANDARD -> StandardXizumMode(m)
            XizumBattleMode.PVP_WITH_SOUP -> PvPWithSoupXizumMode(m)
            XizumBattleMode.PVP_WITH_POTION -> PvPWithPotionXizumMode(m)
            XizumBattleMode.COMPETITIVE -> CompetitiveXizumMode(m)
            else -> throw IllegalArgumentException("Invalid mode!")
        }
    }

    private fun changeQueue(e: InventoryClickEvent, mode: XizumBattleMode, request: XizumBattleRequest?, isInQueue: Boolean) {
        if (isInQueue) {
            if (mode == request!!.mode.enum) {
                m.queue.remove(request)

                e.whoClicked.sendMessage(textComponent {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você saiu da fila §b${XizumBattleMode.prettify(mode)}§c!")
                })
            } else {
                m.queue.remove(request)

                val newRequest = XizumBattleRequest(e.whoClicked as Player, mode = createModeInstance(mode))

                m.queue.add(newRequest)

                e.whoClicked.sendMessage(textComponent {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.GREEN)
                    append("Você saiu da fila §b${XizumBattleMode.prettify(request.mode.enum)} §ae entrou na fila §b${XizumBattleMode.prettify(mode)}§a!")
                })
            }
        } else {
            val newRequest = XizumBattleRequest(e.whoClicked as Player, mode = createModeInstance(mode))

            m.queue.add(newRequest)

            e.whoClicked.sendMessage(textComponent {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Você entrou na fila §b${XizumBattleMode.prettify(mode)}§a!")
            })
        }

        e.whoClicked.closeInventory()
    }
}