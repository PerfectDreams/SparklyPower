package net.perfectdreams.dreamxizum.tables.dao

import net.perfectdreams.dreamxizum.tables.XizumProfiles
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class XizumProfile(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<XizumProfile>(XizumProfiles) {
        fun findOrCreate(uuid: UUID): XizumProfile {
            return find { XizumProfiles.id eq uuid }.firstOrNull() ?: new(uuid) {}
        }
    }

    var rating by XizumProfiles.rating
    var canDropHead by XizumProfiles.canDropHead
}