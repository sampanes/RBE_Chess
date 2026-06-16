# How chess gets narrativized — research findings

Source-backed research underpinning the motif-detection and phrasing design in
`SPEC.md` (§8, §12, §13, §14). Every claim below was cross-checked across
multiple sources via an adversarial verification pass (3 independent skeptics per
claim; only claims surviving ≥2/3 are kept). Confidence and vote are noted.

> TL;DR for the impatient: (1) Human commentary decomposes into ~6 categories;
> only some are computable from the board. (2) The validated way to automate it is
> **compute facts with rules/engine, let a small LM only phrase them** — exactly
> our §12 design. (3) **No off-the-shelf annotator names tactical motifs** (pin,
> fork, trade…); they are all centipawn-only. You detect motifs yourself with
> cheap `python-chess` primitives. (4) Move quality & "is this worth mentioning"
> should gate on **Win% drop**, not raw centipawns.

---

## 1. How humans narrativize chess

### 1.1 The six-category taxonomy of commentary

Academic work that mined a large corpus of human game commentary found it
decomposes into a **stable six-category taxonomy** *(confidence: high, 3-0)*:

1. **Move description** — what physically happened ("knight takes e5").
2. **Move quality** — good/bad/dubious ("a blunder", "the only move").
3. **Comparative** — versus an alternative ("better was Rd1").
4. **Planning** — intent/what it threatens ("preparing the f5 break").
5. **Contextual** — game arc, history, momentum ("White has pressed all game").
6. **General** — off-board chatter (player bios, anecdotes) — not computable.

Modeling work typically uses the **five board-derivable** categories (dropping
"general"), and the easiest three to automate are *description, quality,
comparative* — all directly derivable from move + engine output. *Planning* and
*contextual* need lookahead/history.
Sources: Jhamtani et al., ACL 2018 (P18-1154); Zang et al., ACL 2019 (P19-1597);
2025 survey (arXiv 2506.17294).

### 1.2 Annotation symbols & NAGs (the compact vocabulary)

Human annotators compress judgment into a tiny symbol set, standardized as
**Numeric Annotation Glyphs (NAGs)** in PGN:

| Symbol | NAG | Meaning |
|---|---|---|
| `!` | $1 | good move |
| `?` | $2 | mistake |
| `!!` | $3 | brilliant |
| `??` | $4 | blunder |
| `!?` | $5 | interesting move |
| `?!` | $6 | dubious move |
| `±` / `∓` | $16/$17 | clear advantage W/B |
| `+−` / `−+` | $18/$19 | decisive advantage W/B |
| `=` | $10 | even |
| `∞` | $13 | unclear |

These map cleanly onto engine-derived quality buckets (§8) and onto spoken labels
("blunder", "the only move", "slightly better for white"). Source: Wikipedia NAG
(secondary), corroborated by the annotator tools below.

### 1.3 The motif vocabulary (what gets *named*)

Tactical/positional motifs are the nouns of chess narration. The standard set
(definitions condensed from chess tactic references):

- **Tactical (concrete, short-horizon):** pin, fork (double attack), skewer,
  discovered attack, discovered check, double check, removing the defender /
  deflection, decoy, overloading, interference, x-ray, zwischenzug
  (in-between move), trapped piece, back-rank mate, sacrifice (real vs sham).
