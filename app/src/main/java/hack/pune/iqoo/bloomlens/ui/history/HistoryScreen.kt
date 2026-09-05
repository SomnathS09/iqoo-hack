package hack.pune.iqoo.bloomlens.ui.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import hack.pune.iqoo.bloomlens.model.SessionRecord
import hack.pune.iqoo.bloomlens.model.StoredChatEntry
import hack.pune.iqoo.bloomlens.ui.common.FullScreenImageViewer
import hack.pune.iqoo.bloomlens.ui.common.rememberBitmapFromPath
import hack.pune.iqoo.bloomlens.ui.theme.HintGreen
import hack.pune.iqoo.bloomlens.ui.theme.HintPurple
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryListScreen(
    sessions: List<SessionRecord>,
    onSelect: (SessionRecord) -> Unit,
    onClose: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Text("✕") }
                Text("Study History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            }

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No sessions yet - solve a problem to start building your history.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sessions) { record -> SessionCard(record, onClick = { onSelect(record) }) }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(record: SessionRecord, onClick: () -> Unit) {
    val levelsCovered = record.messages.mapNotNull { it.bloomLevel }.distinct()
    val thumbnail = rememberBitmapFromPath(record.imagePath)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(end = 12.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.timestamp)),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    record.problemText.take(120),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                if (!record.sessionGoal.isNullOrBlank()) {
                    Text("Goal: ${record.sessionGoal}", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (levelsCovered.isNotEmpty()) {
                        Text(levelsCovered.joinToString(" → "), color = HintPurple, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (record.isComplete) "Completed" else "In progress",
                        color = if (record.isComplete) HintGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(session: SessionRecord, onBack: () -> Unit) {
    val fullImage = rememberBitmapFromPath(session.imagePath)
    var showFullImage by remember { mutableStateOf(false) }
    if (showFullImage && fullImage != null) {
        FullScreenImageViewer(bitmap = fullImage, onDismiss = { showFullImage = false })
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Text("←") }
                Text("Session Detail", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            }

            if (fullImage != null) {
                Image(
                    bitmap = fullImage.asImageBitmap(),
                    contentDescription = "Tap to view full photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clickable { showFullImage = true },
                    contentScale = ContentScale.Crop,
                )
            }

            if (!session.sessionGoal.isNullOrBlank()) {
                Text(
                    "Goal: ${session.sessionGoal}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(session.messages) { entry -> HistoryChatBubble(entry) }
            }
        }
    }
}

@Composable
private fun HistoryChatBubble(entry: StoredChatEntry) {
    val alignment = if (entry.fromTutor) Alignment.Start else Alignment.End
    val containerColor = when {
        !entry.fromTutor -> HintGreen.copy(alpha = 0.25f)
        entry.isDevilsAdvocate -> Color(0xFF7A3B12)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (entry.fromTutor && !entry.bloomLevel.isNullOrBlank()) {
            Text(
                if (entry.isDevilsAdvocate) "😈 Devil's Advocate (${entry.bloomLevel})" else entry.bloomLevel,
                color = HintPurple,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Text(
                entry.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.isDevilsAdvocate) Color.White else Color.Unspecified,
            )
        }
    }
}
