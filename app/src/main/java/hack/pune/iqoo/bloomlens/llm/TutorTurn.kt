package hack.pune.iqoo.bloomlens.llm

import kotlinx.serialization.Serializable

@Serializable
data class TutorTurn(
    val recognized: Boolean,
    val bloomLevel: String,
    val feedback: String,
    val message: String,
    val isComplete: Boolean,
)
