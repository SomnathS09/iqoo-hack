package hack.pune.iqoo.bloomlens.state

import android.graphics.Bitmap
import hack.pune.iqoo.bloomlens.llm.BloomLevel

data class ChatEntry(
    val fromTutor: Boolean,
    val text: String,
    val bloomLevel: BloomLevel? = null,
)

data class TutorSession(
    val problemText: String,
    val messages: List<ChatEntry>,
    val currentLevel: BloomLevel,
    val isComplete: Boolean,
)

sealed interface AppScreenState {
    data object Onboarding : AppScreenState
    data object CheckingModel : AppScreenState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long, val fileName: String) : AppScreenState
    data class DownloadFailed(val message: String) : AppScreenState
    data object CameraPermissionRequired : AppScreenState
    data object Capturing : AppScreenState
    data object ProcessingFrame : AppScreenState
    data class Tutoring(val frame: Bitmap?, val session: TutorSession, val sending: Boolean) : AppScreenState
    data class ProcessingFailed(val message: String) : AppScreenState
}
