package dev.connectaword.plugins

import dev.connectaword.data.*
import dev.connectaword.database.DatabaseFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.mindrot.jbcrypt.BCrypt
import java.time.format.DateTimeFormatter
import java.util.*

fun Application.configureRouting() {
    val userDao = UserDao()

    routing {
        get("/") {
            call.respondText("Welcome to ConnectaWord Server!")
        }

        // --- Authentication Routes ---

        post("/register") {
            val request = call.receive<RegisterRequest>()
            application.log.info("Primljen zahtev za registraciju: Email=${request.email}")

            // TODO: Check if email already exists

            val hashedPassword = BCrypt.hashpw(request.lozinka, BCrypt.gensalt())
            val userId = UUID.randomUUID().toString()

            DatabaseFactory.dbQuery {
                Users.insert {
                    it[id] = userId
                    it[korisnickoIme] = request.korisnickoIme
                    it[email] = request.email
                    it[lozinka] = hashedPassword
                }
            }

            val newUser = User(
                id = userId,
                korisnickoIme = request.korisnickoIme,
                email = request.email
            )
            val response = AuthResponse(
                token = "dummy-jwt-token-for-${request.korisnickoIme}",
                korisnik = newUser
            )
            call.respond(HttpStatusCode.OK, response)
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            application.log.info("Primljen zahtev za prijavu: Email=${request.email}")

            val userRow = DatabaseFactory.dbQuery {
                Users.selectAll().where { Users.email eq request.email }.singleOrNull()
            }

            if (userRow == null) {
                call.respond(HttpStatusCode.BadRequest, "Pogrešan email ili lozinka.")
                return@post
            }

            val storedHashedPassword = userRow[Users.lozinka]
            val passwordMatches = BCrypt.checkpw(request.lozinka, storedHashedPassword)

            if (passwordMatches) {
                val user = User(
                    id = userRow[Users.id],
                    korisnickoIme = userRow[Users.korisnickoIme],
                    email = userRow[Users.email]
                )
                val response = AuthResponse(
                    token = "dummy-jwt-token-for-${user.email}",
                    korisnik = user
                )
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.BadRequest, "Pogrešan email ili lozinka.")
            }
        }

        // --- Game Room Routes ---

        get("/rooms") {
            val rooms = DatabaseFactory.dbQuery {
                GameRooms.selectAll().map {
                    RoomResponse(
                        id = it[GameRooms.id],
                        name = it[GameRooms.name],
                        hostId = it[GameRooms.hostId],
                        createdAt = DateTimeFormatter.ISO_INSTANT.format(it[GameRooms.createdAt])
                    )
                }
            }
            call.respond(HttpStatusCode.OK, rooms)
        }

        post("/create-room") {
            val request = call.receive<CreateRoomRequest>()
            val roomId = UUID.randomUUID().toString()

            DatabaseFactory.dbQuery {
                GameRooms.insert {
                    it[id] = roomId
                    it[name] = request.name
                    it[hostId] = request.hostId
                }
            }
            call.respond(HttpStatusCode.Created, mapOf("message" to "Room created successfully", "roomId" to roomId))
        }
    }
}