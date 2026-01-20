package com.fyp.losty.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fyp.losty.AppViewModel
import com.fyp.losty.Notification
import com.fyp.losty.NotificationState
import com.fyp.losty.ui.components.BackButton
import com.fyp.losty.ui.theme.ElectricPink
import com.fyp.losty.ui.theme.SafetyTeal
import com.fyp.losty.ui.theme.UrgentRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    navController: NavController,
    appViewModel: AppViewModel = viewModel()
) {
    val notificationState by appViewModel.notificationState.collectAsState()

    LaunchedEffect(Unit) {
        appViewModel.listenForAllNotifications()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(navController = navController) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = notificationState) {
                is NotificationState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ElectricPink)
                }
                is NotificationState.Success -> {
                    if (state.notifications.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No notifications yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 18.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.notifications) { notification ->
                                NotificationItem(
                                    notification = notification,
                                    onClick = {
                                        appViewModel.markNotificationAsRead(notification.id)
                                        // Navigate based on type
                                        when (notification.type) {
                                            "CLAIM", "CLAIM_APPROVED", "CLAIM_REJECTED", "FOUND_REPORT" -> {
                                                navController.navigate("my_activity")
                                            }
                                            "MESSAGE" -> {
                                                if (notification.conversationId.isNotBlank()) {
                                                    navController.navigate("chat/${notification.conversationId}")
                                                } else {
                                                    navController.navigate("conversations")
                                                }
                                            }
                                            "MATCH_ALERT" -> {
                                                navController.navigate("main")
                                            }
                                            else -> {}
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                is NotificationState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: Notification,
    onClick: () -> Unit
) {
    val icon = when (notification.type) {
        "MESSAGE" -> Icons.AutoMirrored.Filled.Chat
        "CLAIM", "FOUND_REPORT" -> Icons.Default.Info
        "CLAIM_APPROVED" -> Icons.Default.CheckCircle
        "CLAIM_REJECTED" -> Icons.Default.Warning
        else -> Icons.Default.Notifications
    }

    val iconColor = when (notification.type) {
        "MESSAGE" -> ElectricPink
        "CLAIM", "FOUND_REPORT" -> Color(0xFF2196F3) // Blue
        "CLAIM_APPROVED" -> SafetyTeal
        "CLAIM_REJECTED" -> UrgentRed
        else -> ElectricPink
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.fromUserName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.message,
                    fontSize = 14.sp,
                    color = if (notification.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatTimestamp(notification.timestamp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ElectricPink)
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
