package com.ratherbeembed.rbe_chess.chess

enum class GameEndReason(val label: String, val termination: String) {
    CHECKMATE("Checkmate", "checkmate"),
    STALEMATE("Stalemate", "stalemate"),
    FORFEIT("Game ended", "forfeit"),
}
