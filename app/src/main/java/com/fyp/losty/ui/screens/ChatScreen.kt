package com.fyp.losty.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fyp.losty.AppViewModel
import com.fyp.losty.ConversationsState
import com.fyp.losty.Message
import com.fyp.losty.MessagesState
import com.fyp.losty.R
import com.fyp.losty.ui.components.BackButton
import com.fyp.losty.ui.theme.ElectricPink
import com.fyp.losty.ui.theme.OffWhite
import com.fyp.losty.ui.theme.TextBlack
import com.fyp.losty.ui.theme.TextGrey
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    navController: NavController,
    appViewModel: AppViewModel = viewModel(),
    otherUserNameArg: String? = null
) {
    val messagesState by appViewModel.messagesState.collectAsState()
    val conversationsState by appViewModel.conversationsState.collectAsState()
    val userProfile by appViewModel.userProfile.collectAsState()
    val currentUserId = userProfile.uid

    var isRefreshing by remember { mutableStateOf(false) } // Local refreshing state

    val conversation = (conversationsState as? ConversationsState.Success)?.conversations?.find { it.id == conversationId }
    val resolvedOtherName = otherUserNameArg ?: when {
        conversation == null -> "User"
        conversation.participant1Id == currentUserId -> conversation.participant2Name
        else -> conversation.participant1Name
    }

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    fun refresh() = coroutineScope.launch {
        isRefreshing = true
        appViewModel.loadMessages(conversationId, isRefresh = true)
        isRefreshing = false
    }

    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = ::refresh)

    LaunchedEffect(conversationId) {
        appViewModel.loadMessages(conversationId)
    }

    LaunchedEffect(messagesState) {
        if (messagesState is MessagesState.Success) {
            val messages = (messagesState as MessagesState.Success).messages
            if (messages.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    var optionsExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(navController = navController) },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color(0xFFE0E0E0)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(resolvedOtherName.take(1).uppercase(), color = TextGrey, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = resolvedOtherName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextBlack)
                            // TODO: Add user status here (e.g., "online")
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { optionsExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextBlack)
                        }
                        DropdownMenu(
                            expanded = optionsExpanded,
                            onDismissRequest = { optionsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.outline_person_alert_24),
                                            contentDescription = "Report",
                                            tint = TextBlack
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Report User")
                                    }
                                },
                                onClick = {
                                    optionsExpanded = false
                                    // TODO: Implement Report flow
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OffWhite)
            )
        },
        containerColor = OffWhite,
        bottomBar = {
            ChatInputBar(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSendClick = {
                    if (messageText.isNotBlank()) {
                        appViewModel.sendMessage(conversationId, messageText.trim())
                        messageText = ""
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            when (val state = messagesState) {
                is MessagesState.Loading -> {
                    if (!isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ElectricPink)
                    }
                }
                is MessagesState.Success -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
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
            BasicTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(fontSize = 16.sp, color = TextBlack),
                cursorBrush = SolidColor(ElectricPink),
                decorationBox = { innerTextField ->
                    if (messageText.isEmpty()) {
                        Text("Type a message...", color = TextGrey, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            val isSendEnabled = messageText.isNotBlank()
            IconButton(
                onClick = onSendClick,
                enabled = isSendEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSendEnabled) ElectricPink else Color.LightGray)
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
    val bubbleColor = if (isCurrentUser) ElectricPink else Color(0xFFE0E0E0)
    val textColor = if (isCurrentUser) Color.White else TextBlack

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

        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
            fontSize = 10.sp,
            color = TextGrey,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
