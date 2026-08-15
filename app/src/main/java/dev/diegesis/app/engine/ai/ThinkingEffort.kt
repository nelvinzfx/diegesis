package dev.diegesis.app.engine.ai

import dev.diegesis.ai.provider.CustomBody
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Thinking effort for THINK-stage calls (router / plot / agency / extraction —
 * the generateStructured and streamThink paths). Scene prose is never affected.
 *
 * Pure functions only, so the level→budget mapping and the Anthropic clamp are
 * unit-testable without any HTTP layer:
 *  - openai-compat: the level string goes out as a top-level `reasoning_effort`.
 *  - anthropic: the level maps to an extended-thinking token budget
 *    (`thinking: {"type": "enabled", "budget_tokens": N}`), clamped so the
 *    budget always stays below max_tokens, and omitted entirely when the
 *    clamped budget would fall under Anthropic's 1024-token minimum.
 */
object ThinkingEffort {
    const val DEFAULT = "medium"

    /** The four valid levels, in ascending order. Stored lowercase. */
    val LEVELS = listOf("low", "medium", "high", "xhigh")

    /** Anthropic's hard floor for `budget_tokens`. */
    const val MIN_BUDGET_TOKENS = 1024

    /** Headroom reserved for the visible answer: budget <= thinkMaxTokens - 1024. */
    const val ANSWER_HEADROOM_TOKENS = 1024

    /** Defensive: any unknown/legacy stored value falls back to [DEFAULT]. */
    fun normalize(raw: String?): String {
        val level = raw?.trim()?.lowercase() ?: return DEFAULT
        return if (level in LEVELS) level else DEFAULT
    }

    /** Level → Anthropic extended-thinking budget, before clamping. */
    fun anthropicBudgetTokens(level: String): Int = when (normalize(level)) {
        "low" -> 1_024
        "medium" -> 4_096
        "high" -> 16_384
        "xhigh" -> 32_768
        else -> 4_096 // unreachable: normalize() only returns LEVELS values
    }

    /**
     * Effective Anthropic budget after clamping, or null when the thinking
     * object must be omitted. Anthropic rejects budget_tokens < 1024 and
     * budget_tokens >= max_tokens, so:
     *   effective = min(budget, thinkMaxTokens - 1024); null if < 1024.
     */
    fun effectiveAnthropicBudget(level: String, thinkMaxTokens: Int): Int? {
        val clamped = minOf(
            anthropicBudgetTokens(level),
            thinkMaxTokens - ANSWER_HEADROOM_TOKENS
        )
        return if (clamped < MIN_BUDGET_TOKENS) null else clamped
    }

    /** OpenAI-compatible: top-level `reasoning_effort: "<level>"`. */
    fun openAiCustomBody(level: String): List<CustomBody> = listOf(
        CustomBody("reasoning_effort", JsonPrimitive(normalize(level)))
    )

    /**
     * Anthropic: `thinking: {"type": "enabled", "budget_tokens": N}` with the
     * clamped budget, or an empty list when the budget is too small to send.
     */
    fun anthropicCustomBody(level: String, thinkMaxTokens: Int): List<CustomBody> {
        val budget = effectiveAnthropicBudget(level, thinkMaxTokens) ?: return emptyList()
        return listOf(
            CustomBody(
                "thinking",
                buildJsonObject {
                    put("type", "enabled")
                    put("budget_tokens", budget)
                }
            )
        )
    }
}
