package net.perfectdreams.dreamreflections.sessions

import kotlinx.serialization.Serializable

// TODO: Implement this!
@Serializable
data class ReflectionSessionData(
    val minecraftVersion: String,
    val isBedrockClient: Boolean
)