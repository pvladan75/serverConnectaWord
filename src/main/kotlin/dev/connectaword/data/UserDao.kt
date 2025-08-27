package dev.connectaword.data

import dev.connectaword.database.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus

class UserDao {
    suspend fun findUserByEmail(email: String): User? {
        return dbQuery {
            Users.select { Users.email eq email }
                .map { toUser(it) }
                .singleOrNull()
        }
    }

    suspend fun getPasswordForUser(email: String): String? {
        return dbQuery {
            Users.select { Users.email eq email }
                .map { it[Users.lozinka] }
                .singleOrNull()
        }
    }

    data class UserStats(
        val id: String,
        val rating: Int,
        val gamesPlayed: Int
    )

    suspend fun getStatsForUsers(userIds: List<String>): List<UserStats> {
        return dbQuery {
            Users.select { Users.id inList userIds }
                .map {
                    UserStats(
                        id = it[Users.id],
                        rating = it[Users.rating],
                        gamesPlayed = it[Users.gamesPlayed]
                    )
                }
        }
    }

    suspend fun updateUserRatingsAndGamesPlayed(newRatings: Map<String, Int>) {
        dbQuery {
            newRatings.forEach { (userId, newRating) ->
                Users.update({ Users.id eq userId }) {
                    it[rating] = newRating
                    it[gamesPlayed] = gamesPlayed + 1
                }
            }
        }
    }

    private fun toUser(row: ResultRow): User = User(
        id = row[Users.id],
        korisnickoIme = row[Users.korisnickoIme],
        email = row[Users.email],
        // Важно је додати и рејтинг овде да би био доступан
        rating = row[Users.rating]
    )
}