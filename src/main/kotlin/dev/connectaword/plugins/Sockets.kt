package dev.connectaword.plugins

import com.google.gson.Gson
import dev.connectaword.data.GameMessage
import dev.connectaword.data.MakeGuess
import dev.connectaword.data.StartGame
import dev.connectaword.server.RoomController
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val gson = Gson() // Креирамо Gson инстанцу

    routing {
        webSocket("/ws/game/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket
            // TODO: Учитати правог корисника из JWT токена
            val userId = "user-${(0..1000).random()}"
            val username = "Player${(0..1000).random()}"

            try {
                RoomController.onJoin(roomId, userId, username, this)

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()

                        // Паметно декодирање помоћу Gson-а
                        val gameMessage: GameMessage? = when {
                            text.contains("\"guess\"") -> {
                                gson.fromJson(text, MakeGuess::class.java)
                            }
                            text.contains("\"action\":\"start\"") -> {
                                gson.fromJson(text, StartGame::class.java)
                            }
                            else -> null
                        }

                        // На основу типа поруке, позивамо одговарајућу функцију
                        when(gameMessage) {
                            is MakeGuess -> {
                                RoomController.onGuess(roomId, userId, this, gameMessage)
                            }
                            is StartGame -> {
                                RoomController.onStartGame(roomId, userId)
                            }
                            null -> {
                                application.log.warn("Received unknown WebSocket message: $text")
                            }
                            else -> {
                                // Игноришемо остале типове
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                application.log.error("Error in WebSocket session", e)
            } finally {
                RoomController.onLeave(roomId, this)
            }
        }
    }
}