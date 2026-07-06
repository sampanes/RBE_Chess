package com.ratherbeembed.rbe_chess.engine

sealed interface BestMoveResult {
    data class Move(val uci: String) : BestMoveResult
    data class Terminal(val state: TerminalState) : BestMoveResult
}

enum class TerminalState {
    CHECKMATE,
    STALEMATE,

    // Automatic draws detected app-side by DrawDetector, not by the
    // engine. They ride the same terminal plumbing (speech, finished
    // menu, session snapshot) as engine-reported mates.
    DRAW_REPETITION,
    DRAW_MOVE_RULE,
    DRAW_MATERIAL,
}
