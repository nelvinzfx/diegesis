package dev.diegesis.ai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import dev.diegesis.ai.provider.Model
import dev.diegesis.ai.ui.UIMessage
import dev.diegesis.ai.ui.UIMessagePart

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val parameters: () -> InputSchema? = { null },
    val systemPrompt: (model: Model, messages: List<UIMessage>) -> String = { _, _ -> "" },
    val needsApproval: (JsonElement) -> Boolean = { false },
    val execute: suspend (JsonElement) -> List<UIMessagePart>
)

@Serializable
sealed class InputSchema {
    @Serializable
    @SerialName("object")
    data class Obj(
        val properties: JsonObject,
        val required: List<String>? = null,
        @SerialName("\$schema")
        val schema: String? = null,
        @SerialName("\$defs")
        val defs: JsonObject? = null,
        val additionalProperties: Boolean? = null,
    ) : InputSchema()
}
