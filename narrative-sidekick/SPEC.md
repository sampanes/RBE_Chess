# Narrative Sidekick — Narration Spec

A local, lightweight spoken-narration layer for a Stockfish-backed Android chess
app driven by a BLE (ESP32) keypad. This document nails down **exactly what the
phone says out loud, when, and how tersely** — it is a grammar, not theory.

> The generic Stockfish-narrative notes live in `loose-direction.md`. This
> supersedes them for our setup.

---

## 1. Purpose & non-goals

**Purpose:** turn game events (a parsed move, an engine suggestion, an eval) into
short, unambiguous spoken English, on-device, with no network calls.

**Non-goals:**
- No LLM, no API, no cloud. Everything here is deterministic string assembly.
- No "color commentary." We are a referee/coach with a tight mouth, not ESPN.
- The narrator does **not** talk to Stockfish, the BLE keypad, or the TTS engine.
  It is a pure function in the middle: `event -> string`.

**Anti-goal we are actively fixing:** the current app sprinkles filler phrases
("alright, let's see...", "the engine thinks that maybe...") that muddy the
audio. Every token in an utterance must earn its place. Default to silence.

---

## 2. Hard constraints

| Constraint | Consequence in this spec |
|---|---|
| Runs on a small Android phone | No heavy NLP. Template + lookup tables only. |
| Fully offline | No API. Deterministic output. |
| As lightweight as possible | Narrator is a pure function; zero state beyond a verbosity counter. |
| Engine-agnostic TTS (decided later) | Narrator emits **speech-ready tokens**, never raw notation (see §4). |
| Eyes-free / audio-only use | Side-to-move is stated when load-bearing; numbers spoken as words. |

---

## 3. The interaction loop (states & events)

The keypad has **4 increment buttons** (start-row, start-col, end-row, end-col),
a **5th submit button**, and **chords** (button pairs) for control actions.

```
        ┌─────────────────────────────────────────────────────────┐
        │                                                           │
        ▼                                                           │
   [ENTRY]  ──increment buttons──►  speaks ENTRY utterance (L0)     │
        │                                                           │
   submit (5th button)                                              │
        │                                                           │
        ▼                                                           │
   [CONFIRM] ──► speaks the move just entered (L1)                  │
        │                                                           │
        ▼                                                           │
   [ENGINE]  ──► speaks Stockfish's favored move (L1)               │
        │                                                           │
        ▼                                                           │
   [IDLE] ◄── you sit here. Chords available: ──────────────────────┘
        repeat   → re-narrate CONFIRM+ENGINE at next verbosity level (L2, L3…)
        endgame  → SYSTEM utterance, stop
        restart  → SYSTEM utterance, new game
        undo     → SYSTEM utterance, revert last move
        (status) → optional extra chord (5 buttons = 10 possible pairs); speaks eval/side
```

**Events the narrator must handle** (full schema in §10):

| Event | When | Default level |
|---|---|---|
| `entry` | each increment button press | L0 |
| `move` | submit accepted, move parsed | L1 |
| `engine` | Stockfish returns its pick | L1 |
| `system` | a control chord fires | n/a (fixed phrase) |

---

## 4. Core principle: emit speech-ready tokens, never raw notation

A cheap offline TTS will mangle `Nf3#`. It may read `f3` as "thirty-three", `B`
as "bee", `x` as "ecks". **The narrator never hands raw notation to the engine.**
It expands everything to words the dumbest TTS reads correctly:

- Pieces → full words: `N`→"knight", `B`→"bishop", `R`→"rook", `Q`→"queen",
  `K`→"king". Pawn → no piece word (or "pawn" when expanded; see §7).
- Files stay as single lowercase letters (`e`), digits become **words**
  (`3`→"three"). So `f3` → `"f three"`.
- `x`→"takes", `+`→"check", `#`→"checkmate", `=Q`→"promotes to queen",
  `O-O`→"castles kingside", `O-O-O`→"castles queenside".

> **Phonetic override hook (off by default).** You chose plain algebraic. If a
> letter proves ambiguous aloud, a single config flag swaps the file-letter
> lookup table from `{a:"a", b:"b", …}` to NATO `{a:"alpha", b:"bravo", …}`.
> Plain stays the default; nothing else in the grammar changes.

