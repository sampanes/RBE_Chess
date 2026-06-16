# Handoff — narrative-sidekick

Single entry point for anyone (incl. future-me) picking this up cold. Read this,
then `SPEC.md`. Status as of 2026-06-08.

## What this is

A design repo for a **local, lightweight spoken narrator** for a Stockfish-backed
Android chess app driven by a BLE (ESP32) keypad. Goal: replace the current app's
verbose, filler-heavy speech with terse, unambiguous narration that says the
essentials first and reveals detail only on demand. **No API, no cloud, on-device.**

It is currently a **spec + research repo** (no code yet, by design — that was the
agreed first deliverable). The spec is detailed enough to implement against.

## File map

| File | What it is | Status |
|---|---|---|
| `README.md` | Orientation + read order | done |
| `SPEC.md` | The grammar — §1–§14. The implementation target. | done, stable |
| `docs/narrativization.md` | Source-backed research; every claim adversarially verified + cited | done |
| `examples/utterance-catalog.md` | Every utterance type as golden output (= future test fixtures) | done |
| `examples/session-play.md`, `session-suggest.md` | End-to-end sample sessions | done |
| `loose-direction.md` | Original generic Stockfish notes | superseded by SPEC |

## Decisions locked (don't relitigate without reason)

1. **Notation:** plain algebraic spoken ("knight f three"). Phonetic-letter
   override is a config flag, **off** by default. (SPEC §4)
2. **TTS engine:** deferred. Narrator emits **speech-ready text**; any offline
   engine consumes it. Narrator never sees/owns the engine. (SPEC §4, §10)
3. **Architecture:** `narrate(event, level) -> string`, **pure** (no I/O, clock,
   RNG, or chess legality). Only stateful piece is a verbosity counter in a thin
   controller. This is the research-validated "facts first, language last" split
   (SPEC §10, §12; `docs/narrativization.md` §2.1).
4. **Verbosity:** progressive disclosure via the `repeat` chord (L0→L3), **not**
   persistent modes. First-pass utterance is always minimal. (SPEC §5)
5. **Chords:** repeat, endgame, restart, **undo**. `status` is an optional extra
   (5 buttons → 10 possible pairs). (SPEC §6.5, §11)
6. **Self vs opponent commentary:** comment on the move you could change — self
   moves flag inaccuracy+, opponent moves flag blunders only (opportunity).
   Suggest mode treats both sides as self. (SPEC §8.1)
7. **Quality & saliency gate on Win%**, not raw centipawns (Lichess logistic,
   const `0.00368208`; saliency threshold ≈ 7.5 Win% pts). (SPEC §8.2)
8. **Motif naming is DIY** — no off-the-shelf tool does it; build with cheap
   `python-chess` primitives. Motifs are facts, never guesses; silence beats a
   wrong "fork!". (SPEC §13; `docs/narrativization.md` §3)
9. **Optional tiny LLM** only *rephrases* pre-computed facts, gated behind
   `repeat` at L3, fully guarded with silent fallback to templates. Never on the
   critical path. (SPEC §12)

## Open questions (tracked in SPEC §11)

- **Cross-move trend narration** depth (specced in §14, but tuning the arc
  buckets needs real on-device listening).
- **Phonetic-letters default** — revisit after hearing plain algebraic on the S22U.
- **`status` chord** — confirm whether it's wanted as the 5th chord.
- **Opponent moves at L1** — current call: blunders break silence. Alternative:
  total silence at L1, let L2 eval reveal the swing. One-line change in §8.1.

## Recommended next steps (in order)

1. **v1 motif detectors as pseudocode/reference code.** The critical path. Research
   handed us the exact `python-chess` calls (SPEC §13.1 cheap tier: capture,
   check/mate, pin via `is_pinned`/`pin`, fork via multi-target `attacks()`,
   hanging via `attackers()` defenders-vs-threats). Write these as a small pure
   module + golden tests against `examples/`. **This was the agreed next build
   step when work paused.**
2. **Implement the pure `narrate(event, level)`** core for L0–L1 (entry, confirm,
   engine move) against the golden catalog — the deterministic backbone.
3. **Win% + quality bucketing** (SPEC §8.2) as a tiny computation the app feeds in.
4. **SEE-based v1.5 motifs** (trade / wins-material / sacrifice).
5. **Sequence narration** (SPEC §14) once per-move motifs + Win% history exist.
6. **Optional LLM tier** (SPEC §12) — last, only after the core feels good on-device.

Ship order mirrors the tiers: deterministic core → cheap motifs → SEE motifs →
sequence → optional LLM. Each layer works without the next.

## Notes for the implementer

- Golden tests are cheap and exhaustive because `narrate` is pure — same input +
  level ⇒ byte-identical string. Use `examples/utterance-catalog.md` directly.
- The app layer (not the narrator) owns: BLE input, Stockfish, board state, SAN
  derivation, Win%/SEE/motif computation. The narrator only phrases what it's handed.
- Language for the reference module is open; the contract (SPEC §10) is language-
  agnostic. Kotlin (native Android) or a small pure-Kotlin module is the obvious fit.
