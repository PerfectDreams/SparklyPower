package net.perfectdreams.dreamxizum.tables

import net.perfectdreams.exposedpowerutils.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.dao.id.LongIdTable

object XizumMatchesResults : LongIdTable() {
    val arenaName = text("arena_name").index()
    val arenaMode = text("arena_mode").index()
    val winner = uuid("winner").index()
    val loser = uuid("loser").index()
    val finishedAt = timestampWithTimeZone("finished_at")
}