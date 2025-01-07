package net.perfectdreams.dreamreflections.sessions

/**
 * Stores data about the player game state
 */
class ClientGameState {
    /**
     * If the server is awaiting a teleport confirmation from the client
     */
    var awaitingTeleportConfirmation = false

    /**
     * Does the client think they are on ground?
     */
    var isOnGround = false
}