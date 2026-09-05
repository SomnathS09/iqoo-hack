package hack.pune.iqoo.bloomlens.model

import kotlinx.serialization.Serializable

@Serializable
data class StoredChatEntry(
    val fromTutor: Boolean,
    val text: String,
    val bloomLevel: String? = null,
    val isDevilsAdvocate: Boolean = false,
)

@Serializable
data class SessionRecord(
    val id: String,
    val timestamp: Long,
    val problemText: String,
    val sessionGoal: String? = null,
    val imagePath: String? = null,
    /** Whether the tutor recognized [problemText] as an actual problem, vs. an unrelated photo. */
    val recognized: Boolean = true,
    val messages: List<StoredChatEntry>,
    val isComplete: Boolean,
)
