package net.perfectdreams.dreammapwatermarker.tables

import net.perfectdreams.dreamcore.utils.exposed.jsonb
import net.perfectdreams.exposedpowerutils.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.dao.id.LongIdTable

object PlayerPantufaPrintShopCustomMaps : LongIdTable() {
    val requestedBy = uuid("requested_by")
    val mapImagesCount = integer("map_images_count")
    val mapImages = text("map_images")
    val requestedAt = timestampWithTimeZone("requested_at")
    val approvedAt = timestampWithTimeZone("approved_at").nullable()
    val approvedBy = uuid("approved_by_id").nullable()
    val mapIds = jsonb("map_ids").nullable()
    val copies = integer("copies").nullable()
}