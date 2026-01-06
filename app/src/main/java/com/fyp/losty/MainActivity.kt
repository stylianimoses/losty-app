package com.fyp.losty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fyp.losty.auth.LoginScreen
import com.fyp.losty.auth.RegisterScreen
import com.fyp.losty.auth.RegisterSuccessScreen
import com.fyp.losty.ui.screens.CreatePostScreen
import com.fyp.losty.ui.screens.EditPostScreen
import com.fyp.losty.ui.screens.MainScreen
import com.fyp.losty.ui.theme.LOSTYTheme
import com.google.firebase.auth.FirebaseAuth

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
            LOSTYTheme {
                AppNavigation()
            }
        }
    }

    // no more inline emulator wiring here; centralized in FirebaseInit
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Start destination based on whether user is signed in
    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) "main" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") { LoginScreen(navController = navController) }
        composable("register") { RegisterScreen(navController = navController) }
        composable("register_success") { RegisterSuccessScreen(navController = navController) }
        composable("main") { MainScreen(appNavController = navController) }
        composable("create_post") { CreatePostScreen(navController = navController) }
        composable("edit_post/{postId}") { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId")
            EditPostScreen(postId = postId, navController = navController)
        }
    }
}
