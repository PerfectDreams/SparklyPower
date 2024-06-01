package net.perfectdreams.dreamraspadinha.commands

import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import net.perfectdreams.commands.annotation.Subcommand
import net.perfectdreams.commands.bukkit.SparklyCommand
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamraspadinha.DreamRaspadinha
import net.perfectdreams.dreamcore.utils.DreamUtils.random
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamraspadinha.tables.Raspadinhas
import net.perfectdreams.dreamraspadinha.utils.RaspadinhaHolder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction

class ScratchCardCommand(val m: DreamRaspadinha) : SparklyCommand(arrayOf("raspadinha")) {
    companion object {
        const val LORITTA_COMBO = (100_000 * 3)
        const val PANTUFA_COMBO = (10_000 * 3)
        const val GABI_COMBO = (1_000 * 3)
        const val DOKYO_COMBO = (375 * 3)
        const val GESSY_COMBO = (250  * 3)
        const val TOBIAS_COMBO = (130 * 3)
        val RASPINHA_CHAR_KEY = SparklyNamespacedKey("raspadinha_char", PersistentDataType.STRING)
    }

    @Subcommand
    fun raspadinhaInfo(player: Player) {
        player.sendMessage("${DreamRaspadinha.PREFIX} §a§lRaspadinha da Loritta")
        player.sendMessage("${DreamRaspadinha.PREFIX} §eGanhe prêmios comprando um ticket para raspar!")
        player.sendMessage("${DreamRaspadinha.PREFIX} §eAo comprar, raspe o ticket e veja o que você pode ganhar!")
        player.sendMessage("${DreamRaspadinha.PREFIX} §eSe tiver alguma combinação na horizontal, vertical ou na diagonal, você pode ganhar prêmios!")
        player.sendMessage("${DreamRaspadinha.PREFIX} §d§lCombinação de Loritta:§e ${LORITTA_COMBO} sonecas")
        player.sendMessage("${DreamRaspadinha.PREFIX} §d§lCombinação de Pantufa:§e ${PANTUFA_COMBO} sonecas")
        player.sendMessage("${DreamRaspadinha.PREFIX} §d§lCombinação de Gabriela:§e ${GABI_COMBO} sonecas")
        player.sendMessage("${DreamRaspadinha.PREFIX} §d§lCombinação de Dokyo:§e ${DOKYO_COMBO} sonecas")
        player.sendMessage("${DreamRaspadinha.PREFIX} §d§lCombinação de Gessy:§e ${GESSY_COMBO} sonecas")
        player.sendMessage("${DreamRaspadinha.PREFIX} §d§lCombinação de Tobias:§e ${TOBIAS_COMBO} sonecas")
        player.sendMessage("${DreamRaspadinha.PREFIX} §aCompre uma raspadinha por 375 sonecas usando §6/raspadinha comprar§a!")
    }

    @Subcommand(["comprar"])
    fun buyRaspadinha(player: Player) {
        if ((125 * 3) > player.balance) {
            player.sendMessage("${DreamRaspadinha.PREFIX} §cVocê precisa de 375 sonecas para comprar uma raspadinha!")
            return
        }

        player.withdraw(375.00, TransactionContext(type = TransactionType.BETTING, extra = "em uma raspadinha"))

        val array = Array(3) { Array<Char>(3, init = { 'Z' }) }

        for (x in 0 until 3) {
            for (y in 0 until 3) {
                val randomNumber = random.nextInt(1, 101)

                when (randomNumber) {
                    100 -> { // 1
                        array[x][y] = 'L'
                    }
                    in 94..99 -> { // 3
                        array[x][y] = 'P'
                    }
                    in 78..93 -> { // 6
                        array[x][y] = 'B'
                    }
                    in 59..77 -> { // 20
                        array[x][y] = 'D'
                    }
                    in 34..58 -> { // 25
                        array[x][y] = 'G'
                    }
                    in 0..33 -> { // 25
                        array[x][y] = 'T'
                    }
                }
            }
        }

        fun transformToItemStack(char: Char): ItemStack {
            return ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                .rename("§7§oClique para raspar!")
                .apply {
                    this.addUnsafeEnchantment(Enchantment.INFINITY, 1)
                }
                .meta<ItemMeta> {
                    this.persistentDataContainer.set(RASPINHA_CHAR_KEY, char.toString())
                    this.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                }
        }

        scheduler().schedule(m, SynchronizationContext.ASYNC) {
            val id = transaction(Databases.databaseNetwork) {
                Raspadinhas.insertAndGetId {
                    it[receivedById] = player.uniqueId
                    it[receivedAt] = System.currentTimeMillis()
                    it[pattern] = array.joinToString(
                        "\n",
                        transform = {
                            it.joinToString("")
                        })
                    it[scratched] = false
                }
            }

            switchContext(SynchronizationContext.SYNC)

            val inventory = Bukkit.createInventory(RaspadinhaHolder(id.value), 27)

            // menu overlay
            inventory.setItem(
                21,
                ItemStack(Material.WOODEN_HOE)
                    .rename("§f")
                    .meta<Damageable> {
                        this.damage = 3
                    }
                    .meta<ItemMeta> {
                        this.isUnbreakable = true
                        this.addItemFlags(
                            ItemFlag.HIDE_ATTRIBUTES
                        )
                    }
            )

            for (x in 0 until 3) {
                for (y in 0 until 3) {
                    inventory.setItem(
                        x + (y * 9),
                        transformToItemStack(array[x][y])
                    )
                }
            }

            player.openInventory(inventory)
        }
    }
}