package com.bigwizard.aria.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigwizard.aria.data.model.Message
import com.bigwizard.aria.ui.theme.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import java.text.DateFormat
import java.util.Date

/**
 * MessageBubble — Chat-style message display for conversation history.
 */
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val isError = message.isError
    val time = formatTimestamp(message.timestamp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // TalkBack reads one sentence per bubble instead of "A", text, "2m ago" separately.
            .clearAndSetSemantics {
                contentDescription = "${if (isUser) "You" else "Aria"}: ${message.content}, $time"
            },
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Aria avatar dot
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(AriaViolet, AriaCyanDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    color = Color.White,
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Bubble
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart    = if (isUser) 18.dp else 4.dp,
                            topEnd      = if (isUser) 4.dp else 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd   = 18.dp
                        )
                    )
                    .background(
                        color = when {
                            isError -> AriaError.copy(alpha = 0.15f)
                            isUser  -> AriaViolet
                            else    -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 280.dp)
            ) {
                Text(
                    text  = message.content,
                    color = when {
                        isError -> AriaError
                        isUser  -> Color.White
                        else    -> MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (isError) FontStyle.Italic else FontStyle.Normal
                )
            }

            // Timestamp
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text  = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // User avatar dot
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "U",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000  -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        // DateFormat honours the user's 12/24-hour preference and locale.
        diff < 86400_000 -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
        else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
    }
}
