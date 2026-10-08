# Sample session -- suggest mode (a blunder appears)

You enter both sides. Quality flags fire on bad moves even at L1 (that is the
one thing that *should* interrupt the terse flow). A mate sequence shows the
short-circuit.

Legend: `>` = input, `SAY` = spoken, level in `()`.

```
> submit (white plays Bb5 -- an inaccuracy, cpLoss 90)
SAY (L1) "white bishop b five, inaccuracy."       <- flagged because it's >= inaccuracy
SAY (L1) "engine: a six."

> submit (black plays Qxa7?? -- hangs, cpLoss 410)
SAY (L1) "black queen takes a seven, blunder."
SAY (L1) "engine: knight c three."

   -- what did I miss? pull detail --
> repeat
SAY (L2) "black queen takes a seven. blunder, lost about 4 pawns."
SAY (L2) "engine: knight c three, plus 4.1."

> submit (white finds the kill -- Qh5, mate in 3)
SAY (L1) "white queen h five."
SAY (L1) "engine: queen h five."                  <- you already played the best move

> repeat
SAY (L2) "white queen h five. best move."
SAY (L2) "engine: queen h five, mate in three."   <- mate short-circuits the decimal

> chord-4 = status
SAY      "white to move, mate in three, move 19."

> endgame chord
SAY      "game over, white wins."
```
