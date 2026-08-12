package dev.diegesis.ai.provider.providers.openai

import kotlinx.coroutines.flow.Flow
import dev.diegesis.ai.provider.ProviderSetting
import dev.diegesis.ai.provider.TextGenerationParams
import dev.diegesis.ai.ui.MessageChunk
import dev.diegesis.ai.ui.UIMessage

interface OpenAIImpl {
    suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk

    suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk>
}
