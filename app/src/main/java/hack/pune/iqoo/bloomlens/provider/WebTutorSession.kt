package hack.pune.iqoo.bloomlens.provider

import hack.pune.iqoo.bloomlens.llm.BloomLevel
import hack.pune.iqoo.bloomlens.model.UserPersona
import hack.pune.iqoo.bloomlens.state.ChatEntry

/** One remote student's tutoring session, served over Provider Mode's local web server. */
class WebTutorSession(
    val id: String,
    val studentName: String,
    val persona: UserPersona,
    val sessionGoal: String?,
    val problemText: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val messages: MutableList<ChatEntry> = mutableListOf()
    var currentLevel: BloomLevel = BloomLevel.REMEMBER
    var isComplete: Boolean = false
    var imagePath: String? = null
}
