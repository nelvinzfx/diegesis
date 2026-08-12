# Diegesis architecture (v0 sketch)

Doc map: [scope](scope.md) · [ai port map](ai-port-map.md) ·
[pipeline](pipeline.md) · [ui/theme](ui-theme.md) ·
[storage](storage.md) · [workflow](workflow.md)

## Modules

- `ai` — stripped port of RikkaHub's `:ai`. OpenAI-compatible (chat
  completions) + Anthropic providers, streaming, model registry.
  The ~50 lines of `:common` helpers it needs (http await, JSON
  accessors) are inlined. No AICore, no Vertex, no Google provider,
  no Responses API.
- `app` — everything else: Compose UI, engine, storage. Single module
  on purpose. No ksp, no ORM.

## Data model (JSON documents, app-private storage)

    /campaigns/<id>/campaign.json      Campaign meta + session plan
    /campaigns/<id>/npcs/<npcId>.json  NPC sheet, agency, voice, trackers
    /campaigns/<id>/turns/<n>.json     One file per turn
    /campaigns/<id>/memories.jsonl     Extracted memories, append-only

- **Campaign**: id, title, premise, sessionPlan (arc outline text),
  provider/model selection per stage, createdAt.
- **NPC**: id, name, description, personality, voiceExamples
  (seeded from character card v2), agency (current goal, regenerated
  when the world changes around them), trackers (free-form
  name→value map, e.g. trust, health, coin).
- **Turn**: index, playerInput, routerDecision, synopsis,
  presentNpcIds, mechanicResults, sceneOutput, variants[] (swipe),
  createdAt.
- **SceneState** (in campaign.json): location, presentNpcIds.
  Persists across turns until the plot stage declares a scene change.

## Core invariant: visibility filter

The scene stage's context contains ONLY:

1. the fresh synopsis from the plot stage,
2. sheets/agency/trackers of NPCs in `presentNpcIds`,
3. narration from past turns where at least one currently present NPC
   was also present.

Player secrets and off-screen events never enter the scene prompt.
This is enforced by the assembler, not by instructions.

## Stage contracts

- **Router** in: playerInput, sceneState, flags. out:
  `{ needs_check: bool, checks: [...], parallel: [...], skip: [...] }`
- **Plot** in: sessionPlan, recent visible summary, routerDecision,
  mechanicResults. out:
  `{ synopsis: str, present_npcs: [id], scene_change: bool, location? }`
- **Scene** in: synopsis, visibility-filtered context, NPC payloads.
  out: prose (markdown).
- **Mechanics**: pure code. Deck draw + modifiers → outcome object
  injected into plot input. Never model-decided.
- **Memory**: extraction pass over the finished turn → memories.jsonl;
  retrieval via tool search or forced top-k injection pre-turn.

## Regenerate / swipe

A turn's variants keep full stage outputs. Regenerate re-runs the
pipeline from that turn's playerInput and appends a new variant.
Deleting a turn truncates later turns (state is derived per turn).

## Providers / BYOK

One settings screen: provider type (openai-compatible | anthropic),
base URL (if applicable), API key, model id (free text + fetch list).
Per-stage model override: plot/router/extraction vs scene.
