package hack.pune.iqoo.bloomlens.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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

    // The tutor chat is reached from the camera screen but isn't a real back-stack entry
    // either, so back would otherwise exit the app instead of returning to the camera. Reuses
    // the same onNewProblem() reset already wired to the "Scan a New Problem"/"Try again"
    // buttons, rather than adding a second way to unwind this state - the in-progress session
    // is already persisted to History on every turn, so nothing is lost, and no new Bitmap or
    // ViewModel state is retained: onNewProblem() drops the Tutoring state (and its frame
    // Bitmap) from the single StateFlow that holds it, leaving it unreferenced for GC exactly
    // as it already does for those buttons today.
    BackHandler(enabled = historyView == null && !showProviderMode && state is AppScreenState.Tutoring) {
        viewModel.onNewProblem()
    }

    // Applied once here rather than per-screen: on Android 15+ (and this app's edge-to-edge
    // layout generally) content draws behind the system navigation bar by default, so
    // bottom-anchored controls (the shutter FAB, send button, etc.) end up under/behind it
    // without this.
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        if (showProviderMode) {
            ProviderModeScreen(onClose = { showProviderMode = false })
        } else {
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
    }
}
