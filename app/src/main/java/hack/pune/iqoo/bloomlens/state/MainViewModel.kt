package hack.pune.iqoo.bloomlens.state

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geniex.sdk.bean.ModelPaths
import hack.pune.iqoo.bloomlens.llm.BloomLevel
import hack.pune.iqoo.bloomlens.llm.Flashcard
import hack.pune.iqoo.bloomlens.llm.OnDeviceLlm
import hack.pune.iqoo.bloomlens.llm.PromptBuilder
import hack.pune.iqoo.bloomlens.llm.TutorTurn
import hack.pune.iqoo.bloomlens.model.GenieModelRepository
import hack.pune.iqoo.bloomlens.model.HistoryRepository
import hack.pune.iqoo.bloomlens.model.ImageStorage
import hack.pune.iqoo.bloomlens.model.ModelPullEvent
import hack.pune.iqoo.bloomlens.model.PersonaRepository
import hack.pune.iqoo.bloomlens.model.Reward
import hack.pune.iqoo.bloomlens.model.RewardsRepository
import hack.pune.iqoo.bloomlens.model.SessionRecord
import hack.pune.iqoo.bloomlens.model.StoredChatEntry
import hack.pune.iqoo.bloomlens.model.UserPersona
import hack.pune.iqoo.bloomlens.ocr.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val personaRepository: PersonaRepository,
    private val modelRepository: GenieModelRepository,
    private val historyRepository: HistoryRepository,
    private val imageStorage: ImageStorage,
    private val textRecognizer: TextRecognizer,
    private val llm: OnDeviceLlm,
    private val rewardsRepository: RewardsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AppScreenState>(AppScreenState.Onboarding)
    val state: StateFlow<AppScreenState> = _state.asStateFlow()

    /** DOK-1 flashcards, shown as a dialog over the current screen when non-null. */
    private val _flashcards = MutableStateFlow<FlashcardsUiState?>(null)
    val flashcards: StateFlow<FlashcardsUiState?> = _flashcards.asStateFlow()

    /** Study log overlay - independent of [state], shown on top of whatever screen is active. */
    private val _historyView = MutableStateFlow<HistoryViewState?>(null)
    val historyView: StateFlow<HistoryViewState?> = _historyView.asStateFlow()

    /** Stars earned by completing a full Bloom's Taxonomy journey - redeemable for rewards. */
    private val _stars = MutableStateFlow(rewardsRepository.getStars())
    val stars: StateFlow<Int> = _stars.asStateFlow()

    private val _showRewards = MutableStateFlow(false)
    val showRewards: StateFlow<Boolean> = _showRewards.asStateFlow()

    private var persona: UserPersona? = null

    /** SRL "Forethought": the learner's stated goal for the current problem, if any. */
    private var sessionGoal: String? = null

    /** Stable id for the in-progress session, so history updates overwrite rather than duplicate. */
    private var currentSessionId: String? = null

    /** Path to the current problem photo, persisted to disk so history survives app restarts. */
    private var currentImagePath: String? = null

    /** Whether the tutor recognized the current problem photo as an actual problem. */
    private var currentRecognized: Boolean = true

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

    /** SRL "Forethought": called once per new problem, before capture, from the goal-setting prompt. */
    fun onSessionGoalSet(goal: String) {
        sessionGoal = goal.trim().takeIf { it.isNotBlank() }
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

            val prompt = PromptBuilder.buildFirstTurnPrompt(ocrResult.rawText, currentPersona, sessionGoal)
            llm.generateTutorTurn(prompt)
                .onSuccess { turn ->
                    val level = BloomLevel.fromLabel(turn.bloomLevel) ?: BloomLevel.REMEMBER
                    val opening = ChatEntry(fromTutor = true, text = turn.displayMessage, bloomLevel = level.takeIf { turn.recognized })
                    val sessionId = System.currentTimeMillis().toString()
                    currentSessionId = sessionId
                    currentImagePath = withContext(Dispatchers.IO) { imageStorage.save(bitmap, sessionId) }
                    currentRecognized = turn.recognized
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
                    persistCurrentSession()
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

    /** DOK-1: generates quick recall flashcards from the current problem, shown as a dialog. */
    fun onRequestFlashcards() {
        val problemText = (_state.value as? AppScreenState.Tutoring)?.session?.problemText ?: return
        _flashcards.value = FlashcardsUiState(cards = emptyList(), loading = true, error = null)
        viewModelScope.launch {
            llm.generateFlashcards(PromptBuilder.buildFlashcardsPrompt(problemText))
                .onSuccess { cards -> _flashcards.value = FlashcardsUiState(cards = cards, loading = false, error = null) }
                .onFailure {
                    _flashcards.value = FlashcardsUiState(cards = emptyList(), loading = false, error = "Couldn't generate flashcards - try again.")
                }
        }
    }

    fun onDismissFlashcards() {
        _flashcards.value = null
    }

    fun onOpenHistory() {
        _historyView.value = HistoryViewState.ListView(historyRepository.getAllSessions())
    }

    fun onSelectHistorySession(session: SessionRecord) {
        _historyView.value = HistoryViewState.DetailView(session)
    }

    fun onBackFromHistoryDetail() {
        _historyView.value = HistoryViewState.ListView(historyRepository.getAllSessions())
    }

    fun onCloseHistory() {
        _historyView.value = null
    }

    fun onOpenRewards() {
        _showRewards.value = true
    }

    fun onCloseRewards() {
        _showRewards.value = false
    }

    fun onRedeemReward(reward: Reward) {
        if (rewardsRepository.redeem(reward)) {
            _stars.value = rewardsRepository.getStars()
        }
    }

    /** Resumes a past session (finished or in-progress) as the live tutor chat. */
    fun onContinueSession(session: SessionRecord) {
        viewModelScope.launch {
            val restoredFrame = session.imagePath?.let { path -> withContext(Dispatchers.IO) { imageStorage.load(path) } }
            val restoredMessages = session.messages.map {
                ChatEntry(
                    fromTutor = it.fromTutor,
                    text = it.text,
                    bloomLevel = it.bloomLevel?.let { label -> BloomLevel.fromLabel(label) },
                    isDevilsAdvocate = it.isDevilsAdvocate,
                )
            }
            val lastLevel = restoredMessages.lastOrNull { it.fromTutor }?.bloomLevel ?: BloomLevel.REMEMBER

            currentSessionId = session.id
            currentImagePath = session.imagePath
            currentRecognized = session.recognized
            sessionGoal = session.sessionGoal
            _historyView.value = null
            _state.value = AppScreenState.Tutoring(
                frame = restoredFrame,
                session = TutorSession(
                    problemText = session.problemText,
                    messages = restoredMessages,
                    currentLevel = lastLevel,
                    isComplete = session.isComplete,
                ),
                sending = false,
            )
        }
    }

    private fun applyTutorTurn(turn: TutorTurn) {
        val latest = _state.value
        if (latest !is AppScreenState.Tutoring) return
        val level = BloomLevel.fromLabel(turn.bloomLevel) ?: latest.session.currentLevel
        val reply = listOfNotNull(turn.feedback.takeIf { it.isNotBlank() }, turn.message.takeIf { it.isNotBlank() })
            .joinToString("\n\n")
        val justCompleted = !latest.session.isComplete && turn.isComplete
        _state.value = latest.copy(
            session = latest.session.copy(
                messages = latest.session.messages + ChatEntry(
                    fromTutor = true,
                    text = reply,
                    bloomLevel = level,
                    isDevilsAdvocate = turn.isDevilsAdvocate,
                ),
                currentLevel = level,
                isComplete = turn.isComplete,
            ),
            sending = false,
        )
        persistCurrentSession()
        if (justCompleted) {
            currentSessionId?.let { id ->
                if (rewardsRepository.awardStarForSession(id)) {
                    _stars.value = rewardsRepository.getStars()
                }
            }
        }
    }

    private fun persistCurrentSession() {
        val id = currentSessionId ?: return
        val tutoring = _state.value as? AppScreenState.Tutoring ?: return
        historyRepository.saveSession(
            SessionRecord(
                id = id,
                timestamp = id.toLongOrNull() ?: System.currentTimeMillis(),
                problemText = tutoring.session.problemText,
                sessionGoal = sessionGoal,
                imagePath = currentImagePath,
                recognized = currentRecognized,
                messages = tutoring.session.messages.map {
                    StoredChatEntry(
                        fromTutor = it.fromTutor,
                        text = it.text,
                        bloomLevel = it.bloomLevel?.label,
                        isDevilsAdvocate = it.isDevilsAdvocate,
                    )
                },
                isComplete = tutoring.session.isComplete,
            ),
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
        sessionGoal = null
        currentSessionId = null
        currentImagePath = null
        currentRecognized = true
        _state.value = AppScreenState.Capturing
    }

    override fun onCleared() {
        llm.release()
        super.onCleared()
    }
}

data class FlashcardsUiState(
    val cards: List<Flashcard>,
    val loading: Boolean,
    val error: String?,
)
