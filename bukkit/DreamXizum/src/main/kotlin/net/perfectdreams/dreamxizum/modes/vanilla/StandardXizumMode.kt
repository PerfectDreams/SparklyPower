package net.perfectdreams.dreamxizum.modes.vanilla

import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.modes.AbstractXizumBattleMode
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import org.bukkit.entity.Player

class StandardXizumMode(m: DreamXizum) : AbstractXizumBattleMode(XizumBattleMode.STANDARD, m) {
    override fun setupInventory(players: Pair<Player, Player>) {
        // don't need to do anything here
    }

    override fun addAfterCountdown(players: Pair<Player, Player>) {
        // don't need to do anything here
    }
}