The narrator's output is one clean string per utterance. (Optional SSML is a
later, engine-specific concern — see §10. Default output is plain text.)

---

## 5. Verbosity ladder (the `repeat` chord drives this)

Verbosity is **not** a persistent mode. It is a counter that starts at L1 on each
new move and climbs one rung every time you press `repeat` while IDLE. Submitting
the next move resets it to L1.

| Level | Trigger | Adds |
|---|---|---|
| **L0** | live entry | the square under the field being changed, nothing else |
| **L1** | submit (default) | the move; the engine's move; a quality flag **only if** mistake/blunder |
| **L2** | 1st `repeat` | + numeric eval; + explicit move-quality (even when fine) |
| **L3** | 2nd `repeat` | + engine's principal line (next 1–2 moves); + alternative (MultiPV #2); + WDL if decisive |
| **L3+** | further `repeat` | re-speak L3 verbatim (no new info; capped) |

Design intent: the **first thing you hear is never cluttered**. Detail is pull,
not push. This is the direct fix for the "too many little phrases" problem.

---

## 6. Utterance taxonomy

Each utterance type below gives: the **template** (slots in `{braces}`), the
**rules**, and **examples** at each relevant level. Brackets `[…]` mean optional
slot (omitted when empty/not notable).

### 6.1 ENTRY (L0) — live feedback while incrementing

Spoken on **every** increment press; must be ≤3 words so it keeps up with fast
button taps. Speak only the field that changed.

```
template:  "{field} {square}"
field:     "from"  (start-row or start-col changed)
           "to"    (end-row or end-col changed)
square:    algebraic, spoken per §4   e.g. "e two"
```

Examples (pressing start-col until it reads e2, then end fields to e4):

```
"from e two"
"to e four"
```

> Rationale: your fingers move row/col, but the readback is the *resulting
> square* in the same notation as everything else, so entry and confirmation
> speak the same language. No "row… column…" verbosity.

### 6.2 CONFIRM — the move that was submitted

Spoken once on submit. This is the "recite back what happened" step, trimmed.

```
template (L1):  "{side} {move}[, {quality}]"
template (L2):  "{side} {move}. {quality_full}."
side:           "white" | "black"   (always stated — cheap, prevents aural disorientation)
move:           SAN expanded per §7
quality:        L1 → spoken ONLY per §8 + §8.1: self moves flag inaccuracy+,
                     opponent moves flag blunder only; else omitted
quality_full:   L2+ → always spoken (either mover)
```

Examples:

```
L1, ordinary:   "white knight f three."
L1, capture+chk:"black bishop takes c six, check."
L1, blunder:    "white queen takes a seven, blunder."
L2:             "white knight f three. best move."
L2, lost pawn:  "white bishop b five. inaccuracy, lost about a third of a pawn."
```

### 6.3 ENGINE — Stockfish's favored move

Spoken once, right after CONFIRM. **L1 is the move and nothing else.**

```
template (L1):  "engine: {move}."
template (L2):  "engine: {move}, {eval}."
template (L3):  "engine: {move}, {eval}. then {line}. or {alt}.[ {wdl}]"
move:           SAN expanded per §7 (engine output is UCI → convert to SAN → expand)
eval:           per §9
line:           next 1–2 plies of the principal variation, expanded   e.g. "d six, then knight c three"
alt:            MultiPV #2 move + its eval                              e.g. "or c four, plus 0.2"
wdl:            spoken only when decisive (see §9.3)
```

Examples:

```
L1:   "engine: e four."
L2:   "engine: e four, plus 0.3."
L3:   "engine: e four, plus 0.3. then e five, then knight f three. or c four, plus 0.2."
L3*:  "engine: queen h five, mate in three."   (mate short-circuits eval; see §9.2)
```

### 6.4 REPEAT — re-narration on chord

`repeat` does not have its own words. It **re-emits CONFIRM then ENGINE at the
next verbosity level** (§5). No "here it is again" preamble — that is exactly the
filler we are removing.

### 6.5 SYSTEM — control chords

Fixed, tiny phrases. No variation, no verbosity ladder.

