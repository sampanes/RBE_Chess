# Sample session -- play mode

You enter the opponent's move you saw, submit, hear the engine's reply, and
mostly move on. One `repeat` shown to demonstrate the verbosity climb.

Legend: `>` = button/chord input, `SAY` = spoken output, level in `()`.

```
> increment start-col -> e2
SAY (L0) "from e two"
> increment end-col, end-row -> e4
SAY (L0) "to e four"
> submit
SAY (L1) "black e four."                         <- opponent's move, readback
SAY (L1) "engine: e five."                        <- Stockfish's reply, just the move

   -- you think; you want to know why --
> repeat (chord)
SAY (L2) "black pawn e four. fine."
SAY (L2) "engine: e five, level."

> repeat (chord)
SAY (L3) "black pawn e four. fine."
SAY (L3) "engine: e five, level. then knight f three, then knight c six."

   -- you enter your own move, submit, level resets to L1 --
> submit (Nf3)
SAY (L1) "white knight f three."
SAY (L1) "engine: knight c six."
```

Notice: the first utterance after every submit is two short lines. No filler,
no eval, no "the engine suggests considering". Detail only when you pull it.
