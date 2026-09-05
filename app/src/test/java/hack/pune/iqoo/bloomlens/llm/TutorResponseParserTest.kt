package hack.pune.iqoo.bloomlens.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorResponseParserTest {

    @Test
    fun `parses well-formed first-turn JSON`() {
        val raw = """{"recognized": true, "bloomLevel": "Remember", "feedback": "", "message": "Can you restate the problem?", "isComplete": false}"""

        val result = TutorResponseParser.parse(raw)

        assertTrue(result.isSuccess)
        val turn = result.getOrThrow()
        assertTrue(turn.recognized)
        assertEquals("Remember", turn.bloomLevel)
        assertEquals("Can you restate the problem?", turn.message)
        assertFalse(turn.isComplete)
    }

    @Test
    fun `parses JSON wrapped in prose and markdown fences`() {
        val raw = """
            Sure! Here's my response:
            ```json
            {"recognized": true, "bloomLevel": "Apply", "feedback": "Good start.", "message": "Now try applying that to a sorted array.", "isComplete": false}
            ```
        """.trimIndent()

        val result = TutorResponseParser.parse(raw)

        assertTrue(result.isSuccess)
        assertEquals("Apply", result.getOrThrow().bloomLevel)
    }

    @Test
    fun `parses unrecognized JSON result`() {
        val raw = """{"recognized": false, "bloomLevel": "", "feedback": "", "message": "That doesn't look like a problem.", "isComplete": true}"""

        val result = TutorResponseParser.parse(raw)

        assertTrue(result.isSuccess)
        val turn = result.getOrThrow()
        assertFalse(turn.recognized)
        assertTrue(turn.isComplete)
    }

    @Test
    fun `falls back to labeled sections when JSON is malformed`() {
        val raw = """
            bloomLevel: Analyze
            feedback: Nice reasoning.
            message: What would happen if the input were empty?
        """.trimIndent()

        val result = TutorResponseParser.parse(raw)

        assertTrue(result.isSuccess)
        val turn = result.getOrThrow()
        assertEquals("Analyze", turn.bloomLevel)
        assertEquals("What would happen if the input were empty?", turn.message)
    }

    @Test
    fun `falls back to unrecognized when labeled text says so`() {
        val raw = "recognized: false - this text doesn't contain a problem"

        val result = TutorResponseParser.parse(raw)

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().recognized)
    }

    @Test
    fun `fails on unparseable garbage`() {
        val result = TutorResponseParser.parse("")

        assertTrue(result.isFailure)
    }
}
