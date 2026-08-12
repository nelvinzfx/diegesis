# Diegesis scope (v1)

## Goal

A playable single-player AI campaign loop on Android: create a
campaign, get a session plan, play turns through the five-stage
pipeline, with NPCs that have agency, memory, and no omniscience.

## In scope (v1)

- BYOK settings: OpenAI-compatible (chat completions) and Anthropic.
  Per-stage model override (think stages vs scene stage).
- Campaign management: create (premise → generated session plan,
  editable), list, resume, delete.
- Story screen: markdown prose, player input, streaming output,
  stop, regenerate with swipe variants, edit+resend, delete turn.
- Full pipeline: router, mechanics (card deck), plot, scene, agency
  updates, memory extraction, forced top-k memory retrieval.
- NPC sheets: manual creation + character card v2 import
  (JSON file or PNG with embedded `chara` tEXt chunk).
- Scene membership persistence + visibility-filtered context
  assembly (the core invariant, see architecture.md).
- JSON/JSONL storage in app-private files. No runtime permissions.

## v1 simplifications (deliberate)

- Memory retrieval = forced top-k injection only. Tool-call search
  comes later.
- Trackers are a free-form string→value map updated by the plot
  stage, not a typed system.
- One deck: standard 52 cards, fixed difficulty table (see
  pipeline.md). No custom decks yet.
- Single player persona (free text in campaign settings). No
  multi-character play.

## Non-goals (v1)

- Light theme. Google/AICore/Vertex providers. Responses API.
- Sync, backup, export. TTS/STT. Image input/output. MCP.
- In-app subagents. Plugin systems. Play Store release.
- i18n (English UI only).
