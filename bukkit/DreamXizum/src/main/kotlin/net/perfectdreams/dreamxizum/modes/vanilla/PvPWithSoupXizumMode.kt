package net.perfectdreams.dreamxizum.modes.vanilla

import net.perfectdreams.dreamcore.utils.extensions.rightClick
import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.modes.AbstractXizumBattleMode
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

class PvPWithSoupXizumMode(m: DreamXizum) : AbstractXizumBattleMode(XizumBattleMode.PVP_WITH_SOUP, m), Listener {
    override val duration = 180

    override fun setupInventory(players: Pair<Player, Player>) {
        for (player in players.toList()) {
            player.inventory.clear()
            player.inventory.armorContents = arrayOf(
                ItemStack(Material.IRON_BOOTS),
                null,
                ItemStack(Material.IRON_CHESTPLATE),
                null
            )

            player.inventory.setItem(0, ItemStack(Material.STONE_SWORD))

            for (i in 1..35) {
                player.inventory.addItem(ItemStack(Material.MUSHROOM_STEW))
            }

            player.inventory.setItem(14, ItemStack(Material.BOWL, 64))
            player.inventory.setItem(15, ItemStack(Material.RED_MUSHROOM, 64))
            player.inventory.setItem(16, ItemStack(Material.BROWN_MUSHROOM, 64))
        }
    }

    override fun addAfterCountdown(players: Pair<Player, Player>) {
        // don't need to do anything here
    }

    @EventHandler
    fun onSoupInteract(e: PlayerInteractEvent) {
        if (!m.activeBattles.any { it.player == e.player || it.opponent == e.player })
            return

        val battle = m.activeBattles.first { it.player == e.player || it.opponent == e.player }

        if (battle.started && battle.mode is PvPWithSoupXizumMode) {
            e.isCancelled = true

            if (e.item == null)
                return

            if (e.item!!.type == Material.MUSHROOM_STEW && e.rightClick) {
                val player = e.player

                if (player.health + 6.5 > player.maxHealth)
                    player.health = player.maxHealth
                else
                    player.health += 6.5

                if (player.foodLevel + 6 > 20)
                    player.foodLevel = 20
                else
                    player.foodLevel += 6

                e.item!!.amount = 0
                e.player.inventory.setItem(e.player.inventory.heldItemSlot, ItemStack(Material.BOWL, 1))
            }
        }
    }
}