package dev.connectaword.server

import dev.connectaword.data.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Game(
    private val roomId: String,
    private val roomLanguage: String,
    val hostId: String
) {

    val connections = mutableSetOf<GameConnection>()

    private val _gameState = MutableStateFlow(GameState(
        wordToGuess = "",
        pattern = "",
        remainingGuesses = 10,
        players = emptyList(),
        hostId = this.hostId, // Попуњавамо hostId
        status = "WAITING" // Почетни статус
    ))

    fun startGame() {
        val newWord = WordRepository.getRandomWord(roomLanguage)

        if (newWord == null) {
            println("ERROR: Could not get a word for language '$roomLanguage'. Game cannot start.")
            return
        }

        _gameState.update {
            it.copy(
                wordToGuess = newWord,
                pattern = "_ ".repeat(newWord.length).trim(),
                remainingGuesses = 10,
                isGameOver = false,
                players = it.players,
                status = "IN_PROGRESS" // Мењамо статус
            )
        }
    }

    fun addPlayer(connection: GameConnection) {
        connections.add(connection)
        _gameState.update { state ->
            val newPlayer = PlayerData(connection.userId, connection.username, 0)
            state.copy(players = state.players + newPlayer)
        }
    }

    fun removePlayerBySession(session: WebSocketSession) {
        val playerToRemove = connections.find { it.session == session }
        if (playerToRemove != null) {
            connections.remove(playerToRemove)
            _gameState.update { state ->
                state.copy(players = state.players.filter { it.id != playerToRemove.userId })
            }
        }
    }

    fun isEmpty(): Boolean {
        return connections.isEmpty()
    }

    suspend fun processGuess(fromPlayer: GameConnection, guessAction: MakeGuess) {
        val guess = guessAction.guess.uppercase()
        val currentState = _gameState.value

        if (currentState.isGameOver || currentState.status != "IN_PROGRESS") return

        if (guess.length != currentState.wordToGuess.length) {
            val errorMessage = Announcement("Guess must have ${currentState.wordToGuess.length} letters.")
            val errorJson = Json.encodeToString(errorMessage as GameMessage)
            fromPlayer.session.send(Frame.Text(errorJson))
            return
        }

        val newPattern = StringBuilder(currentState.pattern.replace(" ", ""))
        var correctGuess = false

        for (i in currentState.wordToGuess.indices) {
            if (currentState.wordToGuess[i] == guess[i]) {
                newPattern[i] = guess[i]
                correctGuess = true
            }
        }

        _gameState.update {
            it.copy(
                pattern = newPattern.toString().chunked(1).joinToString(" "),
                remainingGuesses = if (!correctGuess) it.remainingGuesses - 1 else it.remainingGuesses
            )
        }

        val updatedState = _gameState.value
        if (updatedState.pattern.replace(" ", "") == updatedState.wordToGuess || updatedState.remainingGuesses <= 0) {
            _gameState.update { it.copy(isGameOver = true, status = "FINISHED") }
        }

        broadcastState()
    }

    suspend fun broadcastState() {
        val clientState = if (_gameState.value.isGameOver) {
            _gameState.value
        } else {
            _gameState.value.copy(wordToGuess = "")
        }

        val stateUpdateMessage = GameStateUpdate(clientState)
        val stateJson = Json.encodeToString(stateUpdateMessage as GameMessage)

        connections.forEach {
            it.session.send(Frame.Text(stateJson))
        }
    }
}