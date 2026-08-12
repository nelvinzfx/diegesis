# Pipeline specification

Five stages. Router, plot, agency, and extraction run on the cheap
("think") model. Scene runs on the strong ("write") model. Mechanics
is pure code.

All structured stages MUST return JSON only. On parse failure: retry
once with "Return valid JSON only, no prose." On second failure use
the stage's documented fallback. Never crash a turn over a stage
failure.

## 1. Router (cheap, per turn)

Input: playerInput, sceneState (location, presentNpcIds), flags.

Output:
```json
{
  "needs_check": false,
  "checks": [{ "skill": "string", "dc": 5, "modifier": 0, "advantage": 0 }],
  "run_agency_update": false,
  "lore_query": null
}
```
- `dc` 3..18, `advantage` -1|0|1 (draw 2 take lower / normal / higher).
- Fallback: `needs_check=false`, everything else empty.

## 2. Mechanics (code, no model)

Deck: standard 52 cards. Rank value 2..14 (J=11, Q=12, K=13, A=14).
Draw 1 (or 2 with advantage/disadvantage, take higher/lower).

Result tiers vs DC:
- value + modifier ≥ DC + 5 → `critical_success`
- value + modifier ≥ DC → `success`
- value + modifier ≥ DC - 3 → `partial`
- else → `failure`

Output per check: `{ skill, dc, modifier, drawn: [{rank, suit}],
value, tier }`. Injected into the plot stage input verbatim. The model
narrates it; it may not contradict the tier.

## 3. Plot (cheap, per turn)

System prompt (template, `{...}` filled by assembler):
```
You are the plot engine of a tabletop campaign. You decide WHAT
happens, never how it is told.

Session plan (the arc to follow):
{sessionPlan}

Story so far (compressed):
{recentSummary}

Rules:
- Advance the arc. Do not stall, do not repeat beats.
- End every beat ON MAXIMUM CONFLICT. Whatever the situation, add
  pressure. Slice of life: add friction. Conversation: escalate.
- If mechanic results are provided, the synopsis MUST honor their
  tiers exactly.
- Nominate which NPCs are physically present. NPCs not listed leave
  the scene.
- Reply with JSON only.
```

User payload: playerInput + mechanicResults (if any) + retrieved
memories.

Output:
```json
{
  "synopsis": "string, 2-6 sentences, what happens in this beat",
  "present_npcs": ["npcId"],
  "scene_change": false,
  "location": null,
  "tracker_updates": [{ "npc": "npcId", "key": "trust", "delta": -1 }]
}
```
Fallback: keep previous sceneState, synopsis = "The moment stretches;
the situation stays tense." and let the scene stage improvise.

## 4. Agency (cheap, conditional)

Runs when router says so, when a scene change happens, or when a
tracker crosses a sign change. Per affected NPC:

```
You maintain the inner life of an NPC. Given what THIS NPC has
witnessed (below) and their current goal, produce their updated
immediate goal and emotional stance. JSON only:
{ "goal": "...", "stance": "...", "will_act_on": "..." }
```
Input context is filtered to that NPC's witnessed turns only.

## 5. Scene (strong, per turn)

System: narrator voice instructions (campaign-configurable; default:
second person, present tense, literary but direct, dialog in quotes,
never decide player actions).

User payload assembled in this order:
1. Synopsis (from plot, verbatim).
2. Mechanic outcomes (if any): "This happened: {tier} on {skill}.
   Narrate it accordingly."
3. NPC payload per present NPC: sheet, agency, voice examples,
   trackers.
4. Visibility-filtered history (see architecture.md invariant).
5. Retrieved memories.
6. playerInput.

Output: markdown prose. This is the only stage whose text reaches
the story screen (plus a compact mechanics badge when checks ran).

## 6. Memory extraction (cheap, post-turn)

```
Extract durable facts from this turn worth remembering across
sessions: revelations, decisions, relationships changes, promises,
names, places. Ignore transient detail. JSON array only:
[{ "scope": "campaign" | "npc", "npc_id": null, "fact": "..." }]
```
Appended to memories.jsonl with turn reference and timestamp.

## Retrieval (v1)

Forced top-k (k=5): score memories by BM25-ish term overlap against
(playerInput + synopsis), inject the top 5 into plot and scene
payloads. Dedup by exact text. Skip when memories.jsonl < 10 entries.
