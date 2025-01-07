package net.perfectdreams.dreamxizum.modes.vanilla

import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.modes.AbstractXizumBattleMode
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class CustomXizumMode(var playerItems: Array<ItemStack?>, var opponentItems: Array<ItemStack?>, m: DreamXizum) : AbstractXizumBattleMode(XizumBattleMode.CUSTOM, m) {
    override val duration = 180

    override fun setupInventory(players: Pair<Player, Player>) {
        players.first.inventory.clear()
        players.second.inventory.clear()

        players.first.inventory.contents = playerItems
        players.second.inventory.contents = opponentItems
    }

    override fun addAfterCountdown(players: Pair<Player, Player>) {
        // don't need to do anything here
    }
}