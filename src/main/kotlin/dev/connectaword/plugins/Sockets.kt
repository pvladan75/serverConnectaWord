package dev.connectaword.plugins

import dev.connectaword.server.RoomController
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureSockets() {
    install(WebSockets) {
        // Use the Kotlin Duration syntax
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        webSocket("/ws/game/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket
            // For now, we'll use dummy user data. We'll get this from a token later.
            val userId = "user-${(0..1000).random()}"
            val username = "Player${(0..1000).random()}"

            try {
                // Announce that a new user has joined
                RoomController.onJoin(roomId, userId, username, this)
                RoomController.broadcast(roomId, "User '$username' has joined the room.")

                // Listen for incoming messages
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        // Broadcast the received message to everyone in the room
                        RoomController.broadcast(roomId, "[$username]: $text")
                    }
                }
            } catch (e: Exception) {
                application.log.error("Error in WebSocket session", e)
            } finally {
                // Announce that the user has left
                RoomController.onLeave(roomId, this)
                RoomController.broadcast(roomId, "User '$username' has left the room.")
            }
        }
    }
}