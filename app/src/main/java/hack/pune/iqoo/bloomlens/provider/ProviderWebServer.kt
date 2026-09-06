package hack.pune.iqoo.bloomlens.provider

import android.graphics.BitmapFactory
import fi.iki.elonen.NanoHTTPD
import hack.pune.iqoo.bloomlens.llm.BloomLevel
import hack.pune.iqoo.bloomlens.llm.OnDeviceLlm
import hack.pune.iqoo.bloomlens.llm.PromptBuilder
import hack.pune.iqoo.bloomlens.model.HistoryRepository
import hack.pune.iqoo.bloomlens.model.ImageStorage
import hack.pune.iqoo.bloomlens.model.SessionRecord
import hack.pune.iqoo.bloomlens.model.StoredChatEntry
import hack.pune.iqoo.bloomlens.model.UserPersona
import hack.pune.iqoo.bloomlens.ocr.TextRecognizer
import hack.pune.iqoo.bloomlens.state.ChatEntry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Serves the same Bloom's-Taxonomy tutor over plain HTTP so nearby devices - connected only to
 * this phone's Wi-Fi hotspot, no internet required - can use its on-device NPU model from their
 * own browser.
 *
 * Every request that touches the model or OCR is serialized through [requestMutex]: the NPU
 * model handle is a single session (see the concurrency note on [GenieOnDeviceLlm]), so only one
 * student's request can be processed at a time regardless of how many are connected. Requests
 * simply queue and wait - the student's browser shows its own native "loading" state meanwhile.
 */
