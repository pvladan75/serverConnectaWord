package dev.connectaword.server

import dev.connectaword.data.GameRooms
import dev.connectaword.data.MakeGuess
import dev.connectaword.database.DatabaseFactory
import io.ktor.util.logging.*
import io.ktor.websocket.*
import org.jetbrains.exposed.sql.select
import java.util.concurrent.ConcurrentHashMap

data class GameConnection(
    val session: WebSocketSession,
    val userId: String,
    val username: String,
    val rating: Int
)

object RoomController {
    private val logger = KtorSimpleLogger("dev.connectaword.server.RoomController")
    private val games = ConcurrentHashMap<String, Game>()

    suspend fun onJoin(roomId: String, userId: String, username: String, rating: Int, session: WebSocketSession) {
        logger.info("Player $username ($userId) attempting to join room $roomId")

        var game = games[roomId]
        if (game == null) {
            logger.info("No existing game found for room $roomId. Creating a new one.")
            val room = DatabaseFactory.dbQuery {
                GameRooms.select { GameRooms.id eq roomId }.singleOrNull()
            }

            if (room == null) {
                logger.error("Room with ID $roomId not found in DB. Connection rejected.")
                session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Room not found"))
                return
            }

            val newGame = Game(roomId, room[GameRooms.language], room[GameRooms.hostId])
            games[roomId] = newGame
            game = newGame
            logger.info("New game created for room $roomId. Host: ${newGame.hostId}, Language: ${room[GameRooms.language]}")
        }

        val connection = GameConnection(session, userId, username, rating)
        game.addPlayer(connection)

        logger.info("Broadcasting state after player $username joined.")
        game.broadcastState()
    }

    suspend fun onPlayAgain(roomId: String, userId: String) {
        logger.info("Received PlayAgain request from $userId for room $roomId")
        val game = games[roomId] ?: return
        if (game.hostId == userId) {
            game.startGame() // Поново покрећемо игру са новим речима
        }
    }

    suspend fun onStartGame(roomId: String, userId: String) {
        logger.info("Received StartGame request from $userId for room $roomId")
        val game = games[roomId] ?: return

        if (game.hostId == userId) {
            logger.info("Host $userId confirmed. Starting game in room $roomId.")
            game.startGame()
            game.broadcastState()
        } else {
            logger.warn("User $userId (not host) tried to start the game in room $roomId.")
        }
    }

    suspend fun onLeave(roomId: String, session: WebSocketSession) {
        val game = games[roomId] ?: return
        game.removePlayerBySession(session)

        if (game.isEmpty()) {
            games.remove(roomId)
            logger.info("Room $roomId is empty and has been removed.")
        } else {
            logger.info("Player left room $roomId. Broadcasting new state.")
            game.broadcastState()
        }
    }

    suspend fun onSurrender(roomId: String, userId: String) {
        logger.info("Player $userId is surrendering the round in room $roomId")
        val game = games[roomId] ?: return
        game.processSurrender(userId)
    }

    suspend fun onGuess(roomId: String, userId: String, session: WebSocketSession, guess: MakeGuess) {
        val game = games[roomId] ?: return

        logger.info("Processing guess '${guess.guess}' from player $userId in room $roomId.")

        // 👇 ИСПРАВКА ЈЕ ОВДЕ 👇
        // Уместо 'connection' објекта, прослеђујемо 'userId' String,
        // јер то је оно што processGuess очекује.
        game.processGuess(userId, guess)
    }
}