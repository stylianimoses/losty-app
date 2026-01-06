package com.fyp.losty

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.database.FirebaseDatabase

object FirebaseInit {
    // Emulator host for the Android emulator (10.0.2.2 maps to the host machine)
    private const val EMULATOR_HOST = "10.0.2.2"

    fun useEmulators() {
        try {
            // NOTE: Emulator wiring has been disabled per request. The original code
            // below is left commented so you can re-enable emulator usage during
            // local development by uncommenting the lines.

            // Firestore
            // val firestore = FirebaseFirestore.getInstance()
            // firestore.useEmulator(EMULATOR_HOST, 8080)

            // Auth
            // val auth = FirebaseAuth.getInstance()
            // auth.useEmulator(EMULATOR_HOST, 9099)

            // Realtime Database (important for auth-related account lookups and messaging if using RTDB)
            // try {
            //     val realtime = FirebaseDatabase.getInstance()
            //     realtime.useEmulator(EMULATOR_HOST, 9000)
            // } catch (e: Exception) {
            //     // If the RTDB dependency is missing, skip gracefully
            //     e.printStackTrace()
            // }

            // Functions (call useEmulator via reflection so the file compiles even if functions lib isn't on the analyzer classpath)
            // try {
            //     val functionsClass = Class.forName("com.google.firebase.functions.FirebaseFunctions")
            //     val getInstance = functionsClass.getMethod("getInstance")
            //     val functionsInstance = getInstance.invoke(null)
            //     val useEmulatorMethod = functionsClass.getMethod("useEmulator", String::class.java, Int::class.javaPrimitiveType)
            //     useEmulatorMethod.invoke(functionsInstance, EMULATOR_HOST, 5001)
            // } catch (_: ClassNotFoundException) {
            //     // functions lib not available in analyzer; skip
            // }

            // Storage
            // val storage = FirebaseStorage.getInstance()
            // storage.useEmulator(EMULATOR_HOST, 9199)

        } catch (e: Exception) {
            // Swallow errors during emulator wiring; log if needed
            e.printStackTrace()
        }
    }
}
