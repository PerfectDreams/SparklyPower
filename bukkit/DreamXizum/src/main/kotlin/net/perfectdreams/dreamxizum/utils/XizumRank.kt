package net.perfectdreams.dreamxizum.utils

enum class XizumRank(val rating: Int, val text: String) {
    UNRANKED(0, "§7§lSem elo"),
    STARTER(100, "§c§lIniciante"),
    ASPIRANT(400, "§e§lAspirante"),
    COMBATANT(700, "§6§lCombatente"),
    EXTRAORDINARY(1000, "§5§lExtraordinário"),
    SAVAGE(1300, "§a§lSelvagem"),
    EXTERMINATOR(1600, "§2§lExterminador"),
    LEGENDARY(1900, "§d§lLendário");

    companion object {
        fun getTextByRating(rating: Int): String {
            return entries
                .sortedByDescending { it.rating }
                .firstOrNull { rating >= it.rating }?.text
                ?: UNRANKED.text
        }
    }
}