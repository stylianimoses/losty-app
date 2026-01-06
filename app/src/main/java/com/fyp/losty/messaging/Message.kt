package com.fyp.losty.messaging

import com.google.firebase.Timestamp

/**
 * Simple chat message model stored in Firestore
 */
data class Message(
    val id: String = "",
    val senderId: String = "",
    val recipientId: String = "",
    val text: String = "",
    val timestamp: Timestamp? = null,
    val seen: Boolean = false
)

