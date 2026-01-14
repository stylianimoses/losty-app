package com.fyp.losty.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import com.fyp.losty.ui.theme.TextBlack

/**
 * A reusable back button that pops the current destination from the navigation stack.
 * This correctly navigates the user to the previous screen.
 */
@Composable
fun BackButton(
    navController: NavController,
    tint: Color = TextBlack,
    contentDescription: String = "Back"
) {
    IconButton(onClick = { navController.popBackStack() }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

/**
 * A button that explicitly navigates to the "home" screen.
 * The name is misleading if used as a generic back button.
 * Consider using BackButton for standard up navigation.
 */
@Composable
fun BackToHomeButton(
    navController: NavController,
    tint: Color = TextBlack,
    contentDescription: String = "Back to home"
) {
    IconButton(onClick = { navController.navigate("home") }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}
