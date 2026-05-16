package com.ratherbeembed.rbe_chess.engine

sealed interface BestMoveResult {
    data class Move(val uci: String) : BestMoveResult
    data class Terminal(val state: TerminalState) : BestMoveResult
}

enum class TerminalState {
    CHECKMATE,
    STALEMATE,
}
