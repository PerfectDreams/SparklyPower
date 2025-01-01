package net.perfectdreams.dreamemotes.gestures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.Material
import org.bukkit.entity.Display.Billboard

@Serializable
sealed class PropMapper {
    @Serializable
    @SerialName("item_display")
    data class ItemDisplay(
        val item: PropItem,
        val scaleX: Float,
        val scaleY: Float,
        val scaleZ: Float,
        val offsetYType: OffsetType,
        val brightness: Brightness? = null
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

        enum class OffsetType {
            BOTTOM,
            CENTER,
            TOP
        }
    }

    @Serializable
    @SerialName("text_display")
    data class TextDisplay(
        val text: String,
        val scaleX: Float,
        val scaleY: Float,
        val scaleZ: Float,
        val billboard: Billboard,
        val backgroundColor: Int = 1073741824,
        val brightness: Brightness? = null
    ) : PropMapper()

    @Serializable
    data class Brightness(
        val blockLight: Int,
        val skyLight: Int
    )
}