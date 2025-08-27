package dev.connectaword.server

import kotlin.math.pow

// Помоћна класа за лакше баратање подацима
data class PlayerStats(
    val userId: String,
    val score: Int,
    val oldRating: Int,
    val gamesPlayed: Int
)

object EloService {

    private const val K_FACTOR_NEWBIE = 40.0 // За играче са < 30 одиграних партија
    private const val K_FACTOR_REGULAR = 20.0 // За искусне играче
    private const val ELO_CONSTANT = 400.0

    /**
     * Главна функција која рачуна нове рејтинге за све играче након партије.
     * @param playerStats Листа играча са њиховим резултатима и старим рејтинзима.
     * @return Мапа која повезује ID играча са његовим новим рејтингом.
     */
    fun calculateNewRatings(playerStats: List<PlayerStats>): Map<String, Int> {
        val newRatings = mutableMapOf<String, Int>()

        for (playerA in playerStats) {
            var totalRatingChange = 0.0

            for (playerB in playerStats) {
                if (playerA.userId == playerB.userId) continue // Не поредимо играча са самим собом

                // Очекивани исход меча А против Б
                val expectedOutcomeA = 1.0 / (1.0 + 10.0.pow((playerB.oldRating - playerA.oldRating) / ELO_CONSTANT))

                // Стварни исход меча на основу поена
                val actualOutcomeA = if (playerA.score + playerB.score == 0) {
                    0.5 // Нерешено ако су обојица имали 0 поена
                } else {
                    playerA.score.toDouble() / (playerA.score + playerB.score)
                }

                // Динамички К-фактор
                val kFactor = if (playerA.gamesPlayed < 30) K_FACTOR_NEWBIE else K_FACTOR_REGULAR

                // Промена рејтинга из овог једног "меча"
                val ratingChange = kFactor * (actualOutcomeA - expectedOutcomeA)
                totalRatingChange += ratingChange
            }

            // Нови рејтинг је стари + просечна промена из свих мечева
            val averageChange = if (playerStats.size > 1) totalRatingChange / (playerStats.size - 1) else 0.0
            newRatings[playerA.userId] = (playerA.oldRating + averageChange).toInt()
        }
        return newRatings
    }
}