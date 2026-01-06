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
        // Show a simple notification for data messages
        val title = remoteMessage.notification?.title ?: "New message"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["text"] ?: ""
        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        // Save the token to Firestore under user profile when available
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.w("FCM", "Token refreshed but user not logged in; will attach on next login.")
            return
        }
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { Log.d("FCM", "FCM token saved for user $uid") }
            .addOnFailureListener { e -> Log.e("FCM", "Failed to save FCM token: ${e.message}", e) }
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "messages"
        val nm = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Messages", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setColor(0xFFFF4081.toInt()) // Electric Pink accent
            .setPriority(NotificationCompat.PRIORITY_HIGH) // pre-O behavior
            .setContentIntent(pending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        nm.notify(1, notif)
    }
}
