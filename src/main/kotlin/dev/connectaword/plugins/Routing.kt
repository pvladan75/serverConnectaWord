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
import java.time.Instant
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
                email = request.email,
                rating = 1500 // Почетни рејтинг
            )

            val token = TokenManager.generateToken(userId)
            val response = AuthResponse(
                token = token,
                korisnik = newUser
            )
            call.respond(HttpStatusCode.OK, response)
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            application.log.info("Primljen zahtev za prijavu: Email=${request.email}")

            val userRow = userDao.findUserByEmail(request.email)

            if (userRow == null) {
                call.respond(HttpStatusCode.BadRequest, "Pogrešan email ili lozinka.")
                return@post
            }

            val storedHashedPassword = userDao.getPasswordForUser(request.email) ?: ""
            val passwordMatches = BCrypt.checkpw(request.lozinka, storedHashedPassword)

            if (passwordMatches) {
                val user = User(
                    id = userRow.id,
                    korisnickoIme = userRow.korisnickoIme,
                    email = userRow.email,
                    rating = userRow.rating
                )

                val token = TokenManager.generateToken(user.id)
                val response = AuthResponse(
                    token = token,
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
                GameRooms.innerJoin(Users)
                    .select(
                        GameRooms.id,
                        GameRooms.name,
                        GameRooms.hostId,
                        Users.korisnickoIme,
                        Users.rating,
                        GameRooms.language,
                        GameRooms.wordSource,
                        GameRooms.createdAt
                    )
                    .map {
                        val originalName = it[GameRooms.name]
                        val language = it[GameRooms.language]
                        val hostUsername = it[Users.korisnickoIme]
                        val wordSource = it[GameRooms.wordSource]
                        val wordSourceDisplay = if (wordSource == "SERVER") "Serverske reči" else "Igračke reči"

                        val formattedName = "$originalName [$language] - Host: $hostUsername ($wordSourceDisplay)"

                        RoomResponse(
                            id = it[GameRooms.id],
                            name = formattedName,
                            hostId = it[GameRooms.hostId],
                            hostUsername = it[Users.korisnickoIme],
                            hostRating = it[Users.rating],
                            language = language,
                            wordSource = wordSource,
                            createdAt = DateTimeFormatter.ISO_INSTANT.format(it[GameRooms.createdAt])
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, rooms)
        }

        post("/create-room") {
            val request = call.receive<CreateRoomRequest>()

            val existingRoom = DatabaseFactory.dbQuery {
                // ISPRAVKA #2: Korišćenje .selectAll().where{...} umesto .selectWhere{...}
                GameRooms.selectAll().where { GameRooms.hostId eq request.hostId }.singleOrNull()
            }

            if (existingRoom != null) {
                call.respond(HttpStatusCode.Conflict, "You already have an active room.")
                return@post
            }

            val roomId = UUID.randomUUID().toString()
            val roomCreatedAt = Instant.now()

            val newRoomResponse = DatabaseFactory.dbQuery {
                GameRooms.insert {
                    it[id] = roomId
                    it[name] = request.name
                    it[hostId] = request.hostId
                    it[language] = request.language
                    it[wordSource] = request.wordSource
                    it[createdAt] = roomCreatedAt
                }

                // ISPRAVKA #2: Korišćenje .selectAll().where{...} umesto .selectWhere{...}
                val host = Users.selectAll().where { Users.id eq request.hostId }.single()

                val originalName = request.name
                val language = request.language
                val hostUsername = host[Users.korisnickoIme]
                val wordSource = request.wordSource
                val wordSourceDisplay = if (wordSource == "SERVER") "Serverske reči" else "Igračke reči"

                val formattedName = "$originalName [$language] - Host: $hostUsername ($wordSourceDisplay)"

                RoomResponse(
                    id = roomId,
                    name = formattedName,
                    hostId = request.hostId,
                    hostUsername = hostUsername,
                    hostRating = host[Users.rating],
                    language = language,
                    wordSource = wordSource,
                    createdAt = DateTimeFormatter.ISO_INSTANT.format(roomCreatedAt)
                )
            }

            call.respond(HttpStatusCode.Created, newRoomResponse)
        }
    }
}