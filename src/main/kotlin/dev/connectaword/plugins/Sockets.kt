package dev.connectaword.plugins

import dev.connectaword.data.MakeGuess
import dev.connectaword.server.RoomController
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

fun Application.configureSockets() {
    install(WebSockets) {
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
                // Handle the user joining the room
                RoomController.onJoin(roomId, userId, username, this)

                // Listen for incoming messages from this client
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        // Assume the incoming text is a JSON representation of a MakeGuess action
                        val guessAction = Json.decodeFromString<MakeGuess>(text)
                        RoomController.onGuess(roomId, userId, this, guessAction)
                    }
                }
            } catch (e: Exception) {
                application.log.error("Error in WebSocket session", e)
            } finally {
                // Handle the user leaving the room
                RoomController.onLeave(roomId, this)
            }
        }
    }
}