package dev.connectaword.data

import kotlinx.serialization.Serializable

sealed interface GameMessage
data class StartGame(val action: String = "start") : GameMessage
data class MakeGuess(val guess: String, val action: String = "guess") : GameMessage
data class SurrenderRound(val action: String = "surrender") : GameMessage
data class PlayAgain(val action: String = "play_again") : GameMessage
data class GameStateUpdate(val gameState: GameState) : GameMessage
data class Announcement(val message: String) : GameMessage

// Помоћна класа за лакше парсирање на серверу
@Serializable
data class BaseAction(val action: String)

@Serializable
data class PlayerProgress(
    val pattern: String,
    val previousGuesses: List<String>,
    val commonLetters: Set<Char>,
    val remainingAttempts: Int,
    val isWordFinished: Boolean
)

@Serializable
data class PlayerData(
    val id: String,
    val username: String,
    val rating: Int,
    val totalScore: Int,
    val currentWordIndex: Int,
    val progress: PlayerProgress?,
    val isGameFinished: Boolean
)

@Serializable
data class GameState(
    val gameStatus: String,
    val players: List<PlayerData>,
    val hostId: String,
    val finalWords: List<String>? = null
)