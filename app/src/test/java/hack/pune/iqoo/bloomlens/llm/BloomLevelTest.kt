package hack.pune.iqoo.bloomlens.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BloomLevelTest {

    @Test
    fun `matches an exact label`() {
        assertEquals(BloomLevel.UNDERSTAND, BloomLevel.fromLabel("Understand"))
    }

    @Test
    fun `matches regardless of case`() {
        assertEquals(BloomLevel.APPLY, BloomLevel.fromLabel("apply"))
        assertEquals(BloomLevel.APPLY, BloomLevel.fromLabel("APPLY"))
    }

    @Test
    fun `matches with surrounding whitespace`() {
        assertEquals(BloomLevel.ANALYZE, BloomLevel.fromLabel("  Analyze  "))
    }

    @Test
    fun `matches a small model's near-miss spelling`() {
        // A small on-device model won't always echo the exact enum word - this is exactly the
        // kind of drift that used to make the tutor read as permanently stuck at one level
        // (exact-match failure silently fell back to the previous level, not an error).
        assertEquals(BloomLevel.UNDERSTAND, BloomLevel.fromLabel("Understanding"))
        assertEquals(BloomLevel.REMEMBER, BloomLevel.fromLabel("Remember."))
        assertEquals(BloomLevel.CREATE, BloomLevel.fromLabel("Create (final)"))
    }

    @Test
    fun `returns null for blank or unrelated text`() {
        assertNull(BloomLevel.fromLabel(""))
        assertNull(BloomLevel.fromLabel("   "))
        assertNull(BloomLevel.fromLabel("Beginner"))
    }
}
