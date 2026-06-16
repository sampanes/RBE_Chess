# Utterance catalog (golden output)

Every row is a deterministic `narrate(event, level)` result per `SPEC.md`. These
double as the golden-test fixtures once `narrate` is implemented. `‹silent›`
means the narrator emits an empty string (nothing is spoken).

## ENTRY (L0)

| event | output |
|---|---|
| `{entry, from, e2}` | `from e two` |
| `{entry, to, e4}`   | `to e four` |
| `{entry, to, c6}`   | `to c six` |

## CONFIRM — move readback

`self` gates quality at L1 (§8.1): self flags inaccuracy+; opponent flags blunder only.

| event | L1 | L2 |
|---|---|---|
| `{move, w, "e4", self}` (best) | `white e four.` | `white pawn e four. best move.` |
| `{move, b, "Nf3", self}` (fine, cpLoss 35) | `black knight f three.` | `black knight f three. fine.` |
| `{move, w, "Bb5", self}` (inacc, cpLoss 90) | `white bishop b five, inaccuracy.` | `white bishop b five. inaccuracy, lost about half a pawn.` |
| `{move, w, "Qxa7", self}` (blunder, cpLoss 410) | `white queen takes a seven, blunder.` | `white queen takes a seven. blunder, lost about 4 pawns.` |
| `{move, w, "O-O", self}` (fine) | `white castles kingside.` | `white castles kingside. fine.` |
| `{move, w, "e8=Q#", self}` | `white e eight promotes to queen, checkmate.` | `white pawn e eight promotes to queen, checkmate. best move.` |
| `{move, w, "Nbd2", self}` (fine) | `white knight from b to d two.` | `white knight from b to d two. fine.` |

### Opponent moves (`self: false`) — quieter at L1

| event | L1 | L2 |
|---|---|---|
| `{move, b, "Bxc6+", opp}` (best) | `black bishop takes c six, check.` | `black bishop takes c six, check. best move.` |
| `{move, b, "Bb5", opp}` (inacc, cpLoss 90) | `black bishop b five.` ← *silent on inaccuracy* | `black bishop b five. inaccuracy, lost about half a pawn.` |
| `{move, b, "Nc3", opp}` (mistake, cpLoss 160) | `black knight c three.` ← *silent on mistake* | `black knight c three. mistake, lost about 1.5 pawns.` |
| `{move, b, "Qxa7", opp}` (blunder, cpLoss 410) | `black queen takes a seven, blunder.` ← *blunder = opportunity, spoken* | `black queen takes a seven. blunder, lost about 4 pawns.` |

## ENGINE — favored move

| event | L1 | L2 | L3 |
|---|---|---|---|
| `{engine, w, "e4", cp +30}` | `engine: e four.` | `engine: e four, plus 0.3.` | `engine: e four, plus 0.3. then e five, then knight f three. or c four, plus 0.2.` |
| `{engine, b, "c5", cp -15}` | `engine: c five.` | `engine: c five, minus 0.1.` | `engine: c five, level. then knight f three.` |
| `{engine, w, "Qh5", mate 3}` | `engine: queen h five.` | `engine: queen h five, mate in three.` | `engine: queen h five, mate in three. then g six, then queen takes g six.` |
| `{engine, w, "Rd8", cp +720, wdl 850/130/20}` | `engine: rook d eight.` | `engine: rook d eight, plus 7.2.` | `engine: rook d eight, plus 7.2. winning four times out of five.` |

## CONFIRM with motifs (§13)

At most one cheap-tier motif at L1, only if it's the point of the move.

| event | L1 | L2 |
|---|---|---|
| `{move, w, "Ne7", self, motifs:[fork(K,R)]}` | `white knight e seven, forking king and rook.` | `white knight e seven, forking king and rook. best move.` |
| `{move, b, "Bb5", opp, motifs:[pin(N)]}` | `black bishop b five, pinning the knight.` | `black bishop b five, pinning the knight. fine.` |
| `{move, w, "Rxd5", self, motifs:[trade(even)]}` | `white rook takes d five.` (capture already in SAN) | `white rook takes d five. trade, even.` |
| `{move, w, "Qxh7", self, motifs:[sacrifice], conf:false}` | `white queen takes h seven.` ← *sacrifice unproven at L1, stays silent* | `white queen takes h seven. blunder, lost about 9 pawns.` |

## SEQUENCE narration (§14) — pulled via chord/repeat

| window | output |
|---|---|
| queens off, then a fork wins exchange, eval +1.8 | `queens traded, then a fork won the exchange. white's clearly better.` |
| 3 quiet moves, eval ~0 | `three quiet developing moves. level.` |
| you dropped a pawn move 12, eval −0.4 | `you dropped a pawn on move twelve, but it's only minus 0.4.` |

## SYSTEM

| event | output |
|---|---|
| `{system, endgame}` | `game over.` |
| `{system, endgame, result w}` | `game over, white wins.` |
| `{system, restart}` | `new game.` |
| `{system, undo}` | `undo.` |
| `{system, undo, reverted {w,"Nf3"}}` | `took back white knight f three.` |
| `{system, status, sideToMove w, cp +30, moveNo 14}` | `white to move, plus 0.3, move 14.` |
