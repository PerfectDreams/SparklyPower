package net.perfectdreams.dreamemotes.gestures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GestureAction(
    val name: String,
    val blockbenchAnimation: String,
    val onFinish: OnFinishAction,
    val loopCount: Int = 1,
    val onTick: List<TickActions> = listOf(),
) {
    @Serializable
    sealed class OnFinishAction {
        @Serializable
        @SerialName("jump")
        data class JumpToAction(val name: String) : OnFinishAction()

        @Serializable
        @SerialName("hold")
        data object HoldAction : OnFinishAction()

        @Serializable
        @SerialName("stop")
        data object StopGestureAction : OnFinishAction()
    }

    @Serializable
    data class TickActions(
        val tick: Int,
        val actions: List<TickAction>
    )

    @Serializable
    sealed class TickAction {
        @Serializable
        @SerialName("play_sound")
        data class PlaySound(val soundKey: String, val volume: Float = 1f, val pitch: Float = 1f) : TickAction()

        @Serializable
        @SerialName("text_display_text")
        data class TextDisplayText(val elementName: String, val text: String) : TickAction()
    }
}