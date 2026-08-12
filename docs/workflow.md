# Workflow / division of labor

## Roles

- **fable 5 (via UnoRouter)** — implementation worker. Writes code,
  edits files, runs and watches CI.
- **lira** — coordinator and reviewer. Owns these docs, reviews
  every change, and is the only one who commits and pushes.
- **za** — product owner. Decides at phase gates. Runtime-tests
  builds.

## Hard rules for workers

- NEVER commit, push, or mutate git. Branch state is lira's.
- May run and watch CI: `gh workflow run ...`, `gh run watch/list/
  view`. Iterate on CI failures by editing files; lira commits.
- Implement against these docs. If a doc is ambiguous, stop and
  report the ambiguity instead of guessing.
- No unrelated refactors. Touch only what the phase task names.
- Default max_trips (32). Small trip budgets starve the worker.

## Phase gates

Each phase ends with: lira review → commit → CI green → za confirms
before the next phase starts. No auto-continue across phases.

0. Project identity + docs (this set). DONE.
1. Gradle scaffold (app + ai modules), `ai` port per
   ai-port-map.md, hello-world streaming call, compile-check CI.
2. Storage layer + data model per storage.md, with unit tests.
3. Engine core: pipeline stages per pipeline.md, visibility
   assembler, mechanics. Headless-testable.
4. Story screen + theme per ui-theme.md, markdown renderer,
   affordances (stop, swipe, edit, delete).
5. Campaign flow: create/list/resume, session plan generation,
   NPC sheets + card import.
6. Memory extraction + retrieval wiring.
7. Polish, release workflow, signed APK.

## Review checklist (lira, per change)

1. Conforms to the named doc sections; deviations are named in the
   commit message.
2. CI compile-check green, unit tests included for logic.
3. No drive-by changes outside phase scope.
4. Dependency budget respected (see README tech list): any new
   dependency needs za's sign-off in the phase report.
