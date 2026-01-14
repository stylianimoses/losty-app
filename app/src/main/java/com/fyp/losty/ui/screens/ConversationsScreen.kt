package com.fyp.losty.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.fyp.losty.AppViewModel
import com.fyp.losty.Conversation
import com.fyp.losty.ConversationsState
import com.fyp.losty.ui.components.BackButton
import com.fyp.losty.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun ConversationsScreen(
    navController: NavController,
    appViewModel: AppViewModel = viewModel()
) {
    val conversationsState by appViewModel.conversationsState.collectAsState()
    val userProfile by appViewModel.userProfile.collectAsState()
    val currentUserId = userProfile.uid

    LaunchedEffect(Unit) {
        appViewModel.loadConversations()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Messages", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = { BackButton(navController = navController) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val isRefreshing = conversationsState is ConversationsState.Loading
        val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = { appViewModel.loadConversations() })

        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search chats...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    }
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (val state = conversationsState) {
                        is ConversationsState.Loading -> CircularProgressIndicator(color = ElectricPink)
                        is ConversationsState.Success -> {
                            if (state.conversations.isEmpty()) {
                                Text("No messages yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.conversations) { conversation ->
                                        ConversationCard(
                                            conversation = conversation,
                                            currentUserId = currentUserId,
                                            onClick = { navController.navigate("chat/${conversation.id}") }
                                        )
                                    }
                                }
                            }
                        }
                        is ConversationsState.Error -> Text("Error: ${state.message}", color = Color.Red)
                    }
                }
            }

            PullRefreshIndicator(isRefreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
fun ConversationCard(
    conversation: Conversation,
    currentUserId: String,
    onClick: () -> Unit
) {
    val otherUserName = if (currentUserId == conversation.participant1Id) conversation.participant2Name else conversation.participant1Name
    val isUnread = conversation.unreadCount > 0

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (conversation.postImageUrl.isNotEmpty()) {
                    AsyncImage(model = conversation.postImageUrl, contentDescription = null, contentScale = ContentScale.Crop)
                } else {
                    Box(contentAlignment = Alignment.Center) { Text("Img", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = otherUserName,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.Bold
                    )
                    Text(
                        text = formatTime(conversation.lastMessageTime),
                        fontSize = 12.sp,
                        color = if (isUnread) ElectricPink else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.lastMessage.ifEmpty { "Photo sent" },
                        fontSize = 14.sp,
                        color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(ElectricPink)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Now"
        diff < 3600000 -> "${diff / 60000}m"
        diff < 86400000 -> "${diff / 3600000}h"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}
