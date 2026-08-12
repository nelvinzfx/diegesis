# AI module port map (RikkaHub → Diegesis)

Source: `~/rikkahub-agent/ai/src/main/java/me/rerere/ai/`
(33 files, ~8.2k lines). Target package: `dev.diegesis.ai`.

## KEEP (copy, rename package, adapt imports)

| Source file | Notes |
|---|---|
| core/MessageRole.kt, core/Reasoning.kt, core/Tool.kt, core/Usage.kt | pure data |
| provider/Model.kt, provider/Provider.kt, provider/ProviderManager.kt, provider/ProviderSetting.kt | registry-facing API |
| provider/providers/OpenAIProvider.kt | chat completions path only |
| provider/providers/ClaudeProvider.kt | anthropic |
| provider/providers/ProviderMessageUtils.kt | shared helpers |
| provider/providers/LosslessCallbackFlow.kt | streaming infra |
| provider/providers/StreamedToolCallIdResolver.kt | streaming infra |
| provider/providers/openai/ChatCompletionsAPI.kt | the core API |
| provider/providers/openai/OpenAIImpl.kt | client impl |
| registry/ModelDsl.kt, registry/ModelRegistry.kt | model catalog |
| ui/Message.kt, ui/MessageMetadata.kt, ui/Image.kt | message model |
| util/Json.kt, util/ErrorParser.kt, util/KeyRoulette.kt, util/Request.kt, util/SSE.kt, util/Serializer.kt, util/FileEncoder.kt | utilities |

## STRIP (do not copy)

| Source file | Why |
|---|---|
| provider/providers/AICoreProvider.kt | drags `com.google.mlkit:genai-prompt` |
| provider/providers/GoogleProvider.kt | spec: 2 providers only |
| provider/providers/vertex/ServiceAccountTokenProvider.kt | google auth |
| provider/OpenRouterRouting.kt | openrouter is covered by openai-compat |
| provider/providers/openai/OpenRouterRequestBuilder.kt | same |
| provider/providers/openai/ResponseAPI.kt | openai-specific v2 API, chat completions suffices |

Also strip from `ai/build.gradle.kts`: the ML kit genai dependency and
the `project(":common")` dependency.

## INLINE (replaces `:common`)

Create `util/CommonInline.kt` (~50 lines) containing:
- `Call.await()` — suspend wrapper for okhttp (from
  `common/src/main/java/me/rerere/common/http/Request.kt`)
- `jsonPrimitiveOrNull`, `jsonObjectOrNull`, `jsonArrayOrNull`,
  `getByKey` — JsonElement accessors (from
  `common/src/main/java/me/rerere/common/http/Json.kt`)

Only 5 files in `:ai` reference `me.rerere.common`; update their
imports to the new inline file.

## Dependency replacements

- `kotlinx.datetime` → `java.time` (Instant/Clock). If a usage is
  painful to convert, keeping kotlinx-datetime is acceptable (small,
  no ksp); prefer java.time.
- Final module deps: okhttp, okhttp-sse, okhttp-logging (debug only),
  kotlinx-serialization-json, kotlinx-coroutines-core.

## Acceptance checks (must all pass)

1. `grep -r "me.rerere" ai/src` returns nothing.
2. Module compiles with the dependency list above and nothing else.
3. `OpenAIProvider` streams a chat completion against an
   OpenAI-compatible endpoint (verified in app hello-world).
4. `ClaudeProvider` streams a message against api.anthropic.com
   (same hello-world, provider switch).
