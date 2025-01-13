package net.perfectdreams.dreamsinuca.sinuca

import org.bukkit.Material
import org.bukkit.entity.ItemDisplay

/**
 * Stores a reference to a pool table
 */
class PoolTable(val poolTableEntity: ItemDisplay) {
    // TODO: Add a display entity and stuff
    //  How can we hook up the SparklySinuca into the PoolTable code?
    val barrierBlocks = mutableSetOf<Long>()

    var activeSinuca: SparklySinuca? = null

    fun configure() {
        val spawnLocation = poolTableEntity.location

        for (z in -1..1) {
            for (x in -2..2) {

                // TODO: Store barrier blocks!
            }
        }
    }
}