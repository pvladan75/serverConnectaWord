package dev.connectaword.server

import dev.connectaword.data.GameState
import dev.connectaword.data.MakeGuess
import dev.connectaword.data.PlayerData
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Game(private val roomId: String) {

    // A simple list of words for now. Later, this will come from the database.
    private val words = listOf("KTOR", "KOTLIN", "SERVER", "ANDROID", "COMPOSE")
    val connections = mutableSetOf<GameConnection>()

    private val _gameState = MutableStateFlow(
        GameState(
            wordToGuess = words.random(),
            pattern = "", // Will be initialized when the game starts
            remainingGuesses = 10,
            players = emptyList()
        )
    )

    init {
        // Initialize the pattern for the first word
        _gameState.update {
            it.copy(pattern = "_ ".repeat(it.wordToGuess.length).trim())
        }
    }

    fun addPlayer(connection: GameConnection) {
        connections.add(connection)
        _gameState.update { state ->
            val newPlayer = PlayerData(connection.userId, connection.username, 0)
            state.copy(players = state.players + newPlayer)
        }
    }

    // This function now accepts the session to find the player to remove
    fun removePlayerBySession(session: WebSocketSession) {
        val playerToRemove = connections.find { it.session == session }
        if (playerToRemove != null) {
            connections.remove(playerToRemove)
            _gameState.update { state ->
                state.copy(players = state.players.filter { it.id != playerToRemove.userId })
            }
        }
    }

    // A helper to check if the game is empty
    fun isEmpty(): Boolean {
        return connections.isEmpty()
    }

    suspend fun processGuess(fromPlayer: GameConnection, guess: MakeGuess) {
        // TODO: Implement guess validation logic here

        // For now, just broadcast the guess as a simple message
        val message = "[${fromPlayer.username} guessed]: ${guess.guess}"
        connections.forEach {
            it.session.send(Frame.Text(message))
        }

        // After processing, you would update the game state and broadcast it
        // broadcastState()
    }

    suspend fun broadcastState() {
        // We will improve this to send a structured GameStateUpdate message later
        val stateJson = Json.encodeToString(_gameState.value)
        connections.forEach {
            // it.session.send(Frame.Text(stateJson))
            val playerNames = _gameState.value.players.joinToString(", ") { it.username }
            it.session.send(Frame.Text("Welcome! Players in room: $playerNames"))
        }
    }
}