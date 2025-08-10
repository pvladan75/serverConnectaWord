package dev.connectaword.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateRoomRequest(
    val name: String,
    val hostId: String // Later, we'll get this from an auth token
)

@Serializable
data class RoomResponse(
    val id: String,
    val name: String,
    val hostId: String,
    val createdAt: String
)