package dev.diegesis.app.engine

import dev.diegesis.app.engine.ai.DefaultAiCaller
import dev.diegesis.app.engine.ai.ThinkingEffort
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request-construction seam: DefaultAiCaller builds TextGenerationParams for
 * think vs prose stages, and the providers hand params.customBody verbatim to
 * mergeCustomBody on the outgoing request. Asserting on the params therefore
 * proves what reaches the wire without needing an HTTP fake (the project has
 * no HTTP-layer seam; providers are constructed inside DefaultAiCaller).
 */
class DefaultAiCallerThinkingEffortTest {

    private fun caller(
        thinkProvider: String = DefaultAiCaller.PROVIDER_OPENAI,
        writeProvider: String = DefaultAiCaller.PROVIDER_OPENAI,
        thinkingEffort: String = "medium",
        thinkMaxTokens: Int = 4096,
    ) = DefaultAiCaller(
        thinkProvider = thinkProvider,
        thinkModel = "think-model",
        writeProvider = writeProvider,
        writeModel = "write-model",
        openaiBaseUrl = "https://example.invalid/v1",
        openaiApiKey = "key",
        anthropicApiKey = "key",
        thinkMaxTokens = thinkMaxTokens,
        thinkingEffort = thinkingEffort,
        client = OkHttpClient(),
    )

    @Test
    fun `openai think params carry top level reasoning_effort`() {
        val params = caller(thinkingEffort = "xhigh").thinkGenerationParams(temperature = 0.3f)
        val effort = params.customBody.single { it.key == "reasoning_effort" }
        assertEquals(JsonPrimitive("xhigh"), effort.value)
        // OpenAI path keeps its temperature.
        assertEquals(0.3f, params.temperature)
    }

    @Test
    fun `anthropic think params carry enabled thinking with clamped budget`() {
        val params = caller(
            thinkProvider = DefaultAiCaller.PROVIDER_ANTHROPIC,
            thinkingEffort = "xhigh",
            thinkMaxTokens = 8192,
        ).thinkGenerationParams(temperature = 0.3f)

        val thinking = params.customBody.single { it.key == "thinking" }.value as JsonObject
        assertEquals("enabled", thinking["type"]!!.jsonPrimitive.content)
        assertEquals(8192 - 1024, thinking["budget_tokens"]!!.jsonPrimitive.int)
        // Anthropic rejects temperature alongside extended thinking.
        assertNull(params.temperature)
    }

    @Test
    fun `anthropic think params omit thinking when the budget is too small`() {
        val params = caller(
            thinkProvider = DefaultAiCaller.PROVIDER_ANTHROPIC,
            thinkingEffort = "low",
            thinkMaxTokens = 2047, // 2047 - 1024 = 1023 < 1024 → omit
        ).thinkGenerationParams(temperature = 0.3f)

        assertTrue(params.customBody.isEmpty())
        // No thinking object → temperature stays.
        assertNotNull(params.temperature)
    }

    @Test
    fun `prose params never carry effort fields`() {
        for (provider in listOf(
            DefaultAiCaller.PROVIDER_OPENAI,
            DefaultAiCaller.PROVIDER_ANTHROPIC,
        )) {
            val params = caller(
                writeProvider = provider,
                thinkingEffort = "xhigh",
            ).proseGenerationParams()
            assertTrue(
                "prose params for $provider must not carry effort/thinking bodies",
                params.customBody.isEmpty()
            )
        }
    }

    @Test
    fun `invalid stored effort falls back to medium on the wire`() {
        val params = caller(thinkingEffort = "banana").thinkGenerationParams(temperature = 0.3f)
        val effort = params.customBody.single { it.key == "reasoning_effort" }
        assertEquals(JsonPrimitive(ThinkingEffort.DEFAULT), effort.value)
    }
}
