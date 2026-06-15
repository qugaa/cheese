package com.example.cheese.data

import java.util.UUID

data class Notification(
    val id: String = UUID.randomUUID().toString(),
    val recipient: String = "",
    val sender: String = "",
    val type: String = "", // "INVITATION", "CONFIRMATION", "RESPONSE_COMPLETE", "CANCELLED", "UPDATED"
    val eventId: String = "",
    val eventName: String = "",
    val eventEmoji: String = "📅",
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val read: Boolean = false
)
