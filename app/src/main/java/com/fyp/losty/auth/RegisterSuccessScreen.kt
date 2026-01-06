package com.fyp.losty.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.draw.clip

@Composable
fun RegisterSuccessScreen(navController: NavController) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val realtime = FirebaseDatabase.getInstance().reference
    val firestore = FirebaseFirestore.getInstance()

    var photoUrl by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf<String?>(null) }
    var fullName by remember { mutableStateOf<String?>(null) }
    var phoneNumber by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        if (uid != null) {
            // Read from Realtime DB
            try {
                val snap = realtime.child("Users").child(uid).get().await()
                if (snap.exists()) {
                    photoUrl = snap.child("photoUrl").getValue(String::class.java)
                    email = snap.child("email").getValue(String::class.java)
                    fullName = snap.child("fullName").getValue(String::class.java)
                    phoneNumber = snap.child("phoneNumber").getValue(String::class.java)
                }
            } catch (e: Exception) {
                // Ignore and rely on Firestore fallback
            }

            // Firestore fallback
            try {
                val doc = firestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    if (photoUrl.isNullOrEmpty()) photoUrl = doc.getString("photoUrl")
                    if (email.isNullOrEmpty()) email = doc.getString("email")
                    if (fullName.isNullOrEmpty()) fullName = doc.getString("fullName")
                    if (phoneNumber.isNullOrEmpty()) phoneNumber = doc.getString("phoneNumber")
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Registration Complete", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))
            if (photoUrl != null && photoUrl!!.isNotEmpty()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Text("No profile image uploaded")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("User: ${fullName ?: "-"}")
            Text("Email: ${email ?: "-"}")
            Text("Phone: ${phoneNumber ?: "-"}")

            Spacer(modifier = Modifier.height(16.dp))
            Text("Data written to:")
            Text("Realtime DB: /Users/${uid ?: "<unknown>"}")
            Text("Firestore: /users/${uid ?: "<unknown>"}")

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { navController.navigate("main") }) {
                Text("Continue to app")
            }
        }
    }
}
