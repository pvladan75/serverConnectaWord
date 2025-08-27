// dev/connectaword/plugins/TokenManager.kt

package dev.connectaword.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import java.util.*

object TokenManager {
    private val secret = "your-very-secret-key-that-is-long-enough" // Промени ово у нешто своје
    private val issuer = "http://0.0.0.0:8080"
    private val algorithm = Algorithm.HMAC256(secret)

    // Функција за генерисање токена
    fun generateToken(userId: String): String {
        return JWT.create()
            .withIssuer(issuer)
            .withClaim("userId", userId) // Убацујемо userId у токен
            .withExpiresAt(Date(System.currentTimeMillis() + 36_000_00 * 24)) // Важи 24 сата
            .sign(algorithm)
    }
}