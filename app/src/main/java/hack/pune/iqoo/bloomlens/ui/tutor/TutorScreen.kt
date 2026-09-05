package hack.pune.iqoo.bloomlens.ui.tutor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hack.pune.iqoo.bloomlens.state.ChatEntry
import hack.pune.iqoo.bloomlens.state.FlashcardsUiState
import hack.pune.iqoo.bloomlens.state.TutorSession
import hack.pune.iqoo.bloomlens.ui.common.FullScreenImageViewer
import hack.pune.iqoo.bloomlens.ui.theme.HintGreen
import hack.pune.iqoo.bloomlens.ui.theme.HintPurple
import hack.pune.iqoo.bloomlens.voice.SpeechRecognizerManager
import hack.pune.iqoo.bloomlens.voice.TextToSpeechManager
import kotlinx.coroutines.delay

private const val IDLE_NUDGE_DELAY_MS = 45_000L
private val DEVILS_ADVOCATE_COLOR = Color(0xFF7A3B12)

@Composable
fun TutorScreen(
    frame: Bitmap?,
    session: TutorSession,
    sending: Boolean,
    flashcardsState: FlashcardsUiState?,
    onSendReply: (String) -> Unit,
    onRequestFlashcards: () -> Unit,
    onDismissFlashcards: () -> Unit,
    onOpenHistory: () -> Unit,
    onNewProblem: () -> Unit,
    stars: Int,
    onOpenRewards: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var speechEnabled by remember { mutableStateOf(true) }
    val tts = remember { TextToSpeechManager(context) }
    DisposableEffect(Unit) { onDispose { tts.release() } }

    LaunchedEffect(session.messages.size) {
        val last = session.messages.lastOrNull()
        if (speechEnabled && last != null && last.fromTutor) {
            tts.speak(last.text)
        }
    }
    LaunchedEffect(speechEnabled) {
        if (!speechEnabled) tts.stop()
    }

    // SRL "Self-Reflection": nudge the learner if they've been sitting on a question a while.
    var showIdleNudge by remember { mutableStateOf(false) }
    LaunchedEffect(session.messages.size, sending, session.isComplete) {
        showIdleNudge = false
        if (!sending && !session.isComplete && session.messages.lastOrNull()?.fromTutor == true) {
            delay(IDLE_NUDGE_DELAY_MS)
            showIdleNudge = true
        }
    }

    var isListening by remember { mutableStateOf(false) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val speechRecognizer = remember { SpeechRecognizerManager(context) }
    DisposableEffect(Unit) { onDispose { speechRecognizer.stopListening() } }

    fun startListening() {
        isListening = true
        tts.stop()
        speechRecognizer.startListening(
            onRecognized = { text ->
                input = text
                isListening = false
            },
            onFailure = { isListening = false },
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (granted) startListening()
    }

    LaunchedEffect(session.messages.size, sending) {
        if (session.messages.isNotEmpty()) {
            listState.animateScrollToItem(session.messages.lastIndex + if (sending) 1 else 0)
        }
    }

    if (flashcardsState != null) {
        FlashcardsDialog(state = flashcardsState, onDismiss = onDismissFlashcards)
    }

    var showFullImage by remember { mutableStateOf(false) }
    if (showFullImage && frame != null) {
        FullScreenImageViewer(bitmap = frame, onDismiss = { showFullImage = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        frame?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Tap to view full photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clickable { showFullImage = true },
                contentScale = ContentScale.Crop,
            )
        }

        BloomProgressBar(currentLevel = session.currentLevel, isComplete = session.isComplete)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                IconButton(onClick = onOpenHistory) { Text("📜") }
                TextButton(onClick = onRequestFlashcards, enabled = !sending) {
                    Text("📇 Flashcards")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenRewards) { Text("⭐ $stars") }
                IconButton(onClick = { speechEnabled = !speechEnabled }) {
                    Text(if (speechEnabled) "🔊" else "🔇")
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(session.messages) { entry -> ChatBubble(entry) }
            if (sending) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BloomLens is thinking...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (session.isComplete) {
                item { SessionSummaryCard(session) }
            }
        }

        if (session.isComplete) {
            Button(
                onClick = onNewProblem,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Scan a New Problem")
            }
        } else {
            Column {
                if (showIdleNudge) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Stuck on this one?", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            showIdleNudge = false
                            onSendReply("I'm stuck - can you give me a hint or break this down further?")
                        }) {
                            Text("Get a hint")
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (isListening) "Listening..." else "Type or speak your answer...") },
                        enabled = !sending,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (hasAudioPermission) startListening() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        enabled = !sending && !isListening,
                    ) {
                        Text(if (isListening) "🔴" else "🎤")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = {
                            onSendReply(input)
                            input = ""
                        },
                        enabled = !sending && input.isNotBlank(),
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(entry: ChatEntry) {
    val alignment = if (entry.fromTutor) Alignment.Start else Alignment.End
    val containerColor = when {
        !entry.fromTutor -> HintGreen.copy(alpha = 0.25f)
        entry.isDevilsAdvocate -> DEVILS_ADVOCATE_COLOR
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (entry.fromTutor && entry.bloomLevel != null) {
            Text(
                if (entry.isDevilsAdvocate) "😈 Devil's Advocate (${entry.bloomLevel.label})" else entry.bloomLevel.label,
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

@Composable
private fun SessionSummaryCard(session: TutorSession) {
    val levelsCovered = session.messages.mapNotNull { it.bloomLevel }.distinct()
    val answerCount = session.messages.count { !it.fromTutor }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Session Summary", style = MaterialTheme.typography.titleMedium, color = HintGreen)
            Text("Levels covered: ${levelsCovered.joinToString(" → ") { it.label }}", style = MaterialTheme.typography.bodySmall)
            Text("Questions answered: $answerCount", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FlashcardsDialog(state: FlashcardsUiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Flashcards") },
        text = {
            when {
                state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating flashcards...")
                }

                state.error != null -> Text(state.error)

                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.cards.forEach { card ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(card.front, style = MaterialTheme.typography.titleMedium)
                                Text(card.back, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