```
endgame:  "game over."           result optional — a bare phrase is enough (it
                                  doubles as a "the chord fired" debug ping).
restart:  "new game."
undo:     "undo."                or "took back {move}." if the app passes the
                                  reverted move (lets you re-orient by ear).
status:   "{side} to move, {eval}, move {n}."   optional extra chord (§11).
```

---

## 7. SAN → speech conversion rules

Input to the narrator for a move is **SAN** (derive it from the engine's UCI +
board state in the app layer; the narrator does not compute legality). Expansion:

| SAN element | Spoken | Notes |
|---|---|---|
| piece letter `K Q R B N` | king / queen / rook / bishop / knight | |
| (no piece letter) | *omitted* (L1) / "pawn" (L2+) | a pawn move is just its square at L1 |
| destination square `e4` | "e four" | file letter + digit-word (§4) |
| capture `x` | "takes" | `Bxc6` → "bishop takes c six" |
| check `+` | ", check" | trailing |
| checkmate `#` | ", checkmate" | trailing; overrides check |
| castle `O-O` | "castles kingside" | |
| castle `O-O-O` | "castles queenside" | |
| promotion `=Q` | "promotes to queen" | `e8=Q` → "e eight promotes to queen" |
| en passant | "takes {sq} en passant" | L2+ only adds "en passant"; L1 just "takes {sq}" |
| disambiguation `Nbd2` | "knight from {b} to {d two}" | the disambiguator becomes "from {file-or-rank}" |
| disambiguation `R1e2` | "rook from rank one to e two" | rank disambiguator → "from rank {n}" |

Ordering of a fully-expanded move: `[side] piece [from-disambig] [takes] dest [promotion] [, check/checkmate]`.

Worked examples:

```
e4        →  "e four"                              (pawn, L1)
e4        →  "pawn e four"                          (L2+)
Nf3       →  "knight f three"
Bxc6+     →  "bishop takes c six, check"
exd5      →  "e takes d five"                       (pawn capture keeps source file)
O-O       →  "castles kingside"
e8=Q#     →  "e eight promotes to queen, checkmate"
Nbd2      →  "knight from b to d two"
Qh5xf7#   →  "queen from h five takes f seven, checkmate"
```

---

## 8. Move-quality (delta) classification

Quality = how much worse the **played** move is than the engine's **best** move,
measured in centipawns lost, both evaluated from the **mover's** perspective
(see §9.1 for sign convention). The app supplies `cpLoss` (≥0) and `isBest`.

| cpLoss (centipawns) | Label | L1 behavior |
|---|---|---|
| move == engine's #1 (`isBest`) | "best move" | silent at L1 |
| 0–20 | "accurate" | silent |
| 20–50 | "fine" | silent |
| 50–120 | "inaccuracy" | **spoken** |
| 120–250 | "mistake" | **spoken** |
| > 250 (non-mate) | "blunder" | **spoken** |
| allowed/missed forced mate | "blunder, missed mate" / "allowed mate" | **spoken** |

- **L1:** append `, {label}` to CONFIRM **only** for inaccuracy and worse. Good
  moves pass in silence — that is the point.
- **L2+:** always append the label plus a magnitude phrase:
  `"{label}, lost about {pawns} of a pawn"` where pawns ∈ {"a tenth", "a third",
  "half", "a pawn", "{n} pawns"} (round `cpLoss/100`, speak fractions for <1).

> The app owns the threshold table as config so you can retune without touching
> the grammar.

### 8.1 Self vs opponent — who gets commentary

Principle: **comment on the move you could have changed.** You don't pick the
opponent's move, so flagging their inaccuracies is noise — the engine's
suggestion already tells you the best reply. But an opponent **blunder** is an
*opportunity*, worth a nudge. So quality commentary is asymmetric by mover.

Each `move` event carries `self: boolean` (the app sets it; see derivation
below). Policy at L1:

| mover | flags spoken at L1 |
|---|---|
| **self** (`self: true`) | inaccuracy and worse — full §8 table |
| **opponent** (`self: false`) | **blunder only** (incl. allowed/missed mate); inaccuracy & mistake stay silent |

At L2+ (you pulled detail with `repeat`), both movers get the full label +
magnitude — if you asked, you get it.

