package net.perfectdreams.dreamsinuca.tables

import net.perfectdreams.dreamsinuca.sinuca.FinishReason
import net.perfectdreams.exposedpowerutils.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.dao.id.LongIdTable

object EightBallPoolGameMatches : LongIdTable() {
    val startedAt = timestampWithTimeZone("started_at")
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    val finishReason = enumerationByName<FinishReason>("finish_reason", 64).nullable().index()

    val player1 = uuid("player1").index()
    val player2 = uuid("player2").index()
    val winner = uuid("winner").nullable().index()
    val loser = uuid("loser").nullable().index()

    val world = text("world")
    val x = double("x")
    val y = double("y")
    val z = double("z")
}