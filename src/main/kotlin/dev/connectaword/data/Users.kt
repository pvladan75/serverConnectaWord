package dev.connectaword.data

import org.jetbrains.exposed.sql.Table

object Users : Table() {
    val id = varchar("id", 128)
    val korisnickoIme = varchar("korisnicko_ime", 256)
    val email = varchar("email", 256)
    val lozinka = varchar("lozinka", 512) // We will store a hashed password here later

    override val primaryKey = PrimaryKey(id)
}