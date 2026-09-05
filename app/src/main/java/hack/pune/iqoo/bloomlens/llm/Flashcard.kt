package hack.pune.iqoo.bloomlens.llm

import kotlinx.serialization.Serializable

@Serializable
data class Flashcard(val front: String, val back: String)

@Serializable
data class FlashcardSet(val cards: List<Flashcard>)
