package net.perfectdreams.dreamsinuca.tables

import net.perfectdreams.exposedpowerutils.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.dao.id.LongIdTable

object EightBallPoolGamesResults : LongIdTable() {
    val startedAt = timestampWithTimeZone("started_at")
    val endedAt = timestampWithTimeZone("ended_at")
    val winner = uuid("winner").index()
    val loser = uuid("loser").index()
    // TOOD: location maybe?
}