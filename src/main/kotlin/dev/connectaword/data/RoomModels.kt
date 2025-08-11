package dev.connectaword.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateRoomRequest(
    val name: String,
    val hostId: String,
    val language: String, // Add this
    val wordSource: String // Add this
)

@Serializable
data class RoomResponse(
    val id: String,
    val name: String,
    val hostId: String,
    val createdAt: String
)