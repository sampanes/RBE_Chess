package com.ratherbeembed.rbe_chess.chess

enum class PieceType {
    KING,
    QUEEN,
    ROOK,
    BISHOP,
    KNIGHT,
    PAWN,
}

data class ChessPiece(
    val side: ChessSide,
    val type: PieceType,
)

data class BoardSquare(
    val file: Int,
    val rank: Int,
) {
    val name: String get() = "${'a' + file}${rank + 1}"

    companion object {
        fun fromUci(value: String): BoardSquare? {
            if (value.length != 2) return null
            val file = value[0] - 'a'
            val rank = value[1] - '1'
            if (file !in 0..7 || rank !in 0..7) return null
            return BoardSquare(file, rank)
        }
    }
}

data class BoardMove(
    val uci: String,
    val from: BoardSquare,
    val to: BoardSquare,
)

data class BoardSnapshot(
    val pieces: Map<BoardSquare, ChessPiece>,
    val lastMove: BoardMove?,
) {
    fun pieceAt(file: Int, rank: Int): ChessPiece? = pieces[BoardSquare(file, rank)]
}

object BoardProjector {
    fun fromHistory(history: MoveHistory): BoardSnapshot {
        val replay = PositionReplay()
        var lastApplied: BoardMove? = null
        for (uci in history.moves) {
            lastApplied = replay.apply(uci) ?: lastApplied
        }
        return BoardSnapshot(pieces = replay.board.toMap(), lastMove = lastApplied)
    }
}
