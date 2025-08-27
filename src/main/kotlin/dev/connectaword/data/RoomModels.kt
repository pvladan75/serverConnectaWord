package dev.connectaword.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateRoomRequest(
    val name: String,
    val hostId: String,
    val language: String,
    val wordSource: String
)

@Serializable
data class RoomResponse(
    val id: String,
    val name: String,
    val hostId: String,
    val hostRating: Int,
    val hostUsername: String,
    val language: String,
    val wordSource: String, // <-- ДОДАТО ПОЉЕ
    val createdAt: String
)