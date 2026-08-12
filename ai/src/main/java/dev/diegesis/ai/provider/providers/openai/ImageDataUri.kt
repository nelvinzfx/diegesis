package dev.diegesis.ai.provider.providers.openai

/**
 * Generic image data-URI parsing. Extracted from RikkaHub's OpenRouterRequestBuilder.kt
 * (the rest of that file is OpenRouter-specific and was stripped per docs/ai-port-map.md);
 * this helper is mime-agnostic and used by the chat-completions image path.
 */

data class ParsedImageDataUri(val mime: String, val base64: String)

private val DATA_URI_REGEX =
    Regex("^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$", RegexOption.DOT_MATCHES_ALL)

/**
 * Parse any image data URI (png/jpeg/webp/...) into its mime and base64 payload.
 * Returns null for non-data-URIs (e.g. http URLs) or malformed input.
 */
fun parseImageDataUri(url: String): ParsedImageDataUri? {
    val m = DATA_URI_REGEX.matchEntire(url.trim()) ?: return null
    return ParsedImageDataUri(mime = m.groupValues[1], base64 = m.groupValues[2])
}
