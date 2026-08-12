# UI / theme specification

## Theme

Dark only. No light scheme, no system follow, no toggle.

Monochrome near-black base. It should read as "almost pure black"
without ever being #000000.

| Token | Value | Use |
|---|---|---|
| `bg` | #0A0A0B | app background |
| `surface` | #121214 | cards, sheets, input bar |
| `surface2` | #18181B | elevated elements, dialogs |
| `border` | #1E1E20 | hairline dividers, outlines |
| `text` | #E8E8EA | primary |
| `textDim` | #9A9AA0 | secondary, metadata |
| `textFaint` | #5C5C62 | timestamps, hints |

Vibrant accents, used ONLY when they carry meaning:

| Token | Value | Use |
|---|---|---|
| `amber` | #FFB020 | mechanics results, deck badge |
| `cyan` | #22D3EE | NPC names in prose, sheet links |
| `red` | #F87171 | stop, delete, danger |
| `green` | #34D399 | success tiers, confirmations |

Everything else stays gray. An accent appearing must feel like an
event.

Typography: system sans (Inter-style defaults), body 16sp/1.5,
prose 17sp/1.65. Monospace for mechanics badge and code spans.

## Markdown

Parser: `com.github.rikkahub:markdown:d79a97cc8e` (jitpack).
Renderer: own thin Compose renderer. Supported: bold, italic,
headers (h1-h3), lists, blockquotes, code spans/blocks, links,
horizontal rules. No tables, no images, no HTML.

## Screens

1. **Settings / BYOK** — provider type (openai-compatible |
   anthropic), base URL, API key, model id (free text + "fetch
   models" button hitting /models). Per-stage override section:
   think model vs write model.
2. **Campaign list** — cards: title, premise snippet, turn count,
   last played. FAB to create.
3. **Campaign create** — premise text field → "generate session
   plan" (think model) → editable plan → create.
4. **Story screen** — the core. Turn list (prose blocks), mechanics
   badge inline where checks ran, input bar with send/stop toggle.
   Streaming renders progressively.
5. **NPC sheet** — per-NPC view: sheet, agency, trackers, voice.
   Read + edit. Accessible from a drawer in the story screen.
6. **Memories** — simple list of extracted facts, deletable.

## Story screen affordances

- While streaming: send button becomes stop (red). Partial text is
  kept as a variant marked "interrupted".
- Long-press a turn: menu = Regenerate, Edit+resend (user turns),
  Delete turn (with confirm; truncates later turns), View stage
  details (debug: synopsis, router decision, checks).
- Variants: ‹ 1/3 › pager on the assistant turn. Regenerate appends
  a variant and moves to it.
- Mechanics badge: amber chip, e.g. "✦ success · stealth (13 vs 10)".
  Tapping expands the draw details.

## Motion

Minimal. 150-200ms fades for turn appearance, streaming text appears
without animation. No springy gimmicks.
