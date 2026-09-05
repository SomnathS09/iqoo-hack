package hack.pune.iqoo.bloomlens.llm

import kotlinx.serialization.json.Json

object TutorResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): Result<TutorTurn> {
        extractJsonObject(raw)?.let { candidate ->
            runCatching { json.decodeFromString<TutorTurn>(candidate) }
                .onSuccess { return Result.success(it) }
        }
        parseLabeledSections(raw)?.let { return Result.success(it) }
        return Result.failure(IllegalArgumentException("Could not parse a tutor turn from model output: $raw"))
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }

    private fun parseLabeledSections(text: String): TutorTurn? {
        if (Regex("(?i)\"?recognized\"?\\s*[:=]\\s*false").containsMatchIn(text)) {
            return TutorTurn(
                recognized = false,
                bloomLevel = "",
                feedback = "",
                message = text.trim().take(300).ifBlank {
                    "I couldn't find a clear problem in that photo - try pointing me at a question or exercise!"
                },
                isComplete = true,
            )
        }

        val level = Regex("(?i)bloomLevel[:\\-]\\s*(\\w+)").find(text)?.groupValues?.get(1)?.trim()
        val feedback = Regex("(?i)feedback[:\\-]\\s*(.+)").find(text)?.groupValues?.get(1)?.trim()
        val message = Regex("(?i)message[:\\-]\\s*(.+)").find(text)?.groupValues?.get(1)?.trim()
        val isDevilsAdvocate = Regex("(?i)isDevilsAdvocate\\s*[:=]\\s*true").containsMatchIn(text)
        if (level.isNullOrBlank() && message.isNullOrBlank()) return null

        return TutorTurn(
            recognized = true,
            bloomLevel = level.orEmpty(),
            feedback = feedback.orEmpty(),
            message = message.orEmpty(),
            isComplete = false,
            isDevilsAdvocate = isDevilsAdvocate,
        )
    }
}
