package com.fyp.losty.ui.components

import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.fyp.losty.R
import androidx.compose.ui.graphics.Color
import com.fyp.losty.ui.theme.*

@Composable
fun BackToHomeButton(
    navController: NavController,
    tint: Color = TextBlack,
    contentDescription: String = "Back to home"
) {
    IconButton(onClick = { navController.navigate("home") }) {
        Icon(
            painter = painterResource(id = R.drawable.outline_arrow_back_24),
            contentDescription = contentDescription,
            tint = tint
        )
    }
}
