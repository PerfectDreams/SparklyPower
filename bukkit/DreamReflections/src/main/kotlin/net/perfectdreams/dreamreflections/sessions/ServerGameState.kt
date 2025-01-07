package net.perfectdreams.dreamreflections.sessions

import org.bukkit.util.Vector

/**
 * Stores data about the server game state
 */
class ServerGameState {
    /**
     * Does the server think they are on ground?
     */
    var isOnGround = false

    var fallDistance = 0f

    var velocity = Vector(0f, 0f, 0f)
}