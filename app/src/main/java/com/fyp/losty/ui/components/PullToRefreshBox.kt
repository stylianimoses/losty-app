package com.fyp.losty.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // Just show content; pull-to-refresh gesture is implemented by the screen that wraps this box.
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }

        if (isRefreshing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
