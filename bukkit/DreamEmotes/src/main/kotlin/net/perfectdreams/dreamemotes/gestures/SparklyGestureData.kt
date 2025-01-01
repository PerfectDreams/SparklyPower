package net.perfectdreams.dreamemotes.gestures

import kotlinx.serialization.Serializable

@Serializable
data class SparklyGestureData(
    val name: String,
    val favoriteGesturesCharacter: String,
    val blockbenchModel: String,
    val actions: List<GestureAction>,
    val props: Map<String, PropMapper>
)