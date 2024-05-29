package net.perfectdreams.dreamcore.dao

import net.perfectdreams.dreamcore.tables.DiscordAccounts
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass

class DiscordAccount(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<DiscordAccount>(DiscordAccounts)

    var minecraftId by DiscordAccounts.minecraftId
    var discordId by DiscordAccounts.discordId
    var isConnected by DiscordAccounts.isConnected
}