package dev.connectaword.data

import kotlinx.serialization.Serializable

// DODAJTE OVU KLASU
@Serializable
data class LoginRequest(
    val email: String,
    val lozinka: String
)

@Serializable
data class RegisterRequest(
    val korisnickoIme: String,
    val email: String,
    val lozinka: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val korisnik: User
)

@Serializable
data class User(
    val id: String,
    val korisnickoIme: String,
    val email: String,
    val rating: Int
)