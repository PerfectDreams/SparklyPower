package net.perfectdreams.dreamreflections.tables

import net.perfectdreams.dreamcore.utils.exposed.jsonb
import net.perfectdreams.exposedpowerutils.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.dao.id.LongIdTable

object PlayerViolations : LongIdTable() {
    val player = uuid("player_id").index()
    val triggeredAt = timestampWithTimeZone("triggered_at").index()

    val metadata = jsonb("metadata").nullable()
}