# narrative-sidekick

Designing a **local, lightweight spoken narrator** for a Stockfish-backed Android
chess app driven by a BLE (ESP32) keypad. No API, no cloud, no LLM — deterministic
string assembly that any offline TTS can read.

The core idea: the narrator is a **pure function `narrate(event, level) -> string`**.
The phone says the bare essentials first (the move, the engine's reply); a `repeat`
chord climbs a verbosity ladder to pull in eval, move-quality, and lines on demand.
This is the fix for the current app's "too many little phrases" problem.

> **Picking this up cold?** Start with **[`HANDOFF.md`](HANDOFF.md)** — current
> status, locked decisions, open questions, and the ordered next steps.

## Read in this order

1. **[`SPEC.md`](SPEC.md)** — the grammar. Interaction loop, verbosity ladder,
   utterance templates, SAN→speech rules, eval/Win% mapping, **motif detection
   (§13)**, **sequence narration (§14)**, the optional LLM tier (§12), the
   function contract.
2. **[`docs/narrativization.md`](docs/narrativization.md)** — source-backed
   research: how chess commentary works, the validated facts-first architecture,
   the motif taxonomy + how to detect each locally, Win%-based quality/saliency.
   Every claim adversarially verified; cited.
3. **[`examples/utterance-catalog.md`](examples/utterance-catalog.md)** — every
   utterance type as golden output (= future test fixtures).
4. **[`examples/session-play.md`](examples/session-play.md)** /
   **[`examples/session-suggest.md`](examples/session-suggest.md)** — end-to-end
   sample sessions.
5. `loose-direction.md` — original generic Stockfish notes (superseded by SPEC).

A tiny on-device LLM (S22U-capable) is specced as an **optional L3 "explain"
tier** in SPEC §12 — bolt-on, fact-only, guarded, never on the critical path.

## Decisions locked

- Spoken notation: **plain algebraic** ("knight f three"); phonetic letters a
  config flag, off by default.
- TTS engine: **decided later** — narrator emits engine-agnostic speech-ready text.
- Verbosity: **progressive disclosure** via the `repeat` chord, not persistent modes.

## Open (see SPEC §11)

Cross-move trend narration, suggest-mode quality scope, phonetic-letters default.
(Chord-4 = `undo` and end-game phrasing are now decided.)
