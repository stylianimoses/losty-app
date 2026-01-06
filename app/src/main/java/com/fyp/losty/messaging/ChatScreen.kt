package com.fyp.losty.messaging

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.fyp.losty.Message
import com.fyp.losty.ui.components.PullToRefreshBox

@Composable
fun ChatScreen(conversationId: String, repo: MessagingRepository = MessagingRepository()) {
    val coroutineScope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }

    // initial one-shot load
    LaunchedEffect(conversationId) {
        isRefreshing = true
        messages = repo.fetchMessagesOnce(conversationId)
        isRefreshing = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = isRefreshing, modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(messages) { msg ->
                    Text(text = "${msg.senderId}: ${msg.text}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        Row(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val m = Message(id = "", conversationId = conversationId, senderId = "me", senderName = "Me", text = text, timestamp = System.currentTimeMillis(), read = false)
                coroutineScope.launch { repo.sendMessageSuspend(conversationId, m) }
                text = ""
            }) {
                Text("Send")
            }
        }
    }
}
