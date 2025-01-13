package net.perfectdreams.dreamsinuca.listeners

import net.perfectdreams.dreamcore.utils.extensions.leftClick
import net.perfectdreams.dreamcore.utils.get
import net.perfectdreams.dreamsinuca.DreamSinuca
import net.perfectdreams.dreamsinuca.DreamSinuca.Companion.POOL_TABLE_ENTITY
import net.perfectdreams.dreamsinuca.sinuca.PoolTable
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.event.world.EntitiesUnloadEvent

class SinucaEntityListener(val m: DreamSinuca) : Listener {
    @EventHandler
    fun onEntityLoad(e: EntitiesLoadEvent) {
        for (entity in e.entities) {
            if (entity is ItemDisplay) {
                val isSinucaEntity = entity.persistentDataContainer.get(POOL_TABLE_ENTITY)

                if (isSinucaEntity) {
                    val poolTable = PoolTable(entity)
                    poolTable.configure()
                    m.poolTables[entity] = poolTable
                }
            }
        }
    }

    @EventHandler
    fun onEntityUnload(e: EntitiesUnloadEvent) {
        for (entity in e.entities) {
            if (entity is ItemDisplay) {
                val isSinucaEntity = entity.persistentDataContainer.get(POOL_TABLE_ENTITY)

                if (isSinucaEntity) {
                    val poolTable = m.poolTables.remove(entity)
                    // The pool table should be torn down when unloaded, and any active sinucas should be cancelled
                }
            }
        }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        if (!e.leftClick)
            return

        val block = e.clickedBlock ?: return
    }
}