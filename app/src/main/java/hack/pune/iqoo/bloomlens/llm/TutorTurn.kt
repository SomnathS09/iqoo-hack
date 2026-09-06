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
) {
    /**
     * The model is instructed to write its own friendly "couldn't find a problem" note, but
     * small models sometimes echo the instruction placeholder verbatim instead of following it.
     * A fixed message for the unrecognized case sidesteps that entirely.
     */
    val displayMessage: String
        get() = if (recognized) message else NOT_RECOGNIZED_MESSAGE

    companion object {
        const val NOT_RECOGNIZED_MESSAGE = "sorry couldn't find the problem, please try again!"
    }
}
