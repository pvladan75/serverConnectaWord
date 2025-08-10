package dev.connectaword.data

import dev.connectaword.database.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

class UserDao {
    suspend fun findUserByEmail(email: String): User? {
        return dbQuery {
            Users.selectAll().where { Users.email eq email }
                .map { toUser(it) }
                .singleOrNull()
        }
    }

    private fun toUser(row: ResultRow): User = User(
        id = row[Users.id],
        korisnickoIme = row[Users.korisnickoIme],
        email = row[Users.email]
    )
}