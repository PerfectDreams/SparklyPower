package net.perfectdreams.dreamxizum.utils.config

import kotlinx.serialization.Serializable
import net.perfectdreams.dreamxizum.structures.XizumArena
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import org.bukkit.Bukkit
import org.bukkit.Location

@Serializable
data class XizumPluginConfig(
    var spectatorPos: Location? = null,
    var arenas: MutableList<XizumArenaConfig>
) {
    @Serializable
    data class XizumArenaConfig(
        val arenaName: String,
        val worldName: String,
        var playerPos: Location? = null,
        var opponentPos: Location? = null,
        var mode: XizumBattleMode? = null
    )

    @Serializable
    data class Location(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
    ) {
        fun toBukkitLocation(world: String) = Location(
            Bukkit.getWorld(world),
            x,
            y,
            z,
            yaw,
            pitch
        )
    }
}