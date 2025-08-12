package dev.connectaword.server

import dev.connectaword.server.Game
import dev.connectaword.data.GameRooms
import dev.connectaword.data.MakeGuess
import dev.connectaword.database.DatabaseFactory
import io.ktor.websocket.*
import org.jetbrains.exposed.sql.select
import java.util.concurrent.ConcurrentHashMap

data class GameConnection(
    val session: WebSocketSession,
    val userId: String,
    val username: String
)

object RoomController {
    private val games = ConcurrentHashMap<String, Game>()

    suspend fun onJoin(roomId: String, userId: String, username: String, session: WebSocketSession) {
        // Прво проверавамо да ли игра већ постоји у меморији.
        var game = games[roomId]

        // Ако не постоји, креирамо је.
        if (game == null) {
            // Овај блок је 'suspend', тако да овде СМЕМО да позовемо 'dbQuery'.
            val room = DatabaseFactory.dbQuery {
                GameRooms.select { GameRooms.id eq roomId }.singleOrNull()
            }

            // Ако соба не постоји у бази, одбијамо конекцију.
            if (room == null) {
                println("Error: Room with ID $roomId not found. Connection rejected.")
                session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Room not found"))
                return // Прекидамо даље извршавање
            }

            // Ако соба постоји, креирамо нову инстанцу Игре.
            val newGame = Game(roomId, room[GameRooms.language], room[GameRooms.hostId])
            games[roomId] = newGame
            game = newGame
        }

        // Од ове тачке, 'game' је гарантовано non-null.
        val connection = GameConnection(session, userId, username)
        game.addPlayer(connection)

        // Обавештавамо све о новом стању (нови играч је ушао).
        game.broadcastState()
    }

    suspend fun onStartGame(roomId: String, userId: String) {
        val game = games[roomId] ?: return

        if (game.hostId == userId) {
            game.startGame()
            game.broadcastState()
        } else {
            println("User $userId (not host) tried to start the game in room $roomId.")
        }
    }

    suspend fun onLeave(roomId: String, session: WebSocketSession) {
        val game = games[roomId] ?: return
        game.removePlayerBySession(session)

        if (game.isEmpty()) {
            games.remove(roomId)
        } else {
            game.broadcastState()
        }
    }

    suspend fun onGuess(roomId: String, userId: String, session: WebSocketSession, guess: MakeGuess) {
        val game = games[roomId]
        val connection = game?.connections?.find { it.session == session } ?: return
        game.processGuess(connection, guess)
    }
}