package com.ratherbeembed.rbe_chess.chess

enum class GameEndReason(val label: String, val termination: String) {
    CHECKMATE("Checkmate", "checkmate"),
    STALEMATE("Stalemate", "stalemate"),
    DRAW_REPETITION("Draw by repetition", "fivefold repetition"),
    DRAW_MOVE_RULE("Draw by the seventy-five move rule", "seventy-five-move rule"),
    DRAW_MATERIAL("Draw by insufficient material", "insufficient material"),
    FORFEIT("Game ended", "forfeit"),
}
