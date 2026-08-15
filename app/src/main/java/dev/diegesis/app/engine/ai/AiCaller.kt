package dev.diegesis.app.engine.ai

import dev.diegesis.ai.provider.Model
import dev.diegesis.ai.provider.ProviderSetting
import dev.diegesis.ai.provider.TextGenerationParams
import dev.diegesis.ai.provider.providers.ClaudeProvider
import dev.diegesis.ai.provider.providers.OpenAIProvider
import dev.diegesis.ai.ui.UIMessage
import dev.diegesis.ai.ui.UIMessagePart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient

/**
 * The engine's only door to the model providers.
 *
 * Two shapes, because the pipeline only ever needs two:
 *  - [generateStructured] for the JSON stages (router/plot/agency/extraction),
 *    which retries once and then falls back rather than throwing.
 *  - [streamProse] for the scene stage, which streams tokens to the UI.
 *
 * Kept as an interface so the whole pipeline is headless-testable with a fake.
 */
interface AiCaller {
    /**
     * Run a structured stage. Contract per pipeline.md: on decode failure,
     * retry once with "Return valid JSON only, no prose."; on second failure
     * return [fallback]. MUST NOT throw for parse or transport problems.
     */
    suspend fun <T> generateStructured(
        systemPrompt: String,
        userPrompt: String,
        decoder: (String) -> T,
        fallback: T,
    ): T

    /** Stream scene prose from the write model. */
    suspend fun streamProse(
        systemPrompt: String,
        userPrompt: String,
    ): Flow<String>

    /**
     * Stream non-scene prose from the THINK model (e.g. session plan
     * generation). Default falls back to streamProse so fakes stay valid;
     * the real caller overrides it with the think provider/model.
     */
    suspend fun streamThink(
        systemPrompt: String,
        userPrompt: String,
    ): Flow<String> = streamProse(systemPrompt, userPrompt)
}

/**
 * Real implementation over the `:ai` providers.
 *
 * Structured stages go to the think model, prose to the write model, per
 * pipeline.md. Provider selection is by the same string keys storage.md uses
 * in `StageModelSelection.provider` ("openai-compat" | "anthropic").
 */
class DefaultAiCaller(
    private val thinkProvider: String,
    private val thinkModel: String,
    private val writeProvider: String,
    private val writeModel: String,
    private val openaiBaseUrl: String,
    private val openaiApiKey: String,
    private val anthropicApiKey: String,
    private val client: OkHttpClient,
) : AiCaller {

    override suspend fun <T> generateStructured(
        systemPrompt: String,
        userPrompt: String,
        decoder: (String) -> T,
        fallback: T,
    ): T {
        val base = listOf(
            UIMessage.system(systemPrompt),
            UIMessage.user(userPrompt),
        )

        val first = runCatching { generate(base) }.getOrNull()
        if (first != null) {
            val decoded = runCatching { decoder(sanitize(first)) }
            if (decoded.isSuccess) return decoded.getOrThrow()
        }

        // Retry once, echoing the bad output back so the model can correct it.
        val retry = base +
            UIMessage.assistant(first ?: "") +
            UIMessage.user("Return valid JSON only, no prose.")

        val second = runCatching { generate(retry) }.getOrNull() ?: return fallback
        return runCatching { decoder(sanitize(second)) }.getOrDefault(fallback)
    }

    override suspend fun streamProse(
        systemPrompt: String,
        userPrompt: String,
    ): Flow<String> {
        val messages = listOf(
            UIMessage.system(systemPrompt),
            UIMessage.user(userPrompt),
        )
        val params = TextGenerationParams(
            model = Model(modelId = writeModel, displayName = writeModel),
            temperature = 0.85f,
            maxTokens = 8192,
        )

        return when (writeProvider) {
            PROVIDER_ANTHROPIC -> flow {
                ClaudeProvider(client)
                    .streamText(claudeSetting(), messages, params)
                    .collect { chunk -> chunk.textDeltas().forEach { emit(it) } }
            }

            PROVIDER_OPENAI -> flow {
                OpenAIProvider(client)
                    .streamText(openAiSetting(), messages, params)
                    .collect { chunk -> chunk.textDeltas().forEach { emit(it) } }
            }

            else -> emptyFlow()
        }
    }

    override suspend fun streamThink(
        systemPrompt: String,
        userPrompt: String,
    ): Flow<String> {
        val messages = listOf(
            UIMessage.system(systemPrompt),
            UIMessage.user(userPrompt),
        )
        val params = TextGenerationParams(
            model = Model(modelId = thinkModel, displayName = thinkModel),
            temperature = 0.7f,
            maxTokens = 4096,
        )

        return when (thinkProvider) {
            PROVIDER_ANTHROPIC -> flow {
                ClaudeProvider(client)
                    .streamText(claudeSetting(), messages, params)
                    .collect { chunk -> chunk.textDeltas().forEach { emit(it) } }
            }

            PROVIDER_OPENAI -> flow {
                OpenAIProvider(client)
                    .streamText(openAiSetting(), messages, params)
                    .collect { chunk -> chunk.textDeltas().forEach { emit(it) } }
            }

            else -> emptyFlow()
        }
    }

    private suspend fun generate(messages: List<UIMessage>): String {
        val params = TextGenerationParams(
            model = Model(modelId = thinkModel, displayName = thinkModel),
            temperature = 0.3f,
            maxTokens = 4096,
        )

        val chunk = when (thinkProvider) {
            PROVIDER_ANTHROPIC ->
                ClaudeProvider(client).generateText(claudeSetting(), messages, params)

            PROVIDER_OPENAI ->
                OpenAIProvider(client).generateText(openAiSetting(), messages, params)

            else -> error("Unknown provider: $thinkProvider")
        }

        val message = chunk.choices.firstOrNull()?.let { it.message ?: it.delta }
        return message?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("") { it.text }
            .orEmpty()
    }

    private fun openAiSetting() = ProviderSetting.OpenAI(
        name = "Diegesis think/write (openai-compat)",
        apiKey = openaiApiKey,
        baseUrl = openaiBaseUrl,
    )

    private fun claudeSetting() = ProviderSetting.Claude(
        name = "Diegesis think/write (anthropic)",
        apiKey = anthropicApiKey,
    )

    private fun dev.diegesis.ai.ui.MessageChunk.textDeltas(): List<String> =
        choices.firstOrNull()
            ?.let { it.delta ?: it.message }
            ?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.map { it.text }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    companion object {
        const val PROVIDER_OPENAI = "openai-compat"
        const val PROVIDER_ANTHROPIC = "anthropic"

        /**
         * Models fence JSON in ```json blocks even when told not to. Strip the
         * fence and any leading/trailing prose before handing text to a decoder,
         * so a cosmetic wrapper doesn't burn the single retry.
         */
        fun sanitize(raw: String): String {
            var text = raw.trim()

            if (text.startsWith("```")) {
                text = text.removePrefix("```")
                    .removePrefix("json")
                    .removePrefix("JSON")
                    .trim()
                val fence = text.lastIndexOf("```")
                if (fence >= 0) text = text.substring(0, fence).trim()
            }

            // Trim to the outermost JSON object/array if the model added prose.
            val firstBrace = text.indexOfFirst { it == '{' || it == '[' }
            if (firstBrace > 0) {
                val opener = text[firstBrace]
                val closer = if (opener == '{') '}' else ']'
                val lastClose = text.lastIndexOf(closer)
                if (lastClose > firstBrace) {
                    text = text.substring(firstBrace, lastClose + 1)
                }
            }

            return text
        }
    }
}
