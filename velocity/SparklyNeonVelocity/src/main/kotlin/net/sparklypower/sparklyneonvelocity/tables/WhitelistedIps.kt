package net.sparklypower.sparklyneonvelocity.tables

import org.jetbrains.exposed.dao.id.LongIdTable

object WhitelistedIps : LongIdTable() {
    val ip = text("ip").index()
}