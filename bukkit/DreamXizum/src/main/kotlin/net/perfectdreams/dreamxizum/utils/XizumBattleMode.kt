package net.perfectdreams.dreamxizum.utils

import kotlinx.serialization.Serializable

@Serializable
enum class XizumBattleMode {
    STANDARD,
    PVP_WITH_SOUP,
    PVP_WITH_POTION,
    COMPETITIVE,
    CUSTOM;

    companion object {
        fun prettify(mode: XizumBattleMode?): String? {
            return when (mode) {
                STANDARD -> "Padrão"
                PVP_WITH_SOUP -> "PvP com Sopa"
                PVP_WITH_POTION -> "PvP com Poção"
                COMPETITIVE -> "Competitivo (Ranqueado)"
                CUSTOM -> "Personalizado"
                else -> null
            }
        }

        fun modeById(id: Int?): XizumBattleMode? {
            return when (id) {
                0 -> STANDARD
                1 -> PVP_WITH_SOUP
                2 -> PVP_WITH_POTION
                3 -> COMPETITIVE
                4 -> CUSTOM
                else -> null
            }
        }
    }
}