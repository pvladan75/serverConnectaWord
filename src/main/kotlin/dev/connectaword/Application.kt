package dev.connectaword

import dev.connectaword.database.DatabaseFactory
import dev.connectaword.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Inicijalizacija konekcije sa bazom podataka
    DatabaseFactory.init()

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    configureSerialization()
    configureSockets()
    configureRouting()
}