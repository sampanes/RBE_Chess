# Handoff -- narrative-sidekick

Single entry point for anyone (incl. future-me) picking this up cold. Read this,
then `SPEC.md`. Status as of 2026-06-08.

## What this is

A design repo for a **local, lightweight spoken narrator** for a Stockfish-backed
Android chess app driven by a BLE (ESP32) keypad. Goal: replace the current app's
verbose, filler-heavy speech with terse, unambiguous narration that says the
essentials first and reveals detail only on demand. **No API, no cloud, on-device.**

It is currently a **spec + research repo** (no code yet, by design -- that was the
agreed first deliverable). The spec is detailed enough to implement against.

## File map

| File | What it is | Status |
|---|---|---|
| `README.md` | Orientation + read order | done |
| `SPEC.md` | The grammar -- section 1-section 14. The implementation target. | done, stable |
| `docs/narrativization.md` | Source-backed research; every claim adversarially verified + cited | done |
| `examples/utterance-catalog.md` | Every utterance type as golden output (= future test fixtures) | done |
| `examples/session-play.md`, `session-suggest.md` | End-to-end sample sessions | done |
| `loose-direction.md` | Original generic Stockfish notes | superseded by SPEC |

## Decisions locked (don't relitigate without reason)

1. **Notation:** plain algebraic spoken ("knight f three"). Phonetic-letter
   override is a config flag, **off** by default. (SPEC section 4)
2. **TTS engine:** deferred. Narrator emits **speech-ready text**; any offline
   engine consumes it. Narrator never sees/owns the engine. (SPEC section 4, section 10)
3. **Architecture:** `narrate(event, level) -> string`, **pure** (no I/O, clock,
   RNG, or chess legality). Only stateful piece is a verbosity counter in a thin
   controller. This is the research-validated "facts first, language last" split
   (SPEC section 10, section 12; `docs/narrativization.md` section 2.1).
4. **Verbosity:** progressive disclosure via the `repeat` chord (L0->L3), **not**
   persistent modes. First-pass utterance is always minimal. (SPEC section 5)
5. **Chords:** repeat, endgame, restart, **undo**. `status` is an optional extra
   (5 buttons -> 10 possible pairs). (SPEC section 6.5, section 11)
6. **Self vs opponent commentary:** comment on the move you could change -- self
   moves flag inaccuracy+, opponent moves flag blunders only (opportunity).
   Suggest mode treats both sides as self. (SPEC section 8.1)
7. **Quality & saliency gate on Win%**, not raw centipawns (Lichess logistic,
   const `0.00368208`; saliency threshold ~ 7.5 Win% pts). (SPEC section 8.2)
8. **Motif naming is DIY** -- no off-the-shelf tool does it; build with cheap
   `python-chess` primitives. Motifs are facts, never guesses; silence beats a
   wrong "fork!". (SPEC section 13; `docs/narrativization.md` section 3)
9. **Optional tiny LLM** only *rephrases* pre-computed facts, gated behind
   `repeat` at L3, fully guarded with silent fallback to templates. Never on the
   critical path. (SPEC section 12)

## Open questions (tracked in SPEC section 11)

- **Cross-move trend narration** depth (specced in section 14, but tuning the arc
  buckets needs real on-device listening).
- **Phonetic-letters default** -- revisit after hearing plain algebraic on the S22U.
- **`status` chord** -- confirm whether it's wanted as the 5th chord.
- **Opponent moves at L1** -- current call: blunders break silence. Alternative:
  total silence at L1, let L2 eval reveal the swing. One-line change in section 8.1.

## Implementation status (2026-10-09)

Steps 1-3 below are implemented in Kotlin inside the app, with golden tests:

- `app/.../chess/Position.kt` -- legal-move generator (perft-verified against the
  chessprogramming reference counts) standing in for python-chess primitives.
- `app/.../chess/San.kt` -- UCI -> SAN with minimal disambiguation and +/#.
- `app/.../narrator/` -- `Narrator.narrate(event, level)` (L0-L3, all event
  types), `SpokenSan` (section 4/7 expansion, phonetic hook), `MoveQuality` +
  `WinPercent` (section 8/8.2), `MotifDetector` (fork, pin, hanging, plus a
  simple even-trade check), `NarrationFacts` / `AnalysisFacts` (app-side
  fact builders).
- `NarratorGoldenTest` pins every catalog row and the session-play transcript.
  Two catalog rows were self-contradictory and were corrected in
  `examples/utterance-catalog.md` (the -15 cp engine rows now read
  "minus 0.2" at L2 and L3 per section 9.1 rounding; the unproven-sacrifice
  row now flags the self blunder at L1 per section 8.1).
- Wired in as the **repeat ladder**: the first repeat press is the app's
  classic replay; presses 2 and 3 speak this narrator's L2 and L3. The
  default first-pass speech is unchanged -- swapping it for L1 is a product
  call to make after hearing L2/L3 on the phone.

Not done: ENTRY/L1 as the primary speech, the `status` chord, SEE-based
material motifs, sequence narration, the LLM tier.

## Recommended next steps (in order)

1. ~~v1 motif detectors~~ -- done (see above).
2. ~~Pure `narrate(event, level)`~~ -- done for L0-L3.
3. ~~Win% + quality bucketing~~ -- done; quality currently keys on cpLoss from
   the app's 600 ms analyses (`WinPercent.classifyDrop` is ready to swap in).
4. **SEE-based v1.5 motifs** (trade / wins-material / sacrifice).
5. **Sequence narration** (SPEC section 14) once per-move motifs + Win% history exist.
6. **Optional LLM tier** (SPEC section 12) -- last, only after the core feels good on-device.

Ship order mirrors the tiers: deterministic core -> cheap motifs -> SEE motifs ->
sequence -> optional LLM. Each layer works without the next.

## Notes for the implementer

- Golden tests are cheap and exhaustive because `narrate` is pure -- same input +
  level => byte-identical string. Use `examples/utterance-catalog.md` directly.
- The app layer (not the narrator) owns: BLE input, Stockfish, board state, SAN
  derivation, Win%/SEE/motif computation. The narrator only phrases what it's handed.
- Language for the reference module is open; the contract (SPEC section 10) is language-
  agnostic. Kotlin (native Android) or a small pure-Kotlin module is the obvious fit.
