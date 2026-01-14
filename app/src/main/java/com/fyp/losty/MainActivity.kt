package com.fyp.losty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyp.losty.navigation.AppNavigation
import com.fyp.losty.ui.theme.LOSTYTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // ✅ Connect to Firebase Emulators only in DEBUG build
        if (BuildConfig.DEBUG) {
            // Emulator wiring has been disabled per request.
            // To re-enable local emulator wiring for development, uncomment the line below:
            // FirebaseInit.useEmulators()
        }

        setContent {
            val appViewModel: AppViewModel = viewModel()
            val isDarkTheme by appViewModel.isDarkTheme.collectAsState()

            LOSTYTheme(darkTheme = isDarkTheme) {
                AppNavigation(appViewModel = appViewModel)
            }
        }
    }

    // no more inline emulator wiring here; centralized in FirebaseInit
}
