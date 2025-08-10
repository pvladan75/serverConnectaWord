package dev.connectaword.server

import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

// This class holds the state for a single connection
data class GameConnection(
    val session: WebSocketSession,
    val userId: String,
    val username: String
)

// This object will manage all active game rooms and their connections
object RoomController {
    // A thread-safe map where:
    // Key = Room ID (String)
    // Value = Set of connections in that room (Set<GameConnection>)
    val rooms = ConcurrentHashMap<String, MutableSet<GameConnection>>()

    fun onJoin(roomId: String, userId: String, username: String, session: WebSocketSession) {
        val room = rooms.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }
        room.add(GameConnection(session, userId, username))
    }

    suspend fun broadcast(roomId: String, message: String) {
        rooms[roomId]?.forEach { connection ->
            connection.session.send(Frame.Text(message))
        }
    }

    fun onLeave(roomId: String, session: WebSocketSession) {
        val room = rooms[roomId]
        room?.removeIf { it.session == session }

        // Optional: remove the room if it's empty
        if (room?.isEmpty() == true) {
            rooms.remove(roomId)
        }
    }
}