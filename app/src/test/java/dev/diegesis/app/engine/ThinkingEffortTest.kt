package dev.diegesis.app.engine

import dev.diegesis.app.engine.ai.ThinkingEffort
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure mapping/clamp logic for the thinking-effort setting. Anthropic rejects
 * budget_tokens < 1024 and budget_tokens >= max_tokens, so the effective
 * budget is min(levelBudget, thinkMaxTokens - 1024) and the thinking object is
 * omitted entirely when that falls under 1024.
 */
class ThinkingEffortTest {

    // ---- level → budget mapping ---------------------------------------------

    @Test
    fun `level maps to documented anthropic budgets`() {
        assertEquals(1_024, ThinkingEffort.anthropicBudgetTokens("low"))
        assertEquals(4_096, ThinkingEffort.anthropicBudgetTokens("medium"))
        assertEquals(16_384, ThinkingEffort.anthropicBudgetTokens("high"))
        assertEquals(32_768, ThinkingEffort.anthropicBudgetTokens("xhigh"))
    }

    @Test
    fun `unknown level maps to the medium budget`() {
        assertEquals(4_096, ThinkingEffort.anthropicBudgetTokens("turbo"))
        assertEquals(4_096, ThinkingEffort.anthropicBudgetTokens(""))
    }

    // ---- normalization (defensive against bad stored values) -----------------

    @Test
    fun `normalize accepts the four levels and trims case and whitespace`() {
        assertEquals("low", ThinkingEffort.normalize("low"))
        assertEquals("medium", ThinkingEffort.normalize("medium"))
        assertEquals("high", ThinkingEffort.normalize("high"))
        assertEquals("xhigh", ThinkingEffort.normalize("xhigh"))
        assertEquals("high", ThinkingEffort.normalize(" HIGH "))
    }

    @Test
    fun `normalize falls back to medium for invalid values`() {
        assertEquals("medium", ThinkingEffort.normalize(null))
        assertEquals("medium", ThinkingEffort.normalize(""))
        assertEquals("medium", ThinkingEffort.normalize("banana"))
        assertEquals("medium", ThinkingEffort.normalize("x-high"))
    }

    // ---- clamping -------------------------------------------------------------

    @Test
    fun `budget is clamped to thinkMaxTokens minus headroom`() {
        // xhigh wants 32768, but 8192 - 1024 = 7168 is all that fits.
        assertEquals(7_168, ThinkingEffort.effectiveAnthropicBudget("xhigh", 8_192))
        // high wants 16384; with 20k max tokens it fits unclamped.
        assertEquals(16_384, ThinkingEffort.effectiveAnthropicBudget("high", 20_000))
    }

    @Test
    fun `budget below the anthropic minimum is omitted`() {
        // 2048 - 1024 = 1024 → exactly the floor, still allowed.
        assertEquals(1_024, ThinkingEffort.effectiveAnthropicBudget("low", 2_048))
        // 2047 - 1024 = 1023 → under the floor, omit.
        assertNull(ThinkingEffort.effectiveAnthropicBudget("low", 2_047))
        // Degenerate max_tokens: omit for every level.
        assertNull(ThinkingEffort.effectiveAnthropicBudget("xhigh", 1_024))
        assertNull(ThinkingEffort.effectiveAnthropicBudget("medium", 0))
    }

    // ---- request body construction --------------------------------------------

    @Test
    fun `openai body is a top level reasoning_effort string`() {
        val body = ThinkingEffort.openAiCustomBody("high")
        assertEquals(1, body.size)
        assertEquals("reasoning_effort", body[0].key)
        assertEquals(JsonPrimitive("high"), body[0].value)
    }

    @Test
    fun `openai body normalizes invalid levels to medium`() {
        val body = ThinkingEffort.openAiCustomBody("nope")
        assertEquals(JsonPrimitive("medium"), body[0].value)
    }

    @Test
    fun `anthropic body is thinking enabled with the clamped budget`() {
        val body = ThinkingEffort.anthropicCustomBody("xhigh", 8_192)
        assertEquals(1, body.size)
        assertEquals("thinking", body[0].key)
        val obj = body[0].value as JsonObject
        assertEquals("enabled", obj["type"]!!.jsonPrimitive.content)
        assertEquals(7_168, obj["budget_tokens"]!!.jsonPrimitive.int)
    }

    @Test
    fun `anthropic body is omitted when the clamped budget is under 1024`() {
        assertTrue(ThinkingEffort.anthropicCustomBody("low", 2_047).isEmpty())
        assertTrue(ThinkingEffort.anthropicCustomBody("xhigh", 1_024).isEmpty())
    }
}
