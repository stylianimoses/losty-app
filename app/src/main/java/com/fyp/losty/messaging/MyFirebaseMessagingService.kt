package com.fyp.losty.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.fyp.losty.MainActivity
import com.fyp.losty.R

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Log for debugging
        Log.d("FCM", "From: ${remoteMessage.from}")

        // 1. Check if message contains a notification payload.
        remoteMessage.notification?.let {
            showNotification(it.title ?: "New Update", it.body ?: "")
        }

        // 2. Also check if message contains a data payload (usually for custom chat logic).
        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "New Chat Message"
            val body = remoteMessage.data["body"] ?: remoteMessage.data["text"] ?: "Check your messages"
            showNotification(title, body)
        }
    }

    override fun onNewToken(token: String) {
        // Save the token to Firestore under user profile when available
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .update("fcmToken", token)
            .addOnSuccessListener { Log.d("FCM", "FCM token updated for user $uid") }
            .addOnFailureListener { e -> 
                // Fallback to set with merge if document doesn't exist yet
                db.collection("users").document(uid)
                    .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
            }
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "messages"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Messages & Chat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat messages and claim updates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this icon exists
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(0xFFFF4081.toInt()) // Electric Pink
            .setContentIntent(pendingIntent)

        // For Android 13+ (Tiramisu), check permission before showing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
