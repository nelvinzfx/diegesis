# Diegesis

> diegesis (n.): the world a story happens in, and everything its characters can truly know.

Diegesis is a multi-stage narrative engine for Android. It does not run a campaign as a chat with a single model. Every turn is a pipeline: a planner decides what happens, a writer renders the scene, and each character only knows what they were present to witness.

## Why

Chat-style roleplay frontends collapse under long campaigns: NPCs turn omniscient, plots go flat, and context windows drown. Diegesis separates cognition from prose and treats information asymmetry as architecture, not as a prompt instruction.

## The pipeline

1. **Router** — inspects the turn, decides whether skill checks are needed, toggles stages, runs independent stages in parallel.
2. **Plot** (cheap model) — reads the session plan and current state, emits a structured synopsis of the next beat plus the list of NPCs present. Core directive: end on maximum conflict.
3. **Scene** (strong model) — receives the synopsis plus only the context the present NPCs could have witnessed. Loads NPC sheets, relationship/resource trackers, agency text (what this NPC currently wants), and voice samples, then writes narration and dialog.
4. **Mechanics** — card/deck-based skill checks resolved deterministically outside the model. The model narrates outcomes; it never decides them.
5. **Memory** — post-turn extraction and retrieval, either on-demand via tool search or forced surfacing before each turn.

## Principles

- **Information asymmetry is architecture.** Off-screen events and player secrets never enter the scene model's context. Not "instructed not to reveal": structurally absent.
- **Structured output is the state machine.** Scene membership, trackers, and goals are stored per turn. Prose is a side effect.
- **Mechanics are deterministic.** Cards are drawn by code. The model narrates the result.
- **Cheap models think, strong models write.** Plot and routing run on flash-class models. Final prose runs on your best model.

## Tech

- Kotlin + Jetpack Compose. Dark-only, near-black monochrome UI with sparse vibrant accents.
- BYOK: OpenAI-compatible and Anthropic providers only.
- AI layer adapted from RikkaHub's `:ai` module, stripped to two providers with shared helpers inlined.
- Markdown rendering, character card v2 import (JSON or PNG-embedded) as NPC sheet seeds.
- Plain JSON/JSONL storage. No ORM, no ksp. Fast builds.
- Built with GitHub Actions.

## Status

Early scaffold. Nothing playable yet.

## License & attribution

AGPL v3 (non-commercial use). The AI provider layer is adapted from RikkaHub. See [NOTICE](NOTICE).
