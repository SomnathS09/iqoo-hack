package hack.pune.iqoo.bloomlens.state

import android.graphics.Bitmap
import hack.pune.iqoo.bloomlens.llm.BloomLevel

data class ChatEntry(
    val fromTutor: Boolean,
    val text: String,
    val bloomLevel: BloomLevel? = null,
    val isDevilsAdvocate: Boolean = false,
)

data class TutorSession(
    val problemText: String,
    val messages: List<ChatEntry>,
    val currentLevel: BloomLevel,
    val isComplete: Boolean,
    /** Consecutive follow-up turns spent at [currentLevel] without advancing - see PromptBuilder.FORCE_ADVANCE_AFTER_TURNS. */
    val turnsAtCurrentLevel: Int = 0,
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
