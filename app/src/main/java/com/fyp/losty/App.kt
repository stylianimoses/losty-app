package com.fyp.losty

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

// Added imports for App Check
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Try to install Debug App Check provider (development only)
        try {
            // Install the debug provider (this class is available because firebase-appcheck-debug is added as a dependency)
            val debugFactory = DebugAppCheckProviderFactory.getInstance()
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(debugFactory)
            Log.i("App", "Installed DebugAppCheckProviderFactory for App Check (development)")

            // Attempt to fetch a token and log it so developer can register it with the Firebase console if needed
            FirebaseAppCheck.getInstance().getAppCheckToken(false)
                .addOnSuccessListener { tokenResult ->
                    try {
                        val tok = tokenResult.token
                        Log.i("App", "App Check debug token (copy and register in Firebase if you enable enforcement): $tok")
                    } catch (e: Exception) {
                        Log.w("App", "Could not read App Check token result: ${e.message}")
                    }
                }
                .addOnFailureListener { ex ->
                    Log.w("App", "Failed to fetch App Check token: ${ex.message}")
                }

        } catch (cnf: ClassNotFoundException) {
            // Debug provider not present on classpath
            Log.i("App", "DebugAppCheckProviderFactory not found on classpath; skipping App Check debug setup: ${cnf.message}")
        } catch (e: Exception) {
            Log.w("App", "Failed to install App Check debug provider: ${e.message}")
        }
    }
}
