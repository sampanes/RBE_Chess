package com.ratherbeembed.rbe_chess.game

import com.ratherbeembed.rbe_chess.chess.AutomaticDraw
import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.GameEndReason
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.TerminalState

/**
 * Legal-move set and check flag for one position, as reported by the engine.
 * An empty legal-move set is terminal: checkmate if in check, else stalemate.
 */
internal data class PositionState(
    val legalMoves: Set<String>,
    val inCheck: Boolean,
) {
    val terminalState: TerminalState?
        get() = when {
            legalMoves.isNotEmpty() -> null
            inCheck -> TerminalState.CHECKMATE
            else -> TerminalState.STALEMATE
        }

    val onlyReply: String? get() = legalMoves.singleOrNull()
}

/** Side to move after [history]; white moves on even ply counts. */
fun sideToMove(history: MoveHistory): ChessSide =
    if (history.size % 2 == 0) ChessSide.WHITE else ChessSide.BLACK

/** Side that played the last ply of [history], or null for an empty history. */
fun lastMover(history: MoveHistory): ChessSide? =
    if (history.size == 0) null else sideToMove(MoveHistory(history.moves.dropLast(1)))

/** Spoken label for [side], from the point of view of the player at the board. */
fun moverLabel(side: ChessSide, playerSide: ChessSide): String {
    val color = if (side == ChessSide.WHITE) "White" else "Black"
    return if (side == playerSide) "Your $color" else "Opponent $color"
}

val TerminalState.label: String
    get() = when (this) {
        TerminalState.CHECKMATE -> "Checkmate"
        TerminalState.STALEMATE -> "Stalemate"
        TerminalState.DRAW_REPETITION -> "Draw by repetition"
        TerminalState.DRAW_MOVE_RULE -> "Draw by seventy-five-move rule"
        TerminalState.DRAW_MATERIAL -> "Draw by insufficient material"
    }

fun TerminalState.toEndReason(): GameEndReason =
    when (this) {
        TerminalState.CHECKMATE -> GameEndReason.CHECKMATE
        TerminalState.STALEMATE -> GameEndReason.STALEMATE
        TerminalState.DRAW_REPETITION -> GameEndReason.DRAW_REPETITION
        TerminalState.DRAW_MOVE_RULE -> GameEndReason.DRAW_MOVE_RULE
        TerminalState.DRAW_MATERIAL -> GameEndReason.DRAW_MATERIAL
    }

fun AutomaticDraw.toTerminalState(): TerminalState =
    when (this) {
        AutomaticDraw.FIVEFOLD_REPETITION -> TerminalState.DRAW_REPETITION
        AutomaticDraw.SEVENTY_FIVE_MOVE_RULE -> TerminalState.DRAW_MOVE_RULE
        AutomaticDraw.INSUFFICIENT_MATERIAL -> TerminalState.DRAW_MATERIAL
    }
