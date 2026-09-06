package hack.pune.iqoo.bloomlens.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hack.pune.iqoo.bloomlens.state.AppScreenState
import hack.pune.iqoo.bloomlens.state.HistoryViewState
import hack.pune.iqoo.bloomlens.state.MainViewModel
import hack.pune.iqoo.bloomlens.ui.capture.CaptureScreen
import hack.pune.iqoo.bloomlens.ui.download.DownloadScreen
import hack.pune.iqoo.bloomlens.ui.history.HistoryDetailScreen
import hack.pune.iqoo.bloomlens.ui.history.HistoryListScreen
import hack.pune.iqoo.bloomlens.ui.onboarding.OnboardingScreen
import hack.pune.iqoo.bloomlens.ui.provider.ProviderModeScreen
import hack.pune.iqoo.bloomlens.ui.rewards.RewardsDialog
import hack.pune.iqoo.bloomlens.ui.tutor.TutorScreen

@Composable
fun BloomLensRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val flashcards by viewModel.flashcards.collectAsStateWithLifecycle()
    val historyView by viewModel.historyView.collectAsStateWithLifecycle()
    val stars by viewModel.stars.collectAsStateWithLifecycle()
    val showRewards by viewModel.showRewards.collectAsStateWithLifecycle()
    var showProviderMode by remember { mutableStateOf(false) }

    if (showRewards) {
        RewardsDialog(stars = stars, onRedeem = viewModel::onRedeemReward, onDismiss = viewModel::onCloseRewards)
    }

    // Both History and Provider Mode are plain conditionally-shown composables, not real
    // navigation entries, so the system back button needs to be told about them explicitly -
    // otherwise it falls through to the default "no back stack" behavior and exits the app.
    BackHandler(enabled = historyView != null) {
        if (historyView is HistoryViewState.DetailView) {
            viewModel.onBackFromHistoryDetail()
        } else {
            viewModel.onCloseHistory()
        }
    }
    BackHandler(enabled = showProviderMode) { showProviderMode = false }

    if (showProviderMode) {
        ProviderModeScreen(onClose = { showProviderMode = false })
        return
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val history = historyView) {
            is HistoryViewState.ListView -> HistoryListScreen(
                sessions = history.sessions,
                onSelect = viewModel::onSelectHistorySession,
                onClose = viewModel::onCloseHistory,
            )

            is HistoryViewState.DetailView -> HistoryDetailScreen(
                session = history.session,
                onBack = viewModel::onBackFromHistoryDetail,
                onContinue = viewModel::onContinueSession,
            )

            null -> when (val current = state) {
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
                -> CaptureScreen(
                    state = current,
                    viewModel = viewModel,
                    onOpenHistory = viewModel::onOpenHistory,
                    onOpenProviderMode = { showProviderMode = true },
                    stars = stars,
                    onOpenRewards = viewModel::onOpenRewards,
                )

                is AppScreenState.Tutoring -> TutorScreen(
                    frame = current.frame,
                    session = current.session,
                    sending = current.sending,
                    flashcardsState = flashcards,
                    onSendReply = viewModel::onSendReply,
                    onRequestFlashcards = viewModel::onRequestFlashcards,
                    onDismissFlashcards = viewModel::onDismissFlashcards,
                    onOpenHistory = viewModel::onOpenHistory,
                    onNewProblem = viewModel::onNewProblem,
                    stars = stars,
                    onOpenRewards = viewModel::onOpenRewards,
                )
            }
        }
    }
}
