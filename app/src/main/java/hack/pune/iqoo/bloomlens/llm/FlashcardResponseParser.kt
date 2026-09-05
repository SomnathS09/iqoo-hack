package hack.pune.iqoo.bloomlens.llm

import kotlinx.serialization.json.Json

object FlashcardResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): Result<List<Flashcard>> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) {
            return Result.failure(IllegalArgumentException("No JSON object found in flashcards output: $raw"))
        }
        return runCatching { json.decodeFromString<FlashcardSet>(raw.substring(start, end + 1)).cards }
            .recoverCatching { throw IllegalArgumentException("Could not parse flashcards from model output: $raw") }
    }
}
