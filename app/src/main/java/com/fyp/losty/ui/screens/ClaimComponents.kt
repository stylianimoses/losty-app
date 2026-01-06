package com.fyp.losty.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.fyp.losty.Claim
import com.fyp.losty.ui.theme.TextBlack

@Composable
fun ClaimApprovalCard(claim: Claim, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Post: ${claim.postTitle}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextBlack
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Status: Pending Approval",
                color = Color(0xFFFF9800), // Orange for pending
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50) // Green
                    )
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Approve")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Reject")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject")
                }
            }
        }
    }
}

@Composable
fun OwnerClaimCard(claim: Claim) {
    val statusColor = when (claim.status) {
        "approved" -> Color(0xFF4CAF50) // Green
        "denied" -> Color.Red
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Post: ${claim.postTitle}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextBlack
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Status: ${claim.status.replaceFirstChar { it.uppercase() }}",
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ClaimCard(claim: Claim) {
    val statusColor = when (claim.status) {
        "approved" -> Color(0xFF4CAF50) // Green
        "denied" -> Color.Red
        "pending" -> Color(0xFFFF9800) // Orange
        else -> Color.Gray
    }

    val statusText = when (claim.status) {
        "approved" -> "Approved"
        "denied" -> "Denied"
        "pending" -> "Pending"
        else -> claim.status.replaceFirstChar { it.uppercase() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = claim.postTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextBlack)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Status: $statusText",
                color = statusColor,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        }
    }
}
