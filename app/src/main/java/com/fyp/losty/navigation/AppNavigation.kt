package com.fyp.losty.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fyp.losty.auth.LoginScreen
import com.fyp.losty.auth.RegisterScreen
import com.fyp.losty.auth.ResetPasswordScreen
import com.fyp.losty.ui.screens.*
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) "main_graph" else "auth_graph"

    NavHost(navController = navController, startDestination = startDestination) {
        navigation(startDestination = "login", route = "auth_graph") {
            composable("login") { LoginScreen(navController = navController) }
            composable("register") { RegisterScreen(navController = navController) }
            composable("reset_password") { ResetPasswordScreen(navController = navController) }
        }
        navigation(startDestination = "main", route = "main_graph") {
            composable("main") { MainScreen(appNavController = navController) }
            composable("create_post") { CreatePostScreen(navController = navController) }
            composable("edit_post/{postId}") { backStackEntry ->
                val postId = backStackEntry.arguments?.getString("postId")
                if (postId != null) {
                    EditPostScreen(postId = postId, navController = navController)
                }
            }
            composable("conversations") { ConversationsScreen(navController = navController) }

            // Chat route with optional query parameter 'otherUserName'
            composable(
                route = "chat/{conversationId}?otherUserName={otherUserName}",
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.StringType },
                    navArgument("otherUserName") { type = NavType.StringType; nullable = true }
                )
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                val otherNameArg = backStackEntry.arguments?.getString("otherUserName")?.takeIf { it.isNotBlank() }
                ChatScreen(conversationId = conversationId, navController = navController, otherUserNameArg = otherNameArg)
            }

            composable("my_activity") { MyActivityScreen(navController = navController) }
            composable("profile") { ProfileScreen(navController = navController) }
        }
    }
}