package dev.connectaword.server

import com.google.gson.Gson
import dev.connectaword.data.*
import io.ktor.util.logging.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class Game(
    private val roomId: String,
    private val roomLanguage: String,
    val hostId: String
) {
    private val logger = KtorSimpleLogger("dev.connectaword.server.Game")
    private val userDao = UserDao()

    val connections = mutableSetOf<GameConnection>()
    private var gameWords: List<String> = emptyList()
    private var initialPatterns: List<String> = emptyList()

    private val serbianConsonantTokens = setOf("B", "C", "Č", "Ć", "D", "DŽ", "Đ", "F", "G", "H", "K", "L", "LJ", "M", "N", "NJ", "P", "R", "S", "Š", "T", "V", "Z", "Ž")
    private val englishConsonants = "BCDFGHKLMNPQRSTVWXYZ".toSet()

    private fun tokenizeSerbianWord(word: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < word.length) {
            if (i + 2 < word.length && word.substring(i, i + 3).equals("DŽ", ignoreCase = true)) {
                tokens.add(word.substring(i, i + 3).uppercase())
                i += 3
            }
            else if (i + 1 < word.length && (word.substring(i, i + 2).equals("NJ", ignoreCase = true) || word.substring(i, i + 2).equals("LJ", ignoreCase = true))) {
                tokens.add(word.substring(i, i + 2).uppercase())
                i += 2
            } else {
                tokens.add(word.substring(i, i + 1).uppercase())
                i += 1
            }
        }
        return tokens
    }


    private val _gameState = MutableStateFlow(
        GameState(
            gameStatus = "WAITING",
            players = emptyList(),
            hostId = this.hostId
        )
    )

    fun addPlayer(connection: GameConnection) {
        connections.removeAll { it.userId == connection.userId }
        connections.add(connection)

        _gameState.update { state ->
            if (state.players.any { it.id == connection.userId }) {
                return@update state
            }
            val newPlayer = PlayerData(
                id = connection.userId,
                username = connection.username,
                rating = connection.rating,
                totalScore = 0,
                currentWordIndex = 0,
                progress = null,
                isGameFinished = false
            )
            state.copy(players = state.players + newPlayer)
        }
    }

    fun removePlayerBySession(session: WebSocketSession) {
        val playerToRemove = connections.find { it.session == session }
        if (playerToRemove != null) {
            connections.remove(playerToRemove)
        }
    }

    fun isEmpty(): Boolean {
        return connections.isEmpty()
    }

    suspend fun startGame() {
        gameWords = WordRepository.getFiveRandomWords(roomLanguage)
        if (gameWords.size < 5) {
            logger.error("Not enough words to start game for language '$roomLanguage'.")
            return
        }

        initialPatterns = gameWords.map { generateInitialPattern(it) }

        _gameState.update { currentState ->
            val initialProgressData = currentState.players.map { player ->
                player.copy(
                    currentWordIndex = 0,
                    isGameFinished = false,
                    totalScore = 0,
                    progress = PlayerProgress(
                        pattern = initialPatterns[0],
                        previousGuesses = emptyList(),
                        commonLetters = emptySet(),
                        remainingAttempts = 10,
                        isWordFinished = false
                    )
                )
            }
            currentState.copy(
                gameStatus = "IN_PROGRESS",
                players = initialProgressData,
                finalWords = null
            )
        }
        broadcastState()
    }

    private suspend fun endGame() {
        _gameState.update { it.copy(gameStatus = "FINISHED", finalWords = gameWords) }
        logger.info("Game finished for room $roomId. Calculating Elo ratings...")

        val finalScores = _gameState.value.players
        if (finalScores.size < 2) {
            logger.info("Not enough players to calculate Elo. Skipping.")
            broadcastState()
            return
        }

        val userIds = finalScores.map { it.id }
        val userStatsFromDb = userDao.getStatsForUsers(userIds)

        val playerStatsForElo = finalScores.mapNotNull { playerData ->
            userStatsFromDb.find { it.id == playerData.id }?.let { stats ->
                PlayerStats(
                    userId = playerData.id,
                    score = playerData.totalScore,
                    oldRating = stats.rating,
                    gamesPlayed = stats.gamesPlayed
                )
            }
        }

        val newRatings = EloService.calculateNewRatings(playerStatsForElo)
        logger.info("New ratings calculated: $newRatings")

        userDao.updateUserRatingsAndGamesPlayed(newRatings)
        logger.info("Database updated with new ratings and games played count.")

        broadcastState()
    }

    // --- NOVA, ROBUSTNIJA LOGIKA ZA OBRADU POGAĐANJA ---
    private fun processGuessInternal(
        guessTokens: List<String>,
        targetTokens: List<String>,
        currentPatternTokens: List<String>
    ): Pair<String, Set<Char>> {
        val newPatternTokens = currentPatternTokens.toMutableList()
        val commonLetters = mutableSetOf<Char>()

        val targetUsed = BooleanArray(targetTokens.size) { false }
        val guessUsed = BooleanArray(guessTokens.size) { false }

        // Prvi prolaz: tražimo tačna poklapanja (zelena slova)
        for (i in guessTokens.indices) {
            if (guessTokens[i] == targetTokens[i]) {
                newPatternTokens[i] = guessTokens[i]
                targetUsed[i] = true
                guessUsed[i] = true
            }
        }

        // Drugi prolaz: tražimo slova na pogrešnom mestu (žuta slova)
        for (i in guessTokens.indices) {
            if (guessUsed[i]) continue // Preskačemo već iskorišćena (zelena) slova iz pogađanja

            for (j in targetTokens.indices) {
                if (targetUsed[j]) continue // Preskačemo već iskorišćena (zelena) slova iz tražene reči

                if (guessTokens[i] == targetTokens[j]) {
                    commonLetters.add(guessTokens[i].first()) // Dodajemo samo prvi karakter za prikaz
                    targetUsed[j] = true // Označavamo da smo iskoristili ovo slovo iz tražene reči
                    break // Prelazimo na sledeće slovo iz pogađanja
                }
            }
        }
        return Pair(newPatternTokens.joinToString(" "), commonLetters)
    }

    suspend fun processGuess(fromPlayerId: String, guessAction: MakeGuess) {
        val playerIndex = _gameState.value.players.indexOfFirst { it.id == fromPlayerId }
        if (playerIndex == -1) return

        val playerData = _gameState.value.players[playerIndex]
        val playerProgress = playerData.progress
        if (playerProgress == null || playerProgress.isWordFinished) return

        val guess = guessAction.guess.uppercase()
        val wordToGuess = gameWords[playerData.currentWordIndex]
        val isSerbian = roomLanguage.lowercase() == "serbian"

        // --- Priprema tokena i provere ---
        val guessTokens = if (isSerbian) tokenizeSerbianWord(guess) else guess.map { it.toString() }
        val targetTokens = if (isSerbian) tokenizeSerbianWord(wordToGuess) else wordToGuess.map { it.toString() }
        val currentPatternTokens = if (isSerbian) playerProgress.pattern.split(" ") else playerProgress.pattern.map { it.toString() }

        // Provera #1: Da li je reč validna i odgovarajuće dužine
        if (guessTokens.size != targetTokens.size || !WordRepository.isValidGuess(guess, roomLanguage)) {
            val reason = if (guessTokens.size != targetTokens.size) "must have ${targetTokens.size} letters" else "is not a valid word"
            sendAnnouncementToPlayer(fromPlayerId, "Guess '${guess}' is invalid: $reason.")
            return
        }

        // --- NOVA PROVERA #2: Da li se pogađanje poklapa sa mustrom ---
        for (i in targetTokens.indices) {
            if (currentPatternTokens[i] != "_" && currentPatternTokens[i] != guessTokens[i]) {
                sendAnnouncementToPlayer(fromPlayerId, "Guess must match the revealed letters in the pattern.")
                return
            }
        }

        // --- Obrada pogađanja pomoću nove funkcije ---
        val (newPatternString, commonLetters) = processGuessInternal(guessTokens, targetTokens, currentPatternTokens)

        val updatedProgress = playerProgress.copy(
            pattern = newPatternString,
            previousGuesses = playerProgress.previousGuesses + guess,
            commonLetters = commonLetters,
            remainingAttempts = playerProgress.remainingAttempts - 1
        )

        var finalPlayerData = playerData.copy(progress = updatedProgress)

        val isWordGuessed = newPatternString.replace(" ", "") == wordToGuess
        if (isWordGuessed || updatedProgress.remainingAttempts <= 0) {
            val scoreForRound = if (isWordGuessed) 50 + updatedProgress.remainingAttempts else 0
            logger.info("Player $fromPlayerId finished round with score: $scoreForRound")
            finalPlayerData = advancePlayer(playerData, scoreForRound)
        }

        _gameState.update { state ->
            val newPlayersList = state.players.toMutableList().also { it[playerIndex] = finalPlayerData }
            state.copy(players = newPlayersList)
        }

        sendStateToPlayer(fromPlayerId)

        if (_gameState.value.players.all { it.isGameFinished }) {
            endGame()
        }
    }


    suspend fun processSurrender(fromPlayerId: String) {
        val playerIndex = _gameState.value.players.indexOfFirst { it.id == fromPlayerId }
        if (playerIndex == -1) return
        val playerData = _gameState.value.players[playerIndex]
        if (playerData.isGameFinished || playerData.progress == null || playerData.progress.isWordFinished) return

        logger.info("Player $fromPlayerId surrendered round ${playerData.currentWordIndex + 1}.")
        val finalPlayerData = advancePlayer(playerData, 0)

        _gameState.update { state ->
            val newPlayersList = state.players.toMutableList()
            newPlayersList[playerIndex] = finalPlayerData
            state.copy(players = newPlayersList)
        }

        sendStateToPlayer(fromPlayerId)

        if (_gameState.value.players.all { it.isGameFinished }) {
            endGame()
        }
    }

    private fun advancePlayer(playerData: PlayerData, scoreForRound: Int): PlayerData {
        val newTotalScore = playerData.totalScore + scoreForRound
        val nextWordIndex = playerData.currentWordIndex + 1

        return if (nextWordIndex < gameWords.size) {
            playerData.copy(
                totalScore = newTotalScore,
                currentWordIndex = nextWordIndex,
                progress = PlayerProgress(
                    pattern = initialPatterns[nextWordIndex],
                    previousGuesses = emptyList(),
                    commonLetters = emptySet(),
                    remainingAttempts = 10,
                    isWordFinished = false
                )
            )
        } else {
            playerData.copy(
                totalScore = newTotalScore,
                isGameFinished = true,
                progress = null
            )
        }
    }

    private fun generateInitialPattern(word: String): String {
        val isSerbian = roomLanguage.lowercase() == "serbian"
        if (!isSerbian) {
            val consonants = englishConsonants
            val wordConsonants = word.filter { it in consonants }
            if (wordConsonants.isEmpty()) return "_".repeat(word.length)
            val randomIndexToShow = word.mapIndexedNotNull { index, char -> if (char in consonants) index else null }.random()
            val pattern = StringBuilder("_".repeat(word.length))
            pattern[randomIndexToShow] = word[randomIndexToShow]
            return pattern.toString()
        } else {
            val tokens = tokenizeSerbianWord(word)
            val consonantTokens = tokens.mapIndexedNotNull { index, token ->
                if (token in serbianConsonantTokens) index else null
            }
            if (consonantTokens.isEmpty()) {
                return List(tokens.size) { "_" }.joinToString(" ")
            }
            val randomIndexToShow = consonantTokens.random()
            val patternTokens = tokens.mapIndexed { index, token ->
                if (index == randomIndexToShow) token else "_"
            }
            return patternTokens.joinToString(" ")
        }
    }


    suspend fun broadcastState() {
        val trueState = _gameState.value
        connections.forEach { connection ->
            val stateForPlayer = trueState.copy(
                players = trueState.players.map {
                    if (it.id == connection.userId) it else it.copy(progress = null)
                }
            )
            val stateJson = Gson().toJson(GameStateUpdate(stateForPlayer))
            connection.session.send(Frame.Text(stateJson))
        }
    }

    private suspend fun sendStateToPlayer(playerId: String) {
        val connection = connections.find { it.userId == playerId }
        if (connection != null) {
            val trueState = _gameState.value
            val stateForPlayer = trueState.copy(
                players = trueState.players.map {
                    if (it.id == playerId) it else it.copy(progress = null)
                }
            )
            val stateJson = Gson().toJson(GameStateUpdate(stateForPlayer))
            connection.session.send(Frame.Text(stateJson))
        }
    }

    private suspend fun sendAnnouncementToPlayer(playerId: String, message: String) {
        val connection = connections.find { it.userId == playerId }
        if (connection != null) {
            val announcementJson = Gson().toJson(Announcement(message))
            connection.session.send(Frame.Text(announcementJson))
        }
    }
}