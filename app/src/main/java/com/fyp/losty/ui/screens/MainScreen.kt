package com.fyp.losty.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun MainScreen(appNavController: NavController) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier
    ) {
        composable("home") {
            HomeScreen(
                navController = navController,
                appNavController = appNavController
            )
        }
        composable("conversations") { ConversationsScreen(navController = navController) }
        composable("chat/{conversationId}") { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            ChatScreen(conversationId = conversationId, navController = navController)
        }
        composable("manage_own_posts") { ManageOwnPostsScreen(appNavController = appNavController) }
        composable("manage_active_claims") { ManageActiveClaimsScreen(navController = navController) }
        composable("manage_post_claims") { ManagePostClaimsScreen() }
        composable("profile") { ProfileScreen(navController = navController, appNavController = appNavController) }
    }
}
