package hack.pune.iqoo.bloomlens.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import hack.pune.iqoo.bloomlens.camera.CameraController
import hack.pune.iqoo.bloomlens.state.AppScreenState
import hack.pune.iqoo.bloomlens.state.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(state: AppScreenState, viewModel: MainViewModel, onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        viewModel.onCameraPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        if (hasCameraPermission) {
            viewModel.onCameraPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val cameraController = remember { CameraController(context) }
    DisposableEffect(Unit) {
        onDispose { cameraController.unbind() }
    }

    // SRL "Forethought": ask for a quick session goal once per new problem. CaptureScreen is
    // recreated each time we return here from Tutoring, so this naturally re-prompts per problem.
    var showGoalDialog by remember { mutableStateOf(true) }
    var goalInput by remember { mutableStateOf("") }
    if (showGoalDialog && state == AppScreenState.Capturing) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("What's your goal?") },
            text = {
                Column {
                    Text(
                        "Setting a quick goal helps you stay focused (optional).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        placeholder = { Text("e.g. Fully understand this problem") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onSessionGoalSet(goalInput)
                    showGoalDialog = false
                }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text("Skip") }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        scope.launch { cameraController.bind(lifecycleOwner, previewView) }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Only tappable while actually ready to shoot - otherwise it sits hidden behind
            // the processing spinner or hint cards, which also occupy the bottom of the screen.
            if (state == AppScreenState.Capturing) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val bitmap = runCatching { cameraController.captureBitmap() }.getOrNull()
                            if (bitmap != null) viewModel.onShutterPressed(bitmap)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                ) {
                    Text("●")
                }
            }

            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text("📜")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Camera permission is needed to capture problems.", style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Grant permission")
                }
            }
        }

        when (state) {
            AppScreenState.ProcessingFrame -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is AppScreenState.ProcessingFailed -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.message, color = Color.White)
                    Button(onClick = viewModel::onNewProblem, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Try again")
                    }
                }
            }

            else -> Unit
        }
    }
}