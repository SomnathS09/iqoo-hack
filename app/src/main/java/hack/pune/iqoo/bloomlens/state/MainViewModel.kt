package hack.pune.iqoo.bloomlens.state

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geniex.sdk.bean.ModelPaths
import hack.pune.iqoo.bloomlens.llm.BloomLevel
import hack.pune.iqoo.bloomlens.llm.OnDeviceLlm
import hack.pune.iqoo.bloomlens.llm.PromptBuilder
import hack.pune.iqoo.bloomlens.llm.TutorTurn
import hack.pune.iqoo.bloomlens.model.GenieModelRepository
import hack.pune.iqoo.bloomlens.model.ModelPullEvent
import hack.pune.iqoo.bloomlens.model.PersonaRepository
import hack.pune.iqoo.bloomlens.model.UserPersona
import hack.pune.iqoo.bloomlens.ocr.TextRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val personaRepository: PersonaRepository,
    private val modelRepository: GenieModelRepository,
    private val textRecognizer: TextRecognizer,
    private val llm: OnDeviceLlm,
) : ViewModel() {

    private val _state = MutableStateFlow<AppScreenState>(AppScreenState.Onboarding)
    val state: StateFlow<AppScreenState> = _state.asStateFlow()

    private var persona: UserPersona? = null

    init {
        val existing = personaRepository.getPersona()
        if (existing != null) {
            persona = existing
            prepareModel()
        }
    }

    fun onOnboardingComplete(skillLevel: String, focusArea: String) {
        val newPersona = UserPersona(skillLevel, focusArea)
        persona = newPersona
        personaRepository.savePersona(newPersona)
        prepareModel()
    }

    private fun prepareModel() {
        _state.value = AppScreenState.CheckingModel
        viewModelScope.launch {
            modelRepository.prepareModel().collect { event ->
                when (event) {
                    is ModelPullEvent.AlreadyReady -> initializeLlm(event.paths)
                    is ModelPullEvent.Ready -> initializeLlm(event.paths)
                    is ModelPullEvent.Progress ->
                        _state.value = AppScreenState.Downloading(event.downloadedBytes, event.totalBytes, event.fileName)

                    is ModelPullEvent.Failed -> _state.value = AppScreenState.DownloadFailed(event.message)
                }
            }
        }
    }

    private suspend fun initializeLlm(paths: ModelPaths) {
        llm.initialize(paths)
            .onSuccess { _state.value = AppScreenState.CameraPermissionRequired }
            .onFailure { _state.value = AppScreenState.DownloadFailed(it.message ?: "Failed to initialize the model") }
    }

    fun onRetryDownload() = prepareModel()

    fun onCameraPermissionResult(granted: Boolean) {
        _state.value = if (granted) AppScreenState.Capturing else AppScreenState.CameraPermissionRequired
    }

    fun onShutterPressed(bitmap: Bitmap) {
        val currentPersona = persona ?: return
        _state.value = AppScreenState.ProcessingFrame
        viewModelScope.launch {
            val ocrResult = runCatching { textRecognizer.recognize(bitmap) }.getOrNull()
            if (ocrResult == null || ocrResult.isBlank) {
                _state.value = AppScreenState.ProcessingFailed("No text detected - try again")
                return@launch
            }

            val prompt = PromptBuilder.buildFirstTurnPrompt(ocrResult.rawText, currentPersona)
            llm.generateTutorTurn(prompt)
                .onSuccess { turn ->
                    val level = BloomLevel.fromLabel(turn.bloomLevel) ?: BloomLevel.REMEMBER
                    val opening = ChatEntry(fromTutor = true, text = turn.message, bloomLevel = level.takeIf { turn.recognized })
                    _state.value = AppScreenState.Tutoring(
                        frame = bitmap,
                        session = TutorSession(
                            problemText = ocrResult.rawText,
                            messages = listOf(opening),
                            currentLevel = level,
                            isComplete = turn.isComplete,
                        ),
                        sending = false,
                    )
                }
                .onFailure {
                    _state.value = AppScreenState.ProcessingFailed(it.message ?: "Could not understand the model's answer")
                }
        }
    }

    fun onSendReply(text: String) {
        val current = _state.value
        if (current !is AppScreenState.Tutoring || current.sending || current.session.isComplete) return
        val currentPersona = persona ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val lastTutorMessage = current.session.messages.lastOrNull { it.fromTutor }?.text.orEmpty()
        val withUserReply = current.session.copy(messages = current.session.messages + ChatEntry(fromTutor = false, text = trimmed))
        _state.value = current.copy(session = withUserReply, sending = true)

        viewModelScope.launch {
            val prompt = PromptBuilder.buildFollowUpPrompt(
                problemText = current.session.problemText,
                persona = currentPersona,
                currentLevel = current.session.currentLevel,
                previousQuestion = lastTutorMessage,
                userAnswer = trimmed,
            )
            llm.generateTutorTurn(prompt)
                .onSuccess { turn -> applyTutorTurn(turn) }
                .onFailure { stopSendingWithError() }
        }
    }

    private fun applyTutorTurn(turn: TutorTurn) {
        val latest = _state.value
        if (latest !is AppScreenState.Tutoring) return
        val level = BloomLevel.fromLabel(turn.bloomLevel) ?: latest.session.currentLevel
        val reply = listOfNotNull(turn.feedback.takeIf { it.isNotBlank() }, turn.message.takeIf { it.isNotBlank() })
            .joinToString("\n\n")
        _state.value = latest.copy(
            session = latest.session.copy(
                messages = latest.session.messages + ChatEntry(fromTutor = true, text = reply, bloomLevel = level),
                currentLevel = level,
                isComplete = turn.isComplete,
            ),
            sending = false,
        )
    }

    private fun stopSendingWithError() {
        val latest = _state.value
        if (latest is AppScreenState.Tutoring) {
            _state.value = latest.copy(
                session = latest.session.copy(
                    messages = latest.session.messages + ChatEntry(
                        fromTutor = true,
                        text = "Sorry, I had trouble responding to that - could you try rephrasing?",
                    ),
                ),
                sending = false,
            )
        }
    }

    fun onNewProblem() {
        _state.value = AppScreenState.Capturing
    }

    override fun onCleared() {
        llm.release()
        super.onCleared()
    }
}
