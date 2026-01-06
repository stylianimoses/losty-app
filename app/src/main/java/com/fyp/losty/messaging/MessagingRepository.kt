package com.fyp.losty.messaging

import com.fyp.losty.Message
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow

class MessagingRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val conversations = db.collection("conversations")

    fun sendMessage(conversationId: String, message: Message): Task<Void> {
        val messagesRef = conversations.document(conversationId).collection("messages")
        val docRef = messagesRef.document()
        val data = mapOf(
            "conversationId" to conversationId,
            "senderId" to message.senderId,
            "senderName" to message.senderName,
            "text" to message.text,
            "timestamp" to Timestamp.now(),
            "read" to message.read
        )
        return docRef.set(data)
    }

    suspend fun sendMessageSuspend(conversationId: String, message: Message) {
        val messagesRef = conversations.document(conversationId).collection("messages")
        val docRef = messagesRef.document()
        val data = mapOf(
            "conversationId" to conversationId,
            "senderId" to message.senderId,
            "senderName" to message.senderName,
            "text" to message.text,
            "timestamp" to Timestamp.now(),
            "read" to message.read
        )
        docRef.set(data).await()
    }

    fun listenMessages(conversationId: String): Flow<List<Message>> = callbackFlow {
        val messagesRef = conversations.document(conversationId).collection("messages").orderBy("timestamp")
        val sub = messagesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                try {
                    val ts = doc.getTimestamp("timestamp")
                    val epoch = ts?.toDate()?.time ?: System.currentTimeMillis()
                    Message(
                        id = doc.id,
                        conversationId = doc.getString("conversationId") ?: conversationId,
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "",
                        text = doc.getString("text") ?: "",
                        timestamp = epoch,
                        read = doc.getBoolean("read") ?: false
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { sub.remove() }
    }

    // One-shot fetch for pull-to-refresh behavior
    suspend fun fetchMessagesOnce(conversationId: String): List<Message> {
        val snapshot = conversations.document(conversationId).collection("messages").orderBy("timestamp", Query.Direction.ASCENDING).get().await()
        return snapshot.documents.mapNotNull { doc ->
            try {
                val ts = doc.getTimestamp("timestamp")
                val epoch = ts?.toDate()?.time ?: System.currentTimeMillis()
                Message(
                    id = doc.id,
                    conversationId = doc.getString("conversationId") ?: conversationId,
                    senderId = doc.getString("senderId") ?: "",
                    senderName = doc.getString("senderName") ?: "",
                    text = doc.getString("text") ?: "",
                    timestamp = epoch,
                    read = doc.getBoolean("read") ?: false
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
