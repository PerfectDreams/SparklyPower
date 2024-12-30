package net.perfectdreams.dreamemotes.gestures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.Material

@Serializable
sealed class PropMapper {
    @Serializable
    @SerialName("item_display")
    data class ItemDisplay(
        val item: PropItem,
        val scaleX: Float,
        val scaleY: Float,
        val scaleZ: Float,
    ) : PropMapper() {
        @Serializable
        data class PropItem(
            val material: Material,
            val itemModel: String,
            val playerProfileSkin: PlayerProfile? = null
        ) {
            @Serializable
            data class PlayerProfile(
                val value: String,
                val signature: String? = null
            )
        }
    }
}