**Deriving `self`:**
- **Play mode:** the app knows which colour is you (`you: "w"|"b"`), so
  `self = (mover === you)`.
- **Suggest mode:** you are choosing *both* sides as a study tool, so every move
  is effectively self → `self = true` for both. (This matches your hunch: in
  suggest mode there is no "opponent" whose hand you can't stay.)
- **Unknown / undelineated:** default `self = true` (over-informing about errors
  beats missing them; flip per-deployment if it gets chatty).

The **engine** utterance (§6.3) is unaffected — it is always the actionable next
move, for whichever side is to move, regardless of who just moved.

### 8.2 Quality threshold on Win%, not raw centipawns (research-backed)

> Backed by `docs/narrativization.md` §4. Existing annotators (python-chess-
> annotator, chess-artist) and Lichess/Chess.com all gate on **win-probability
> change**, because raw centipawn loss is non-linear in practical importance —
> dropping 100 cp at +0.2 is a real swing; dropping 100 cp at +6.0 is nothing.

The app should convert each centipawn eval to a **Win%** before classifying, using
the Lichess logistic (constant verified `0.00368208`):

```
Win%(cp) = 50 + 50 * ( 2 / (1 + exp(-0.00368208 * cp)) - 1 )
```

Then:
- **Move quality** (§8) = drop in Win% from the best move to the played move
  (`winBest - winPlayed`), not `cpLoss`. Suggested buckets (tunable config):
  ≥ ~20 pts → blunder, ~10–20 → mistake, ~5–10 → inaccuracy, < 5 → fine/best.
- **Saliency gate** (used by §14): a move/event is "worth mentioning" only if its
  Win% delta clears a threshold. python-chess-annotator's default is **7.5 Win%
  points** — a good starting value.

`cpLoss` stays in the `move` event for back-compat/debug, but `winDrop` is the
field the grammar should key on. The app computes both; the narrator just reads
the bucket label. (Raw-cp thresholds from §8 remain a valid lightweight fallback
if Win% conversion is ever undesirable.)

---

## 9. Eval → phrase mapping

### 9.1 Sign convention (decide once, here)

**All evals are spoken from the perspective of the side to move** — i.e. the side
the engine is advising / the side that just moved, normalized so **positive =
that side is better**. This matches Stockfish's native UCI `score cp` convention
(already side-to-move-relative), so the app passes it through unchanged.

Spoken as a **signed decimal pawn value**: `cp/100`, one decimal, with the word
"plus" or "minus". Zero-ish is spoken as "level".

```
+30 cp  → "plus 0.3"
-145 cp → "minus 1.5"
+5 cp   → "level"
```

### 9.2 Mate scores

Mate short-circuits the decimal entirely:

```
mate in 3 (winning) → "mate in three"
mate in 2 (losing)  → "getting mated in two"
```

### 9.3 Optional coarse bucket (L1 status chord / when numbers feel noisy)

For the `status` chord and any place a number is overkill, a one-word bucket may
replace the decimal (config toggle, default = speak the number):

| |cp| | bucket |
|---|---|
| < 30 | "level" |
| 30–90 | "slight edge" |
| 90–250 | "clear advantage" |
| 250–600 | "winning" |
| > 600 | "decisive" |

### 9.4 WDL (L3 only, decisive positions only)

WDL (`info string wdl W D L`, per-1000) is **opt-in noise**. Speak it only at L3
**and** only when one outcome dominates (≥800), as a single clause:

```
wdl 850 130 20 → "winning four times out of five"   (rounded, side-to-move)
```

Otherwise omit. (The "sudden draw-prob collapse = volatility" idea from the loose
doc is interesting but is *trend* analysis across moves — out of scope for a
single-utterance narrator. Tracked in §11.)

---

## 10. Narrator function contract

Language-agnostic (engine decided later). The narrator is a **pure function**;
the only state is the verbosity counter, owned by a thin session controller.

```
// ── Input event (one of) ────────────────────────────────────────────
Entry  = { type: "entry",  field: "from" | "to", square: Sq }
Move   = { type: "move",   side: "w" | "b", san: string,
           self: boolean,               // §8.1: gates quality commentary
           quality?: { cpLoss: number, isBest: boolean,
                       missedMate?: boolean, allowedMate?: boolean } }
Engine = { type: "engine", side: "w" | "b", san: string,
           eval: { cp?: number, mate?: number },
           line?: string[],            // SAN plies of the PV, in order
           alt?:  { san: string, eval: { cp?: number, mate?: number } },
           wdl?:  [number, number, number] }   // win, draw, loss per 1000
System = { type: "system", action: "endgame" | "restart" | "undo" | "status",
           result?: "w" | "b" | "draw",     // endgame (optional)
           reverted?: { side: "w" | "b", san: string },   // undo (optional)
           sideToMove?: "w" | "b", eval?: { cp?: number; mate?: number },
           moveNo?: number }                // status

// ── Signature ───────────────────────────────────────────────────────
narrate(event, level) -> string        // pure; level in {0,1,2,3}

// ── Session controller (thin, the only stateful piece) ──────────────
onSubmit(move, engine):  level = 1; say(narrate(move,1)); say(narrate(engine,1))
onRepeat():              level = min(level+1, 3); say(narrate(lastMove,level));
                                                  say(narrate(lastEngine,level))
onEntry(e):              say(narrate(e,0))
onSystem(s):             say(narrate(s,1))   // level ignored for system
```

**Purity rules:** `narrate` does no I/O, no clock, no RNG, no board legality
computation. SAN → speech is table lookup (§7). Same input + level ⇒ byte-identical
output (this makes the golden tests in `examples/` exhaustive and cheap).

**SSML:** out of scope for v1 output. If a later engine wants pauses, wrap in the
controller, not in `narrate` — keep the pure core text-only.

---

## 11. Open questions / parked

1. **Chord-4 = `undo`.** Decided. `status` survives as an optional extra chord
   (5 buttons → 10 possible pairs, so there's room). Remaining chord ideas if you
   ever want them: `toggle play/suggest`, `toggle phonetic letters`.
2. **`endgame` says a bare phrase** ("game over") — result is optional and mostly
   a debug ping that the chord fired. No work needed if the app doesn't know the
   result.
3. **Cross-move trend narration** (story arc, volatility from WDL/eval swings).
   Genuinely useful but needs per-game state, not a pure per-utterance function.
   Candidate for a separate optional module later. Parked deliberately.
4. **Suggest mode quality scope — decided (§8.1).** Comment on the move you could
   change: self moves flag inaccuracy+, opponent moves flag blunders only (an
   opportunity). Suggest mode treats both sides as self. Engine suggestion is
   always spoken regardless.
5. **Phonetic letters default.** Shipped off; revisit after you hear plain
   algebraic on the real device.

---

## 12. Optional LLM "explain" tier (bolt-on, NOT load-bearing)

> **Research note:** this "facts first, language last" split is the *validated*
> architecture, not just a hunch. Lee et al. (2022) feed engine-computed control
> tags to an LM that only verbalizes them — human raters preferred it ≥72% over
> the older end-to-end neural approach. Kim et al. (NAACL 2025, "CCC") add a
> concept layer the same way. See `docs/narrativization.md` §2.1.

A tiny on-device LLM may *augment* the narrator with one thing templates do
badly: a natural-language **explanation** of why a move is good, and (later)
cross-move **trend** narration (§11.3). It is strictly optional. Everything in
§1–§11 must work with this tier absent. The deterministic narrator remains the
backbone: instant, exact, testable.

### 12.1 What it does and does NOT touch

| Utterance | Generator | Why |
|---|---|---|
| ENTRY (L0) | template only | must keep up with button taps; LLM far too slow |
| CONFIRM (L1/L2) | template only | must be instant and exact |
| ENGINE move + eval (L1/L2) | template only | correctness is non-negotiable |
| **L3 "explain"** | **LLM (this tier)** | one sentence on the *plan*; you already opted into latency by pressing `repeat` |
| **trend narration** | **LLM (§11.3)** | needs per-game state + free-form phrasing |
| SYSTEM | template only | fixed phrases |

The LLM **never** replaces TTS. It emits text → the same speech-ready string the
TTS engine consumes (§4 rules still apply to its output; see 12.4).

Consistent with §8.1, the explain tier narrates the **actionable** thing: the
engine's suggested move/plan (always "for you"), and — when the moved side is
self — why your move held up or slipped. It does **not** editorialize an
opponent's choice; for an opponent move it explains your *reply*, not their pick.

### 12.2 The non-negotiable rule: feed it facts, forbid it the chess

The model is a **phraser, not a player**. It must never decide what is true about
the position — it only rewrites facts the app already computed (move, eval, PV,
quality). This is what keeps hallucination near zero on a 1–3B model.

```
SYSTEM PROMPT (fixed):
  You narrate chess for an audio app. You will be given FACTS as key=value lines.
  Write ONE short spoken sentence (max ~18 words) using ONLY those facts.
  Do not invent moves, evaluations, threats, or piece names not in the facts.
  No greetings, no filler, no markdown. Plain spoken English.

USER PROMPT (per call, app-filled):
  move=Nf3
  eval=+0.3
  side=white
  plan=develops the knight and controls e5      ← from a fixed motif table, NOT the LLM
  quality=best

→ acceptable: "Knight f3 is the top move, developing and clamping e5 for a small edge."
→ rejected by 12.5 if it mentions any square/piece/number not in the facts.
```

> `plan=` strings come from a small **deterministic motif table** keyed on simple
> features (developing move? central pawn? capture? king-side attack? pawn-storm?),
> *not* invented by the LLM. The LLM only makes them flow. This is the single most
> important constraint in this section.

### 12.3 Model & runtime (S22U target)

- **Hardware reality:** S22U (SD 8 Gen 1 / Exynos 2200, 8–12 GB RAM) runs a 1–3B
  Q4 model. Budget ~10–30 tok/s; an ~18-word sentence ≈ 1–3 s + one-time ~1–2 s load.
- **Model:** start at **Qwen2.5-1.5B-Instruct Q4** or **Llama-3.2-1B-Instruct Q4**
  (~0.7–1 GB). Drop to 0.5B if hot/slow; rise to Gemma-2-2B if quality demands.
- **Runtime (pick one, app-layer concern):**
  - **Google AI Edge / MediaPipe LLM Inference** — most turnkey Android path, GPU.
  - **llama.cpp** (NDK) — most control, GGUF, broad model support.
  - **MLC-LLM** — Adreno GPU via Vulkan/OpenCL.
- **Decode caps:** `max_tokens ≈ 32`, `temperature ≤ 0.3` (we want flat, factual
  phrasing, not creativity), stop on newline. Keep the model **resident** between
  moves to avoid reload latency; unload on game end.

### 12.4 Output must still obey §4

The LLM returns prose with raw notation ("Nf3", "+0.3"). Before TTS, run it
through the **same expansion pass** templates use (§4/§7/§9): `Nf3`→"knight f
three", `+0.3`→"plus 0.3". The expansion is a post-processor on *any* text source,
LLM or template — so the TTS layer never sees raw notation regardless of origin.

### 12.5 Guardrails & fallback (latency + correctness budget)

The controller wraps the LLM call; on **any** of these it silently falls back to
the L3 template (no error spoken — a failed explain just degrades to the
deterministic line):

- **Timeout:** no complete sentence within **2.5 s** → cancel, use template.
- **Fact check (cheap, deterministic):** reject the output if it contains a
  square (`[a-h][1-8]`) or piece word **not present in the facts**, or a signed
  number not equal to the given eval. Regex-level, runs in microseconds.
- **Length:** > 24 words or > 1 sentence → reject.
- **Empty / refusal / repetition** → reject.

Fallback target is always the corresponding L3 template, so the worst case is
exactly today's deterministic behavior — never silence, never a wrong claim.

### 12.6 Seam (how it plugs into §10 without disturbing it)

`narrate(event, level)` stays pure and synchronous. The LLM lives **outside** it,
in the controller, as an async decorator on the L3 path only:

```
onRepeat():
  level = min(level+1, 3)
  say(narrate(lastMove, level))
  if level >= 3 and llmEnabled:
     explain = await llmExplain(factsFrom(lastEngine))   // 12.2 prompt, 12.5 guarded
     say(expand(explain ?? narrate(lastEngine, 3)))       // fallback = template
  else:
     say(narrate(lastEngine, level))
```

Pure core unchanged. Golden tests (`examples/`) still cover L0–L3 templates
verbatim; the LLM path is tested separately against the 12.5 guardrails, not for
exact strings (it's nondeterministic by nature).

### 12.7 Verdict

Ship the deterministic narrator first; it is the product. Add this tier only once
the core feels good on-device. It is a *nice-to-have voice of color* gated behind
`repeat`, never on the critical path — so it can never make the app slower or
wrong for the utterances that matter.

---

## 13. Motif detection (the "trades, pins, captures" layer)

> Research basis: `docs/narrativization.md` §3. Key finding — **no off-the-shelf
> annotator names motifs**; they are all centipawn-only. This layer is ours to
> build, but the primitives are cheap and standard (`python-chess`: `attacks`,
> `attackers`, `is_attacked_by`, `pin`, `is_pinned`, plus SEE).

A motif is a **named, board-derived fact** about a move (`fork`, `pin`, `trade`…),
computed deterministically by the app and attached to events as structured data.
The narrator only *phrases* motifs (and the optional LLM, §12, only rephrases
them). Motifs are facts, never guesses — if a detector isn't confident, it emits
nothing. **Silence beats a wrong "fork!"**

### 13.1 Detector catalog & cost tiers

`atk(c,sq)` = `board.attackers(color=c, square=sq)`. "valuable" = piece value
above the attacker, or undefended. Tiers gate what ships when.

| Motif | Local signal (deterministic) | Tier |
|---|---|---|
| **capture** | `board.is_capture(move)` | cheap — **v1** |
| **check / mate** | `board.is_check()` / `is_checkmate()` after push | cheap — **v1** |
| **castle / promotion** | move flags | cheap — **v1** |
| **pin** | `board.is_pinned(color, sq)` / `board.pin(...)` (built in) | cheap — **v1** |
| **fork / double attack** | moved piece's `attacks()` hits **≥2** valuable enemy targets | cheap — **v1** |
| **hanging piece** | `atk(enemy,sq)` non-empty **and** `atk(own,sq)` empty (or attacker cheaper than target) | cheap — **v1** |
| **wins material** | SEE(capture) > 0 | moderate — **v1.5** |
| **trade / exchange** | capture sequence on one square resolving ≈ material-even (SEE ≈ 0) | moderate — **v1.5** |
| **skewer / x-ray** | ray-attack logic (chessprogramming X-ray) | moderate — **v1.5** |
| **discovered attack** | diff attack-maps pre/post: a *non-moved* piece now hits a valuable target along the vacated line | moderate — **v1.5** |
| **sacrifice (real vs sham)** | SEE < 0 **but** Stockfish eval stays favorable → needs engine to disambiguate | hard — **v2** |
| **zwischenzug, deflection, decoy, overload, interference, prophylaxis, zugzwang, minority attack, initiative/tempo** | multi-move *intent*, not a board fact — infer only weakly from PV/eval swings, or don't claim | hard — **parked** |

**Rule of thumb:** v1 = anything decidable from a single board with python-chess
primitives. v1.5 = needs SEE or a small pre/post diff. v2/parked = needs engine
judgment or encodes *intent*; default to not naming these (§13.4).

### 13.2 SEE is the workhorse for material motifs

**Static Exchange Evaluation** statically scores the net material outcome of a
capture sequence on a square without searching — the canonical cheap way to split
**win material** (SEE > 0) / **even trade** (SEE ≈ 0) / **sacrifice** (SEE < 0).
This one function powers most of the v1.5 tier. (chessprogramming SEE.)

### 13.3 Where motifs appear in utterances

Motifs are **opt-in detail**, not first-pass noise — same philosophy as §5.

- **L1 (default):** motif spoken **only** if it is the headline of the move and
  cheap-tier — i.e. capture/check are already in the SAN expansion (§7); a clean
  **fork** or **pin created** may be appended as one tag: `", forking the rooks"`,
  `", pinning the knight"`. At most **one** motif at L1, and only cheap-tier.
- **L2:** add the most salient secondary motif + the material result of a trade
  ("trade, even" / "wins a pawn").
- **L3:** full motif list + the engine's planning line (§6.3 `line`), and the
  optional LLM (§12) may weave them into a sentence.

Template extension to CONFIRM (§6.2), L1:
```
"{side} {move}[, {motif}][, {quality}]"
motif: at most one, cheap-tier, only if it IS the point of the move
e.g.  "white knight e seven, forking king and rook."
      "black bishop b five, pinning the knight."
```

### 13.4 Confidence gate (non-negotiable)

A motif is emitted only when its detector is certain by construction (a pin *is* a
pin; a fork *is* ≥2 attacked valuables). For anything inferred from eval/intent
(sacrifice, the parked list), require corroboration (e.g. SEE<0 **and** eval still
≥ prior) or stay silent. This mirrors §12.5's fact-check: **never narrate a motif
you can't prove from the position.**

### 13.5 Event schema addition

```
Move = { …, self: boolean,
         motifs?: Motif[] }          // app-computed, ranked most-salient-first
Motif = { kind: "fork"|"pin"|"skewer"|"discovered"|"trade"|"winsMaterial"
                 |"hanging"|"sacrifice"|…,
          targets?: string[],        // squares/pieces involved, for phrasing
          material?: number,         // net pawns, for trade/win (SEE result)
          tier: "cheap"|"moderate"|"hard",
          confident: boolean }       // false ⇒ narrator must not speak it
```

---

## 14. Sequence narration (the last N moves as one phrase)

> Research basis: `docs/narrativization.md` §1.1 (planning/contextual categories),
> §2.3 (engine lookahead supplies arc), §4.2 (saliency = Win% delta). This is the
> home of the parked trend idea (§11.3) and the "describe the last few moves as
> trades and pins" goal.

Goal: occasionally collapse recent history into one human sentence — *"queens came
off, then a pin won the d-pawn"* — instead of only narrating the current ply.
This is **pull-only** (a chord, e.g. `status`, or the top of the `repeat` ladder),
never automatic, never on the move's critical path.

### 14.1 Selection — which recent moves are worth mentioning

Keep a small rolling window of the last K moves (K ≈ 6–8), each tagged with its
`winDrop` (§8.2) and `motifs` (§13). Pick the **salient** ones:

1. Any move whose Win% delta clears the saliency gate (default 7.5 pts, §8.2) —
   the swings.
2. Any move carrying a cheap-tier motif of consequence (fork, pin, trade that
   changed material, a capture sequence).
3. Cap at the **top 2–3** by |Win% delta| so the sentence stays short. If nothing
   clears the gate, say so briefly ("quiet, level") rather than narrate filler.

This is exactly the literature's approach: rank by score-change, mention the few
that moved the needle (python-chess-annotator's 7.5-pt gate; CCC concept ranking).

### 14.2 Phrasing — collapse to one clause per kept event

Order kept events oldest→newest, join with light connectives, no filler:

```
template:  "{event}[, then {event}][, and now {arc}]"
event:     a motif/quality phrase   e.g. "queens traded", "a pin won the d-pawn",
                                         "white blundered a knight"
arc:       current standing from eval (§9)   e.g. "white's clearly better"
```

Examples:
```
"queens traded, then a fork won the exchange. white's clearly better."
"three quiet developing moves. level."
"you dropped a pawn on move 12, but it's only minus 0.4."
```

### 14.3 Arc / momentum from eval swings

The game-arc clause comes from the eval trajectory over the window, not prose
analysis: a sustained climb → "white has taken over"; a sharp reversal → "the
game just swung"; flatline near zero → "still level". Bucket the trend the same
way §9.3 buckets a single eval; speak at most one arc clause.

> Per `docs/narrativization.md` §2.3, Stockfish PV/MultiPV + the eval history
> already supply everything needed here — no model required to *compute* the arc;
> the optional LLM (§12) only makes the assembled facts flow if enabled.

### 14.4 Cost & placement

Sequence narration touches K cached results, not new engine work (evals were
already computed per move), so it is cheap. It lives behind a chord/`repeat`, is
nondeterministic only if the LLM phrases it, and falls back to the deterministic
join (§14.2) on any LLM failure (§12.5).
