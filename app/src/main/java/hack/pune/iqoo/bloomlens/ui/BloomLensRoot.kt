package hack.pune.iqoo.bloomlens.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hack.pune.iqoo.bloomlens.state.AppScreenState
import hack.pune.iqoo.bloomlens.state.MainViewModel
import hack.pune.iqoo.bloomlens.ui.capture.CaptureScreen
import hack.pune.iqoo.bloomlens.ui.download.DownloadScreen
import hack.pune.iqoo.bloomlens.ui.onboarding.OnboardingScreen
import hack.pune.iqoo.bloomlens.ui.tutor.TutorScreen

@Composable
fun BloomLensRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            AppScreenState.Onboarding -> OnboardingScreen(onComplete = viewModel::onOnboardingComplete)

            AppScreenState.CheckingModel -> DownloadScreen(
                downloadedBytes = 0L,
                totalBytes = 0L,
                message = "Checking for the on-device model…",
                onRetry = null,
            )

            is AppScreenState.Downloading -> DownloadScreen(
                downloadedBytes = current.downloadedBytes,
                totalBytes = current.totalBytes,
                message = if (current.fileName.isBlank()) "Downloading model…" else "Downloading ${current.fileName}…",
                onRetry = null,
            )

            is AppScreenState.DownloadFailed -> DownloadScreen(
                downloadedBytes = 0L,
                totalBytes = 0L,
                message = current.message,
                onRetry = viewModel::onRetryDownload,
            )

            AppScreenState.CameraPermissionRequired,
            AppScreenState.Capturing,
            AppScreenState.ProcessingFrame,
            is AppScreenState.ProcessingFailed,
            -> CaptureScreen(state = current, viewModel = viewModel)

            is AppScreenState.Tutoring -> TutorScreen(
                frame = current.frame,
                session = current.session,
                sending = current.sending,
                onSendReply = viewModel::onSendReply,
                onNewProblem = viewModel::onNewProblem,
            )
        }
    }
}
