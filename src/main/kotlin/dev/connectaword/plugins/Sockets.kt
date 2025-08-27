package dev.connectaword.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.gson.Gson
import dev.connectaword.data.*
import dev.connectaword.database.DatabaseFactory
import dev.connectaword.server.RoomController
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.jetbrains.exposed.sql.select
import kotlin.time.Duration.Companion.seconds

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val gson = Gson()

    routing {
        webSocket("/ws/game/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket

            val token = call.request.queryParameters["token"]
            if (token == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No token provided"))
                return@webSocket
            }

            val decodedJWT = try {
                val secret = "your-very-secret-key-that-is-long-enough"
                val algorithm = Algorithm.HMAC256(secret)
                val verifier = JWT.require(algorithm).build()
                verifier.verify(token)
            } catch (e: Exception) {
                application.log.error("Token validation failed", e)
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }

            val userId = decodedJWT.getClaim("userId").asString()

            val userRow = DatabaseFactory.dbQuery {
                Users.select { Users.id eq userId }.singleOrNull()
            }
            val username = userRow?.get(Users.korisnickoIme) ?: "Guest"
            val rating = userRow?.get(Users.rating) ?: 1500

            try {
                RoomController.onJoin(roomId, userId, username, rating, this)

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()

                        try {
                            // 👇 НОВА, РОБУСНИЈА ЛОГИКА ЗА ОБРАДУ ПОРУКА 👇
                            // 1. Прво парсирамо само "action" поље да видимо о чему се ради
                            val baseAction = gson.fromJson(text, BaseAction::class.java)

                            // 2. Онда, на основу акције, парсирамо целу поруку у исправан објекат
                            val gameMessage: GameMessage? = when (baseAction.action) {
                                "start" -> gson.fromJson(text, StartGame::class.java)
                                "guess" -> gson.fromJson(text, MakeGuess::class.java)
                                "surrender" -> gson.fromJson(text, SurrenderRound::class.java)
                                "play_again" -> gson.fromJson(text, PlayAgain::class.java)
                                else -> null
                            }

                            // 3. Шаљемо поруку даље на обраду
                            when(gameMessage) {
                                is MakeGuess -> RoomController.onGuess(roomId, userId, this, gameMessage)
                                is StartGame -> RoomController.onStartGame(roomId, userId)
                                is SurrenderRound -> RoomController.onSurrender(roomId, userId)
                                is PlayAgain -> RoomController.onPlayAgain(roomId, userId)
                                else -> application.log.warn("Received unknown WebSocket action: ${baseAction.action}")
                            }
                        } catch (e: Exception) {
                            application.log.error("Error parsing WebSocket message: $text", e)
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