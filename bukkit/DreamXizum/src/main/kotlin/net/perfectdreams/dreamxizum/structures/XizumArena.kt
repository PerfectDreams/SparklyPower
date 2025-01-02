package net.perfectdreams.dreamxizum.structures

import net.perfectdreams.dreamxizum.utils.config.XizumPluginConfig
import org.bukkit.Location

class XizumArena(
    var data: XizumPluginConfig.XizumArenaConfig,
    val playerPos: Location?,
    val opponentPos: Location?
) {
    var inUse: Boolean = false
}