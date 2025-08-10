package dev.connectaword.plugins

import dev.connectaword.data.AuthResponse
import dev.connectaword.data.LoginRequest
import dev.connectaword.data.RegisterRequest
import dev.connectaword.data.User
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Welcome to ConnectaWord Server!")
        }

        post("/register") {
            val request = call.receive<RegisterRequest>()

            application.log.info("Primljen zahtev za registraciju: Korisnik=${request.korisnickoIme}, Email=${request.email}")

            if (request.lozinka.length < 6) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Lozinka mora imati najmanje 6 karaktera."
                )
                return@post
            }

            val newUser = User(
                id = UUID.randomUUID().toString(),
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

            if (request.email.isNotBlank() && request.lozinka.isNotBlank()) {
                val dummyUser = User(
                    id = UUID.randomUUID().toString(),
                    korisnickoIme = "korisnik-iz-baze",
                    email = request.email
                )
                val response = AuthResponse(
                    token = "dummy-jwt-token-for-${request.email}",
                    korisnik = dummyUser
                )
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.BadRequest, "Email i lozinka su obavezni.")
            }
        }
    }
}