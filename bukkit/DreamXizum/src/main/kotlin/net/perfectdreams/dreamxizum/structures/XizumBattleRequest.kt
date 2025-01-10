package net.perfectdreams.dreamxizum.structures

import net.perfectdreams.dreamxizum.modes.AbstractXizumBattleMode
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class XizumBattleRequest(
    val player: Player,
    val opponent: Player? = null,
    val mode: AbstractXizumBattleMode,
    val time: Long = System.currentTimeMillis(),
    var processed: Boolean
)