package net.perfectdreams.dreamxizum.modes.vanilla

import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.modes.AbstractXizumBattleMode
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class CompetitiveXizumMode(m: DreamXizum) : AbstractXizumBattleMode(XizumBattleMode.COMPETITIVE, m) {
    override val duration = 180

    override fun setupInventory(players: Pair<Player, Player>) {
        players.toList().forEach {
            it.inventory.clear()
            it.inventory.armorContents = arrayOf(
                ItemStack(Material.IRON_BOOTS),
                ItemStack(Material.IRON_LEGGINGS),
                ItemStack(Material.IRON_CHESTPLATE),
                ItemStack(Material.IRON_HELMET)
            )

            it.inventory.setItemInOffHand(ItemStack(Material.SHIELD))

            it.inventory.addItem(ItemStack(Material.IRON_SWORD))
            it.inventory.addItem(ItemStack(Material.BOW))
            it.inventory.addItem(ItemStack(Material.GOLDEN_APPLE, 8))
        }
    }

    override fun addAfterCountdown(players: Pair<Player, Player>) {
        players.toList().forEach {
            it.inventory.addItem(ItemStack(Material.ARROW, 16))
        }
    }
}