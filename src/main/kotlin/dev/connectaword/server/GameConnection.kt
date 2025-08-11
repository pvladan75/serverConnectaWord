package dev.connectaword.server

import dev.connectaword.data.MakeGuess
import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

// This class holds the state for a single connection (no changes here)
data class GameConnection(
    val session: WebSocketSession,
    val userId: String,
    val username: String
)

// This object will manage all active game rooms
object RoomController {
    // The map now holds Game instances instead of just connections
    private val games = ConcurrentHashMap<String, Game>()

    suspend fun onJoin(roomId: String, userId: String, username: String, session: WebSocketSession) {
        // Find the game or create a new one if it doesn't exist
        val game = games.computeIfAbsent(roomId) { Game(roomId) }
        val connection = GameConnection(session, userId, username)

        game.addPlayer(connection)

        // Broadcast the new state to all players in the room
        game.broadcastState()
    }

    suspend fun onLeave(roomId: String, session: WebSocketSession) {
        val game = games[roomId] ?: return

        game.removePlayerBySession(session)

        if (game.isEmpty()) {
            games.remove(roomId)
        } else {
            // Broadcast the updated state (without the departed player)
            game.broadcastState()
        }
    }

    suspend fun onGuess(roomId: String, userId: String, session: WebSocketSession, guess: MakeGuess) {
        val game = games[roomId]
        // We need the username, let's find the connection object
        val connection = game?.connections?.find { it.session == session } ?: return

        game.processGuess(connection, guess)
    }
}