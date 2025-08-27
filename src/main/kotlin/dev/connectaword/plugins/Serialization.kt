package dev.connectaword.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            // Ова линија осигурава да сервер увек шаље сва поља у JSON-у,
            // чак и ако имају подразумевану вредност (као што је status="WAITING").
            encodeDefaults = true
        })
    }
}