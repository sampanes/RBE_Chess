# Sample session — suggest mode (a blunder appears)

You enter both sides. Quality flags fire on bad moves even at L1 (that is the
one thing that *should* interrupt the terse flow). A mate sequence shows the
short-circuit.

Legend: `▶` = input, `🔊` = spoken, level in `()`.

```
▶ submit (white plays Bb5 — an inaccuracy, cpLoss 90)
🔊 (L1) "white bishop b five, inaccuracy."       ← flagged because it's ≥ inaccuracy
🔊 (L1) "engine: a six."

▶ submit (black plays Qxa7?? — hangs, cpLoss 410)
🔊 (L1) "black queen takes a seven, blunder."
🔊 (L1) "engine: knight c three."

   ── what did I miss? pull detail ──
▶ repeat
🔊 (L2) "black queen takes a seven. blunder, lost about 4 pawns."
🔊 (L2) "engine: knight c three, plus 4.1."

▶ submit (white finds the kill — Qh5, mate in 3)
🔊 (L1) "white queen h five."
🔊 (L1) "engine: queen h five."                  ← you already played the best move

▶ repeat
🔊 (L2) "white queen h five. best move."
🔊 (L2) "engine: queen h five, mate in three."   ← mate short-circuits the decimal

▶ chord-4 = status
🔊      "white to move, mate in three, move 19."

▶ endgame chord
🔊      "game over, white wins."
```
