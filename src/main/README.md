# ConnectaWord - Server

Ovo je backend server za multiplayer igru pogađanja reči "ConnectaWord". Izgrađen je korišćenjem Ktor framework-a i Kotlin-a, i dizajniran je da upravlja korisnicima, sobama za igru i real-time komunikacijom.

## ✨ Ključne Funkcionalnosti

* **Autentifikacija korisnika:**
    * Registracija (`/register`) sa heširanjem lozinki (BCrypt).
    * Prijava (`/login`) sa verifikacijom lozinke.
* **Upravljanje Sobama za Igru:**
    * Kreiranje nove sobe (`/create-room`).
    * Prikaz liste svih dostupnih soba (`/rooms`).
* **Real-time Komunikacija:**
    * WebSocket endpoint (`/ws/game/{roomId}`) za svaku sobu.
    * Upravljanje konekcijama igrača (ulazak, izlazak).
    * Slanje poruka svim igračima u sobi (broadcasting).

## 🛠️ Tehnološki Stek

* **Framework:** [Ktor](https://ktor.io/)
* **Jezik:** [Kotlin](https://kotlinlang.org/)
* **Server:** [Netty](https://netty.io/)
* **Baza Podataka:** [PostgreSQL](https://www.postgresql.org/)
* **DB Framework:** [Exposed](https://github.com/JetBrains/Exposed)
* **Asinhrono programiranje:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
* **Serijalizacija:** [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
* **Build System:** [Gradle (Kotlin DSL)](https://gradle.org/)

## 🚀 Pokretanje

### Lokalno

1.  **Podešavanje Baze:**
    * Instalirajte PostgreSQL.
    * Kreirajte bazu pod nazivom `connectaword_db`.
    * U fajlu `DatabaseFactory.kt`, unesite Vašu lozinku za `postgres` korisnika.
2.  **Pokretanje Servera:**
    * Otvorite projekat u IntelliJ IDEA.
    * Pokrenite `main` funkciju u `Application.kt`.
    * Server će biti dostupan na `http://0.0.0.0:8080`.

### Produkcija (DigitalOcean)

* Projekat je namenjen za pokretanje na DigitalOcean Droplet-u sa Ubuntu 22.04 i PostgreSQL Managed Database.

## 🚧 Status Projekta

* Trenutno u fazi razvoja.
* Osnovne funkcionalnosti (autentifikacija, lobi) su implementirane.
* Sledeći korak: Implementacija logike same igre.