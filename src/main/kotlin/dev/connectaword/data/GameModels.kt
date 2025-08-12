package dev.connectaword.data

import kotlinx.serialization.Serializable

@Serializable
sealed interface GameMessage

@Serializable
data class MakeGuess(val guess: String) : GameMessage

@Serializable
data class StartGame(val action: String = "start") : GameMessage

@Serializable
data class GameStateUpdate(val gameState: GameState) : GameMessage

@Serializable
data class Announcement(val message: String) : GameMessage

@Serializable
data class GameState(
    val wordToGuess: String,
    val pattern: String,
    val remainingGuesses: Int,
    val players: List<PlayerData>,
    val isGameOver: Boolean = false,
    val hostId: String = "", // Додато
    val status: String = "WAITING" // Додато (може бити "WAITING", "IN_PROGRESS", "FINISHED")
)

@Serializable
data class PlayerData(
    val id: String,
    val username: String,
    val score: Int
)