class ProviderWebServer(
    private val textRecognizer: TextRecognizer,
    private val llm: OnDeviceLlm,
    private val historyRepository: HistoryRepository,
    private val imageStorage: ImageStorage,
    port: Int = DEFAULT_PORT,
) : NanoHTTPD(port) {

    private val sessions = ConcurrentHashMap<String, WebTutorSession>()
    private val requestMutex = Mutex()

    override fun serve(session: IHTTPSession): Response = try {
        when {
            session.method == Method.GET && session.uri == "/" -> renderLanding(null)
            session.method == Method.POST && session.uri == "/start" -> handleStart(session)
            session.method == Method.GET && session.uri == "/chat" -> renderChatOrRedirect(session)
            session.method == Method.POST && session.uri == "/reply" -> handleReply(session)
            session.method == Method.POST && session.uri == "/flashcards" -> handleFlashcards(session)
            session.method == Method.GET && session.uri == "/new" -> handleNew(session)
            session.method == Method.GET && session.uri == "/photo" -> servePhoto(session)
            else -> notFound()
        }
    } catch (e: Exception) {
        html(Response.Status.INTERNAL_ERROR, ProviderHtml.errorPage(e.message ?: "Something went wrong"))
    }

    private fun handleStart(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val parms = session.parms

        val name = parms["name"]?.trim().orEmpty().ifBlank { "Student" }
        val skillLevel = parms["skillLevel"]?.trim().orEmpty().ifBlank { "Beginner" }
        val focusArea = parms["focusArea"]?.trim().orEmpty().ifBlank { "General" }
        val goal = parms["goal"]?.trim()?.takeIf { it.isNotBlank() }
        val manualText = parms["problemText"]?.trim().orEmpty()
        val uploadedPath = files["photo"]

        return runBlocking {
            requestMutex.withLock {
                val bitmap = uploadedPath
                    ?.let { File(it) }
                    ?.takeIf { it.length() > 0 }
                    ?.let { BitmapFactory.decodeFile(it.absolutePath) }

                val problemText = when {
                    bitmap != null -> {
                        val ocr = runCatching { textRecognizer.recognize(bitmap) }.getOrNull()
                        if (ocr == null || ocr.isBlank) "" else ocr.rawText
                    }
                    manualText.isNotBlank() -> manualText
                    else -> ""
                }

                if (problemText.isBlank()) {
                    return@withLock renderLanding("Couldn't read a problem from that - try a clearer photo, or type it in directly.")
                }

                val persona = UserPersona(skillLevel, focusArea)
                val prompt = PromptBuilder.buildFirstTurnPrompt(problemText, persona, goal)

                llm.generateTutorTurn(prompt).fold(
                    onSuccess = { turn ->
                        val id = UUID.randomUUID().toString()
                        val level = BloomLevel.fromLabel(turn.bloomLevel) ?: BloomLevel.REMEMBER
                        val webSession = WebTutorSession(
                            id = id,
                            studentName = name,
                            persona = persona,
                            sessionGoal = goal,
                            problemText = problemText,
                        )
                        webSession.imagePath = bitmap?.let { imageStorage.save(it, "web_$id") }
                        webSession.messages.add(
                            ChatEntry(fromTutor = true, text = turn.displayMessage, bloomLevel = level.takeIf { turn.recognized }),
                        )
                        webSession.currentLevel = level
                        webSession.isComplete = turn.isComplete
                        sessions[id] = webSession
                        persist(webSession)
                        ProviderStatus.sessionCount.value = sessions.size
                        redirect("/chat", setCookie = id)
                    },
                    onFailure = { renderLanding("The tutor had trouble with that - please try again.") },
                )
            }
        }
    }

    private fun handleReply(session: IHTTPSession): Response {
        val webSession = currentSession(session) ?: return redirect("/")
        session.parseBody(HashMap())
        val answer = session.parms["answer"]?.trim().orEmpty()
        if (answer.isBlank() || webSession.isComplete) return redirect("/chat")

        return runBlocking {
            requestMutex.withLock {
                val previousQuestion = webSession.messages.lastOrNull { it.fromTutor }?.text.orEmpty()
                webSession.messages.add(ChatEntry(fromTutor = false, text = answer))

                val prompt = PromptBuilder.buildFollowUpPrompt(
                    problemText = webSession.problemText,
                    persona = webSession.persona,
                    currentLevel = webSession.currentLevel,
                    previousQuestion = previousQuestion,
                    userAnswer = answer,
                    forceAdvance = webSession.turnsAtCurrentLevel >= PromptBuilder.FORCE_ADVANCE_AFTER_TURNS &&
                        webSession.currentLevel != BloomLevel.CREATE,
                )
                llm.generateTutorTurn(prompt).fold(
                    onSuccess = { turn ->
                        val level = BloomLevel.fromLabel(turn.bloomLevel) ?: webSession.currentLevel
                        val reply = listOfNotNull(turn.feedback.takeIf { it.isNotBlank() }, turn.message.takeIf { it.isNotBlank() })
                            .joinToString("\n\n")
                        webSession.messages.add(
                            ChatEntry(fromTutor = true, text = reply, bloomLevel = level, isDevilsAdvocate = turn.isDevilsAdvocate),
                        )
                        webSession.turnsAtCurrentLevel = if (level == webSession.currentLevel) webSession.turnsAtCurrentLevel + 1 else 0
                        webSession.currentLevel = level
                        webSession.isComplete = turn.isComplete
                        persist(webSession)
                    },
                    onFailure = {
                        webSession.messages.add(
                            ChatEntry(fromTutor = true, text = "Sorry, I had trouble responding to that - could you try rephrasing?"),
                        )
                    },
                )
                redirect("/chat")
            }
        }
    }

    private fun handleFlashcards(session: IHTTPSession): Response {
        val webSession = currentSession(session) ?: return redirect("/")
        return runBlocking {
            requestMutex.withLock {
                val cards = llm.generateFlashcards(PromptBuilder.buildFlashcardsPrompt(webSession.problemText)).getOrNull()
                html(Response.Status.OK, ProviderHtml.flashcardsPage(cards))
            }
        }
    }

    private fun handleNew(session: IHTTPSession): Response {
        currentSession(session)?.let { sessions.remove(it.id) }
        ProviderStatus.sessionCount.value = sessions.size
        return redirect("/")
    }

    private fun servePhoto(session: IHTTPSession): Response {
        val sid = session.parms["sid"] ?: return notFound()
        val path = sessions[sid]?.imagePath ?: return notFound()
        val file = File(path)
        if (!file.exists()) return notFound()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", file.inputStream(), file.length())
    }

    private fun persist(webSession: WebTutorSession) {
        historyRepository.saveSession(
            SessionRecord(
                id = webSession.id,
                timestamp = webSession.createdAt,
                problemText = webSession.problemText,
                sessionGoal = webSession.sessionGoal,
                imagePath = webSession.imagePath,
                recognized = true,
                studentName = webSession.studentName,
                messages = webSession.messages.map {
                    StoredChatEntry(fromTutor = it.fromTutor, text = it.text, bloomLevel = it.bloomLevel?.label, isDevilsAdvocate = it.isDevilsAdvocate)
                },
                isComplete = webSession.isComplete,
            ),
        )
    }

    private fun cookieSessionId(session: IHTTPSession): String? {
        val cookieHeader = session.headers["cookie"] ?: return null
        return cookieHeader.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("sid=") }
            ?.substringAfter("sid=")
    }

    private fun currentSession(session: IHTTPSession): WebTutorSession? = cookieSessionId(session)?.let { sessions[it] }

    private fun html(status: Response.Status, body: String): Response {
        val response = newFixedLengthResponse(status, "text/html; charset=utf-8", body)
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun redirect(location: String, setCookie: String? = null): Response {
        val response = newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, MIME_PLAINTEXT, "")
        response.addHeader("Location", location)
        setCookie?.let { response.addHeader("Set-Cookie", "sid=$it; Path=/; Max-Age=86400") }
        return response
    }

    private fun notFound(): Response = html(Response.Status.NOT_FOUND, ProviderHtml.errorPage("Not found"))

    private fun renderLanding(error: String?): Response = html(Response.Status.OK, ProviderHtml.landingPage(error))

    private fun renderChatOrRedirect(session: IHTTPSession): Response {
        val webSession = currentSession(session) ?: return redirect("/")
        return html(Response.Status.OK, ProviderHtml.chatPage(webSession))
    }

    companion object {
        const val DEFAULT_PORT = 8080
    }
}
