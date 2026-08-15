package dev.diegesis.app.engine

import dev.diegesis.app.engine.ai.DefaultAiCaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The writer must follow the configured story language even when character
 * cards or other source material are in a different language.
 */
class LanguageDirectiveTest {

    @Test
    fun `directive appends Bahasa Indonesia instruction`() {
        val out = DefaultAiCaller.languageDirective("You are the narrator.", "Bahasa Indonesia")
        assertTrue(out.startsWith("You are the narrator."))
        assertTrue(out.contains("Write all narration, dialogue, and prose in Bahasa Indonesia"))
        assertTrue(out.contains("regardless of the language of any character sheets"))
    }

    @Test
    fun `directive also applies to explicit English`() {
        // English is a real choice: an Indonesian card must still yield English prose.
        val out = DefaultAiCaller.languageDirective("sys", "English")
        assertTrue(out.contains("in English"))
    }

    @Test
    fun `blank language leaves the prompt untouched`() {
        assertEquals("sys", DefaultAiCaller.languageDirective("sys", ""))
    }

    @Test
    fun `directive does not double-append trailing whitespace`() {
        val out = DefaultAiCaller.languageDirective("sys   \n", "English")
        assertFalse(out.contains("   \n\n"))
    }
}
