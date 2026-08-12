package dev.diegesis.ai.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException
import kotlin.coroutines.resumeWithException

/**
 * Inlined replacements for RikkaHub's `:common` module (common/http helpers).
 * See docs/ai-port-map.md § INLINE.
 */

suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { cause, _, _ ->
                    response.closeQuietly()
                }
            }
        })
    }
}

val JsonElement.jsonObjectOrNull: JsonObject?
    get() = this as? JsonObject

val JsonElement.jsonArrayOrNull: JsonArray?
    get() = this as? JsonArray

val JsonElement.jsonPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

/**
 * Resolve a dotted/indexed path expression like `data.total_usage` or `data[0].balance`
 * against this object and return the value as a string. Missing segments resolve to "".
 *
 * Simplified from RikkaHub's full JSON expression evaluator: only path navigation is
 * supported, which is all the balance-option lookup ever needs.
 */
fun JsonObject.getByKey(key: String): String {
    var cur: JsonElement? = this
    val segment = Regex("""([^.\[\]]+)|\[(\d+)]""")
    for (m in segment.findAll(key)) {
        val field = m.groupValues[1]
        val index = m.groupValues[2]
        cur = when {
            field.isNotEmpty() -> (cur as? JsonObject)?.get(field)
            index.isNotEmpty() -> (cur as? JsonArray)?.getOrNull(index.toInt())
            else -> null
        }
        if (cur == null) return ""
    }
    return when (val v = cur) {
        null, is JsonNull -> ""
        is JsonPrimitive -> v.content
        else -> v.toString()
    }
}
