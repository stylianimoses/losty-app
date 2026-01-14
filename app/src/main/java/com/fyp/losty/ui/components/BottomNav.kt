package com.fyp.losty.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.fyp.losty.R
import com.fyp.losty.ui.theme.ElectricPink

private data class BottomNavItem(val route: String, val icon: ImageVector?, val label: String, val drawableRes: Int? = null)

@Composable
fun BottomNavigationBar(selectedRoute: String, onItemSelected: (String) -> Unit) {
    val items = listOf(
        BottomNavItem("home", Icons.Filled.Home, "Home"),
        BottomNavItem("chat", Icons.AutoMirrored.Filled.Chat, "Chat"),
        BottomNavItem("add", Icons.Outlined.Add, "Add"),
        BottomNavItem("my_activity", null, "My Activity", R.drawable.outline_inventory_2_24),
        BottomNavItem("profile", Icons.Filled.Person, "Profile")
    )

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { item ->
            val isSelected = item.route == selectedRoute
            val iconScale by animateFloatAsState(targetValue = if (isSelected) 1.18f else 1f, animationSpec = tween(durationMillis = 200))
            NavigationBarItem(
                selected = isSelected,
                onClick = { onItemSelected(item.route) },
                icon = {
                    if (item.icon != null) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) ElectricPink else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                        )
                    } else if (item.drawableRes != null) {
                        Icon(
                            painter = painterResource(id = item.drawableRes),
                            contentDescription = item.label,
                            tint = if (isSelected) ElectricPink else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                        )
                    }
                },
                label = {
                    Text(
                        item.label,
                        color = if (isSelected) ElectricPink else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            )
        }
    }
}
