package hack.pune.iqoo.bloomlens.ui.tutor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hack.pune.iqoo.bloomlens.state.ChatEntry
import hack.pune.iqoo.bloomlens.state.TutorSession
import hack.pune.iqoo.bloomlens.ui.theme.HintGreen
import hack.pune.iqoo.bloomlens.ui.theme.HintPurple
import hack.pune.iqoo.bloomlens.voice.SpeechRecognizerManager
import hack.pune.iqoo.bloomlens.voice.TextToSpeechManager

@Composable
fun TutorScreen(
    frame: Bitmap?,
    session: TutorSession,
    sending: Boolean,
    onSendReply: (String) -> Unit,
    onNewProblem: () -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        frame?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentScale = ContentScale.Crop,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { speechEnabled = !speechEnabled }) {
                Text(if (speechEnabled) "🔊" else "🔇")
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

@Composable
private fun ChatBubble(entry: ChatEntry) {
    val alignment = if (entry.fromTutor) Alignment.Start else Alignment.End
    val containerColor = if (entry.fromTutor) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        HintGreen.copy(alpha = 0.25f)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (entry.fromTutor && entry.bloomLevel != null) {
            Text(
                entry.bloomLevel.label,
                color = HintPurple,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Text(entry.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
