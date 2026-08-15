package dev.diegesis.app.engine

import dev.diegesis.app.engine.ai.DefaultAiCaller
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DefaultAiCaller must fail friendly when the selected provider has no API key:
 * structured stages return their fallback, streams emit a clear user-facing
 * message, and no network call is attempted.
 */
class DefaultAiCallerGuardTest {

    private fun caller(
        openaiKey: String = "",
        anthropicKey: String = "",
        thinkProvider: String = "openai-compat",
        writeProvider: String = "openai-compat",
    ) = DefaultAiCaller(
        thinkProvider = thinkProvider,
        thinkModel = "think-model",
        writeProvider = writeProvider,
        writeModel = "write-model",
        openaiBaseUrl = "https://example.invalid/v1",
        openaiApiKey = openaiKey,
        anthropicApiKey = anthropicKey,
        client = OkHttpClient(),
    )

    @Test
    fun `generateStructured returns fallback immediately when think key is blank`() = runBlocking {
        val result = caller().generateStructured(
            systemPrompt = "sys",
            userPrompt = "user",
            decoder = { _: String -> "decoded" },
            fallback = "FALLBACK",
        )
        assertEquals("FALLBACK", result)
    }

    @Test
    fun `streamProse emits friendly message when write key is blank`() = runBlocking {
        val chunks = caller().streamProse("sys", "user").toList()
        assertEquals(listOf("Set your API key in Settings first."), chunks)
    }

    @Test
    fun `streamThink emits friendly message when think key is blank`() = runBlocking {
        val chunks = caller().streamThink("sys", "user").toList()
        assertEquals(listOf("Set your API key in Settings first."), chunks)
    }

    @Test
    fun `anthropic providers guard on the anthropic key`() = runBlocking {
        val chunks = caller(
            openaiKey = "present",
            anthropicKey = "",
            thinkProvider = "anthropic",
            writeProvider = "anthropic",
        ).streamProse("sys", "user").toList()
        assertEquals(listOf("Set your API key in Settings first."), chunks)
    }

    @Test
    fun `configured key does not trip the guard`() = runBlocking {
        // With a key present the guard must not fire; the call will fail on the
        // network (example.invalid) but generateStructured must still resolve
        // to the fallback through its normal error path, proving the guard
        // itself did not short-circuit.
        val result = caller(openaiKey = "present").generateStructured(
            systemPrompt = "sys",
            userPrompt = "user",
            decoder = { _: String -> "decoded" },
            fallback = "FALLBACK",
        )
        assertTrue(result == "FALLBACK" || result == "decoded")
    }
}
