package com.ratherbeembed.rbe_chess.chess

import androidx.compose.runtime.Immutable

/**
 * Running list of plies in UCI form, fed verbatim into Stockfish's
 * `position startpos moves ...` command. M1 keeps this dumb on
 * purpose: no legality check, no side tracking, no SAN. Stockfish
 * silently drops illegal moves in the `position` line, which is good
 * enough for the auto-advance loop where every appended move is either
 * the user's typed opponent move or the engine's own bestmove.
 *
 * @Immutable so Compose strong-skipping doesn't suppress recomposition
 * when a new instance with an appended ply replaces the old one
 * (same reason MoveBuffer carries the annotation).
 */
@Immutable
data class MoveHistory(val moves: List<String> = emptyList()) {

    val size: Int get() = moves.size

    fun append(uci: String): MoveHistory = MoveHistory(moves + uci)

    /**
     * Drop the most recent pair of plies (the user's typed move and the
     * engine's auto-advanced reply, in normal auto-advance mode). If
     * fewer than two plies exist, drops what's there. Empty history
     * stays empty.
     */
    fun undoLastPair(): MoveHistory =
        if (moves.isEmpty()) this
        else MoveHistory(moves.dropLast(minOf(2, moves.size)))

    companion object {
        val EMPTY: MoveHistory = MoveHistory()
    }
}
