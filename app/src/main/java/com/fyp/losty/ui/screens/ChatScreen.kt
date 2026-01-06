package com.fyp.losty.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.fyp.losty.AppViewModel
import com.fyp.losty.Conversation
import com.fyp.losty.Message
import com.fyp.losty.MessagesState
import com.fyp.losty.ConversationsState
import com.fyp.losty.ui.components.BackToHomeButton
import com.fyp.losty.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.pullrefresh.PullRefreshIndicator

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    navController: NavController,
    appViewModel: AppViewModel = viewModel()
) {
    val messagesState by appViewModel.messagesState.collectAsState()
    val conversationsState by appViewModel.conversationsState.collectAsState()
    val userProfile by appViewModel.userProfile.collectAsState()
    val currentUserId = userProfile.uid

    // --- NEW FUNCTION: Identify Other User for Header ---
    val conversation = (conversationsState as? ConversationsState.Success)?.conversations?.find { it.id == conversationId }
    val otherUserName = when {
        conversation == null -> "Chat"
        conversation.participant1Id == currentUserId -> conversation.participant2Name
        else -> conversation.participant1Name
    }
    // Placeholder for avatar logic
    val otherUserPhotoUrl: String? = null

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(conversationId) {
        appViewModel.loadMessagesOnce(conversationId)
    }

    LaunchedEffect(messagesState) {
        if (messagesState is MessagesState.Success) {
            val messages = (messagesState as MessagesState.Success).messages
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Add pull-to-refresh state
    val isRefreshing = messagesState is MessagesState.Loading
    val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = { appViewModel.loadMessagesOnce(conversationId) })

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                // --- MODIFIED: Header now shows Avatar + Name ---
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color(0xFFE0E0E0)
                        ) {
                            if (otherUserPhotoUrl != null) {
                                AsyncImage(model = otherUserPhotoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(otherUserName.take(1).uppercase(), color = TextGrey, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = otherUserName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextBlack)
                            // Added "Active" status in Pink
                            Text(text = "Active now", fontSize = 12.sp, color = ElectricPink)
                        }
                    }
                },
                navigationIcon = { BackToHomeButton(navController = navController) },
                actions = {
                    IconButton(onClick = { /* Options */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextBlack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OffWhite)
            )
        },
        containerColor = OffWhite,
        bottomBar = {
            // --- NEW FUNCTION: Pill-Shaped Input Bar ---
            ChatInputBar(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSendClick = {
                    appViewModel.sendMessage(conversationId, messageText.trim())
                    messageText = ""
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            when (val state = messagesState) {
                is MessagesState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ElectricPink)
                is MessagesState.Success -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp), // Increased spacing
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.messages) { message ->
                            MessageBubble(
                                message = message,
                                isCurrentUser = message.senderId == currentUserId
                            )
                        }
                    }
                }
                is MessagesState.Error -> Text("Error: ${state.message}", modifier = Modifier.align(Alignment.Center), color = Color.Red)
            }

            PullRefreshIndicator(
                isRefreshing,
                pullRefreshState,
                Modifier.align(Alignment.TopCenter),
                backgroundColor = Color.White,
                contentColor = ElectricPink
            )
        }
    }
}

@Composable
fun ChatInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pill Shape Input
            BasicTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(fontSize = 16.sp, color = TextBlack),
                cursorBrush = SolidColor(ElectricPink), // Maintained Brand Color
                decorationBox = { innerTextField ->
                    if (messageText.isEmpty()) {
                        Text("Type a message...", color = TextGrey, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Round Send Button
            val isSendEnabled = messageText.isNotBlank()
            IconButton(
                onClick = onSendClick,
                enabled = isSendEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSendEnabled) ElectricPink else Color.LightGray) // Brand Color
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean) {
    // Maintained Original Theme Colors (Pink/Grey) instead of Blue
    val bubbleColor = if (isCurrentUser) ElectricPink else Color(0xFFE0E0E0)
    val textColor = if (isCurrentUser) Color.White else TextBlack

    // --- NEW FUNCTION: Tail Shapes ---
    val bubbleShape = if (isCurrentUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 2.dp, bottomEnd = 18.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Bubble
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            modifier = Modifier.widthIn(max = 280.dp),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 16.sp
                )
            }
        }

        // Time Stamp outside
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
            fontSize = 10.sp,
            color = TextGrey,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}