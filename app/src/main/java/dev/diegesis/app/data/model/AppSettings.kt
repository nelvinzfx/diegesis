package dev.diegesis.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val thinkModel: StageModelSelection = StageModelSelection("openai-compat", "gpt-4o-mini"),
    val writeModel: StageModelSelection = StageModelSelection("anthropic", "claude-3-5-sonnet-20241022"),
    val openaiBaseUrl: String = "https://api.openai.com/v1",
    val openaiApiKey: String = "",
    val anthropicApiKey: String = ""
)

@Serializable
data class StageModelSelection(
    val provider: String,
    val model: String
)
