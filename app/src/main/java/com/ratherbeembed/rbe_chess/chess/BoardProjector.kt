package com.ratherbeembed.rbe_chess.chess

import kotlin.math.abs

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
        val board = startingBoard()
        var lastApplied: BoardMove? = null
        for (uci in history.moves) {
            val move = parseMove(uci) ?: continue
            lastApplied = applyMove(board, move) ?: lastApplied
        }
        return BoardSnapshot(pieces = board.toMap(), lastMove = lastApplied)
    }

    private fun startingBoard(): MutableMap<BoardSquare, ChessPiece> {
        val board = mutableMapOf<BoardSquare, ChessPiece>()
        for (file in 0..7) {
            board[BoardSquare(file, 1)] = ChessPiece(ChessSide.WHITE, PieceType.PAWN)
            board[BoardSquare(file, 6)] = ChessPiece(ChessSide.BLACK, PieceType.PAWN)
        }
        val backRank = listOf(
            PieceType.ROOK,
            PieceType.KNIGHT,
            PieceType.BISHOP,
            PieceType.QUEEN,
            PieceType.KING,
            PieceType.BISHOP,
            PieceType.KNIGHT,
            PieceType.ROOK,
        )
        for ((file, type) in backRank.withIndex()) {
            board[BoardSquare(file, 0)] = ChessPiece(ChessSide.WHITE, type)
            board[BoardSquare(file, 7)] = ChessPiece(ChessSide.BLACK, type)
        }
        return board
    }

    private data class ParsedMove(
        val uci: String,
        val from: BoardSquare,
        val to: BoardSquare,
        val promotion: Char?,
    )

    private fun parseMove(uci: String): ParsedMove? {
        if (uci.length < 4) return null
        val from = BoardSquare.fromUci(uci.substring(0, 2)) ?: return null
        val to = BoardSquare.fromUci(uci.substring(2, 4)) ?: return null
        val promotion = uci.getOrNull(4)?.lowercaseChar()
        return ParsedMove(uci = uci, from = from, to = to, promotion = promotion)
    }

    private fun applyMove(
        board: MutableMap<BoardSquare, ChessPiece>,
        move: ParsedMove,
    ): BoardMove? {
        val piece = board.remove(move.from) ?: return null
        val targetOccupied = board.containsKey(move.to)

        if (piece.type == PieceType.PAWN && move.from.file != move.to.file && !targetOccupied) {
            board.remove(BoardSquare(move.to.file, move.from.rank))
        }

        if (piece.type == PieceType.KING && abs(move.to.file - move.from.file) == 2) {
            moveCastleRook(board, move)
        }

        board[move.to] = piece.promotedTo(move.promotion)
        return BoardMove(uci = move.uci, from = move.from, to = move.to)
    }

    private fun moveCastleRook(
        board: MutableMap<BoardSquare, ChessPiece>,
        move: ParsedMove,
    ) {
        val kingSide = move.to.file > move.from.file
        val rookFromFile = if (kingSide) 7 else 0
        val rookToFile = if (kingSide) 5 else 3
        val rook = board.remove(BoardSquare(rookFromFile, move.from.rank)) ?: return
        board[BoardSquare(rookToFile, move.from.rank)] = rook
    }

    private fun ChessPiece.promotedTo(promotion: Char?): ChessPiece {
        if (type != PieceType.PAWN || promotion == null) return this
        val promotedType = when (promotion) {
            'q' -> PieceType.QUEEN
            'r' -> PieceType.ROOK
            'b' -> PieceType.BISHOP
            'n' -> PieceType.KNIGHT
            else -> null
        } ?: return this
        return copy(type = promotedType)
    }
}