- **Material:** capture, exchange/**trade** (captures resolving to ~even
  material), winning the exchange (R for minor), hanging piece.
- **Positional (slow, plan-level):** outpost, weak square / hole, open/half-open
  file, pawn break, minority attack, prophylaxis, zugzwang, initiative/tempo,
  king-side vs queen-side play, pawn-structure terms (isolated, doubled, passed).

Sources: Wikipedia "Chess tactic" (secondary); chessworld tactics glossary
(secondary). **Crucial caveat for us:** these are *human* labels — no eval engine
emits them. See §3.

---

## 2. How automated commentary systems work

### 2.1 The validated architecture: facts first, language last

The single most important finding for this project *(confidence: high, 3-0)*:

> **Compute the chess facts deterministically (rules + engine); let a small
> language model do nothing but render those pre-computed facts into a sentence.
> The LM performs no chess reasoning.**

Lee et al. (2022, arXiv 2212.08195) feed an engine's quality/suggested-move
**control tags** to an LM that only verbalizes them — and human raters preferred
its output **≥72%** of the time over the earlier end-to-end neural baseline
(Jhamtani GAC). Kim et al. (NAACL 2025, "CCC", arXiv 2410.20811) push further with
an **expert/concept layer** fed into the model. Both **decouple reasoning from
language** so the model can't hallucinate chess.

This is precisely our SPEC §12 design (facts → fact-only prompt → phraser → §4
expansion → TTS). The research independently arrives at it. Good.

### 2.2 What eval-based annotators actually narrate (and don't)

Open annotators are **centipawn-only quality machines** — they label move
quality and suggest better moves, and that's it:

- **python-chess-annotator** (rpdelaney) — assigns NAGs purely from engine
  centipawn deltas; thresholds historically ~ −75 / −150 / −300 cp for
  inaccuracy / mistake / blunder. Selects moves to annotate by a Win%-delta
  threshold (`NEEDS_ANNOTATION_THRESHOLD = 7.5`). *Performs no tactical or
  positional motif detection.* *(3-0)*
- **chess-artist** (fsmosca) — annotates PGN via a UCI engine; maps centipawns to
  NAG symbols ($1/$2/$4/$6 …). Also quality-only. *(3-0)*
- **DecodeChess** — markets *natural-language* tactical/strategic explanation, but
  it is a server-side product, not a local library, and not open. (blog source)
- **Lichess / Chess.com Game Review** — quality + accuracy + "Game Report"
  classifications (Brilliant/Great/Best/Inaccuracy/Mistake/Blunder), driven by
  engine eval; Chess.com's "brilliant"/"great" use extra heuristics (a good move
  that's also a sacrifice, the only good move, etc.).

**Takeaway:** the move-quality half is a solved, copyable recipe. The
**motif-naming half does not exist off the shelf — it is yours to build** *(3-0)*.

### 2.3 The deep/contextual tier uses engine lookahead

Richer narration (planning, "what it threatens", game arc) is produced by feeding
**engine lookahead** to the generator: current and resulting boards, win rates,
the best alternative move, and self-played continuation lines (Zang et al. ACL
2019 self-plays long-term lines; GAC feeds engine threats+eval). **Stockfish
PV + MultiPV already give us all of this** — no extra model needed to *compute*
it. Source: P19-1597, P18-1154. Corpus scale (context): ~298K (move, comment)
pairs over ~11K online games — commentary is highly style-diverse and
context-dependent, which is *why* the fact-first split beats end-to-end.

---

## 3. Detecting motifs locally (no LLM, phone-cheap)

No annotator does this, but `python-chess` exposes exactly the primitives needed
*(confidence: high, 3-0)*:

- `board.attacks(square)` → squares a piece hits.
- `board.attackers(color, square)` → all pieces of `color` hitting a square.
  - `attackers(own_color, sq)` = **defenders** of that square.
  - `attackers(enemy_color, sq)` = **threats** to it.
- `board.is_attacked_by(color, square)` → boolean threat test.
- `board.pin(color, square)` / `board.is_pinned(color, square)` → **pins**, built in.

Derived detectors (cheap, single-board, microseconds):

| Motif | Local signal |
|---|---|
| **Capture** | move is a capture (`board.is_capture`) |
| **Check / mate** | `board.is_check()` / `is_checkmate()` after move |
| **Pin** | `board.is_pinned(color, sq)` / `board.pin(...)` — built in |
| **Fork / double attack** | moved piece's `attacks()` hits **≥2** valuable enemy targets |
| **Hanging / undefended** | `attackers(enemy,sq)` non-empty and `attackers(own,sq)` empty (or attacker value < defended value) |
| **Trade / exchange** | capture sequence on one square that resolves ~material-even, evaluated by **SEE** (Static Exchange Evaluation) |
| **Winning material** | SEE of a capture > 0 |
| **Sacrifice (real vs sham)** | SEE < 0 **but** Stockfish eval stays favorable → needs engine to disambiguate |
| **X-ray / skewer** | ray attack logic (chessprogramming "X-ray Attacks") — moderate |
| **Discovered attack** | diff attack-maps before/after: a *non-moved* piece now hits a valuable target along the vacated line — moderate |

**SEE** (Static Exchange Evaluation) is the key tool for material-resolution
motifs: it statically scores the net material outcome of a capture sequence on a
square without searching. It's the canonical, cheap way to classify
trade/win/sacrifice. Source: chessprogramming SEE & X-ray (secondary);
python-chess core docs (primary); a FEN/PGN tactic-detection API blog confirms the
attacks/attackers approach works in practice (blog).

### 3.1 Cost tiers (what's phone-cheap vs hard)

- **Cheap (single board, python-chess primitives):** capture, check/mate, pin,
  fork, hanging piece, defended/undefended, castling, promotion, attacks near the
  king. → **v1 candidates.**
- **Moderate (SEE or small ray/diff logic):** trade vs win vs sacrifice,
  skewer/x-ray, discovered attack. → **v1.5.**
- **Hard (intent / multi-move / requires engine PV or semantics):** zwischenzug,
  deflection, decoy, overloading, interference, prophylaxis, zugzwang, minority
  attack, initiative/tempo. These are *plans*, not board facts — infer
  conservatively from Stockfish PV/eval swings, or leave to the engine-lookahead
  context tier, or don't claim them at all. Better silent than wrong.

---

## 4. Move quality & saliency — gate on Win%, not centipawns

Two refinements the research strongly supports over our first-pass cp thresholds:

### 4.1 Convert centipawns → Win% before judging
*(confidence: high, 3-0)* Raw centipawn loss is non-linear in practical
importance (losing 100cp at +0.2 matters more than at +6.0). Lichess converts a
Stockfish centipawn eval to a **Win percentage** via a logistic:

```
Win% = 50 + 50 * ( 2 / (1 + exp(-0.00368208 * centipawns)) - 1 )
```

Move quality is then the **drop in Win%** caused by the move (best vs played), not
raw cp. Chess.com-style accuracy formulas work the same way. Source: Lichess
accuracy page (primary); python-chess-annotator & chess-artist (primary).

### 4.2 Saliency = Win% delta threshold
*(confidence: high, 3-0)* "Is this move worth commenting on?" is decided by a
**Win%-delta gate**: python-chess-annotator annotates only when best-vs-played
Win% drop exceeds `NEEDS_ANNOTATION_THRESHOLD = 7.5` points. Kim et al. (CCC)
sharpen selection by ranking *concepts* by their score-change before/after the
move. Source: python-chess-annotator (primary); CCC (primary).

This directly drives our §8 (quality flag gate) and §14 (which of the last N moves
to mention).

---

## 5. What this means for the spec

| Research finding | Where it lands in SPEC |
|---|---|
| Facts-first, LM-only-phrasing is the validated architecture (72%+ preferred) | §12 — confirmed; add citation |
| Quality/saliency gate on **Win% drop**, not raw cp | §8.2 (new), §14 saliency |
| Lichess Win% logistic constant `0.00368208` | §8.2 (new) |
| Annotators don't name motifs → DIY with python-chess | §13 (new) — motif catalog + detectors |
| `pin/is_pinned`, `attacks/attackers/is_attacked_by`, SEE | §13 detector recipes |
| Cheap/moderate/hard motif cost tiers | §13.1 — what's v1 vs parked |
| Engine PV/MultiPV already supplies lookahead for planning/context | §14 sequence/arc + §6.3 L3 line |
| Sequence saliency: rank concepts by score change | §14 selection heuristic |

---

## Sources

**Primary (academic / source code / official):**
- Jhamtani et al., *Learning to Generate Move-by-Move Commentary for Chess Games*, ACL 2018 — https://aclanthology.org/P18-1154/
- Zang et al., ACL 2019 (per-category sub-models, self-played lines) — https://aclanthology.org/P19-1597/
- Lee et al., 2022 (engine control-tags → LM phrasing, ≥72% preference) — https://arxiv.org/pdf/2212.08195
- Kim et al., *CCC*, NAACL 2025 (concept/expert layer, concept prioritization) — https://arxiv.org/abs/2410.20811
- Chess commentary survey, 2025 — https://arxiv.org/html/2506.17294v1
- python-chess core (attacks/attackers/pin/SEE) — https://python-chess.readthedocs.io/en/latest/core.html
- python-chess-annotator (cp thresholds, 7.5 Win% saliency gate, quality-only) — https://github.com/rpdelaney-archive/python-chess-annotator
- chess-artist (cp→NAG mapping) — https://github.com/fsmosca/chess-artist
- Lichess accuracy / Win% logistic — https://lichess.org/page/accuracy
- Google AI Edge MediaPipe LLM Inference (Android on-device) — https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android

**Secondary / reference:**
- Numeric Annotation Glyphs — https://en.wikipedia.org/wiki/Numeric_Annotation_Glyphs
- Chess tactic taxonomy — https://en.wikipedia.org/wiki/Chess_tactic
- Tactics glossary — https://www.chessworld.net/chessclubs/openingguide/chess-tactics-glossary.asp
- Static Exchange Evaluation — https://www.chessprogramming.org/Static_Exchange_Evaluation
- X-ray attacks (bitboards) — https://www.chessprogramming.org/X-ray_Attacks_(Bitboards)

**Blog / product (lower weight):**
- DecodeChess natural-language analysis — https://decodechess.com/natural-language-chess-analysis/
- Tactic-detection-from-FEN/PGN API write-up — https://dev.to/stevejvv/i-built-an-api-that-detects-chess-tactical-patterns-from-fen-and-pgn-5ef0

**One refuted claim** (kept out): a specific dataset-statistics figure (exact
game/pair counts and a 5-category split) failed verification 0-3 — the taxonomy is
6 categories (§1.1), and exact corpus counts vary by source, so we don't rely on
them.
