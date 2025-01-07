package net.perfectdreams.dreamxizum.modes

import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import org.bukkit.entity.Player

abstract class AbstractXizumBattleMode(
    val enum: XizumBattleMode,
    val m: DreamXizum
) {
    abstract val duration: Int

    abstract fun setupInventory(players: Pair<Player, Player>)

    abstract fun addAfterCountdown(players: Pair<Player, Player>)
}