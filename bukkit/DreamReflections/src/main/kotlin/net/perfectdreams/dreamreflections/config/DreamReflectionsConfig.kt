package net.perfectdreams.dreamreflections.config

import kotlinx.serialization.Serializable

@Serializable
data class DreamReflectionsConfig(
    val webhookUrl: String?
)