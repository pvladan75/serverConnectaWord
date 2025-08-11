package dev.connectaword.data

import kotlinx.serialization.Serializable

// --- DODAJTE/AŽURIRAJTE SADRŽAJ OVOG FAJLA ---

@Serializable
data class GameState(
    val wordToGuess: String,
    val pattern: String,
    val remainingGuesses: Int,
    val players: List<PlayerData>,
    val isGameOver: Boolean = false
)

@Serializable
data class PlayerData(
    val id: String,
    val username: String,
    val score: Int
)

@Serializable
data class MakeGuess(
    val guess: String
)

@Serializable
data class BroadcastMessage(
    val message: String,
    val timestamp: Long
)