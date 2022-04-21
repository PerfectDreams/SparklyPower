package net.perfectdreams.dreamnetworkbans.utils

import net.md_5.bungee.api.ChatColor
import java.awt.Color

enum class StaffColors(val prefixes: List<Char>, val colors: Colors, val permission: String) {
    OWNER(
        listOf('\ue245'),
        Triple(0x4EED4E, 0x8CEA8C, 0x6EE56E).asColors,
        "dreamnetworkbans.owner"
    ),

    ADMIN(
        listOf('\ue244'),
        Triple(0xFF6B6B, 0xFF9999, 0xFF7A7A).asColors,
        "dreamnetworkbans.admin"
    ),

    COORDINATOR(
        listOf('\ue242', '\ue243'),
        Triple(0xCD6BFF, 0xE2ADFF, 0xD182FF).asColors,
        "dreamnetworkbans.coord"
    ),

    MODERATOR(
        listOf('\ue240', '\ue241'),
        Triple(0x34B2F3, 0x94D2EF, 0x54BBEA).asColors,
        "dreamnetworkbans.moderator"
    ),

    BUILDER(
        listOf('\ue23e', '\ue23f'),
        Triple(0xFF5EE1, 0xFFA8EC, 0xFF77E1).asColors,
        "dreamnetworkbans.builder"
    ),

    SUPPORT(
        listOf('\ue23d'),
        Triple(0xFFAA00, 0xFBD377, 0xF9C24A).asColors,
        "dreamnetworkbans.support"
    )
}

data class Colors(private val options: Triple<Int, Int, Int>) {
    val nick = options.first.asColor
    val chat = options.second.asColor

    fun mention(text: String) = "${options.third.asColor}$text$chat"
}

private val Triple<Int, Int, Int>.asColors get() = Colors(this)
private val Int.asColor get() = ChatColor.of(Color(this))