package net.perfectdreams.dreamxizum.tables

import org.jetbrains.exposed.dao.id.IdTable
import java.util.UUID

object XizumProfiles : IdTable<UUID>() {
    override val id = uuid("player_id").entityId()
    override val primaryKey = PrimaryKey(id)

    val wins = integer("wins").default(0)
    val losses = integer("losses").default(0)
    val rating = integer("rating").default(0)
    val canDropHead = bool("can_drop_head").default(true)
}