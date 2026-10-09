package com.ratherbeembed.rbe_chess.narrator

import com.ratherbeembed.rbe_chess.chess.ChessMove
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.chess.Position
import com.ratherbeembed.rbe_chess.chess.San

/**
 * App-side fact builder: turns the game loop's UCI moves and engine
 * output into [NarrationEvent]s. This is where the chess knowledge lives
 * (SAN, motifs); [Narrator] stays a pure phraser. Every builder returns
 * null when the history or move is not legal, so a bad input silences
 * one utterance instead of crashing the loop.
 */
object NarrationFacts {

    fun moveEvent(
        historyBefore: MoveHistory,
        uci: String,
        self: Boolean = true,
        quality: QualityFacts? = null,
    ): MoveEvent? {
        val position = Position.fromHistory(historyBefore) ?: return null
        val move = legalMove(position, uci) ?: return null
        return MoveEvent(
            side = position.sideToMove,
            san = San.of(position, move),
            self = self,
            quality = quality,
            motifs = MotifDetector.detect(position, move),
        )
    }

    /**
     * Engine pick for the side to move after [history]. [pv] is the UCI
     * principal variation; a leading copy of [uci] is skipped, and the
     * line stops at the first ply that does not replay legally.
     */
    fun engineEvent(
        history: MoveHistory,
        uci: String,
        eval: Eval,
        pv: List<String> = emptyList(),
        alt: Pair<String, Eval>? = null,
        wdl: Wdl? = null,
    ): EngineEvent? {
        val position = Position.fromHistory(history) ?: return null
        val move = legalMove(position, uci) ?: return null
        var cursor = position.play(move)
        val line = mutableListOf<String>()
        for (ply in if (pv.firstOrNull() == uci) pv.drop(1) else pv) {
            val next = legalMove(cursor, ply) ?: break
            line += San.of(cursor, next)
            cursor = cursor.play(next)
        }
        return EngineEvent(
            side = position.sideToMove,
            san = San.of(position, move),
            eval = eval,
            line = line,
            alt = alt?.let { (altUci, altEval) ->
                legalMove(position, altUci)?.let { Alternative(San.of(position, it), altEval) }
            },
            wdl = wdl,
        )
    }

    /** The last move of [history] as an undo target ("took back ..."). */
    fun revertedMove(history: MoveHistory): RevertedMove? {
        val last = history.moves.lastOrNull() ?: return null
        val event = moveEvent(MoveHistory(history.moves.dropLast(1)), last) ?: return null
        return RevertedMove(event.side, event.san)
    }

    private fun legalMove(position: Position, uci: String): ChessMove? =
        ChessMove.fromUci(uci)?.takeIf(position::isLegal)
}
