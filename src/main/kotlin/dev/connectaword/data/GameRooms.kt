package dev.connectaword.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object GameRooms : Table() {
    val id = varchar("id", 128)
    val name = varchar("name", 256)
    val hostId = varchar("host_id", 128) references Users.id
    val createdAt = timestamp("created_at").default(Instant.now())
    // We can add password, max_players, etc. here later

    override val primaryKey = PrimaryKey(id)
}