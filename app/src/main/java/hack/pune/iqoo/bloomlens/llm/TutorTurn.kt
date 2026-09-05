package hack.pune.iqoo.bloomlens.llm

import kotlinx.serialization.Serializable

@Serializable
data class TutorTurn(
    val recognized: Boolean,
    val bloomLevel: String,
    val feedback: String,
    val message: String,
    val isComplete: Boolean,
    /** DOK-4 "Devil's Advocate": this message deliberately states something subtly wrong. */
    val isDevilsAdvocate: Boolean = false,
)
