package com.ratherbeembed.rbe_chess.chess

import kotlin.math.abs

/**
 * Single shared move-replay for everything that projects a [MoveHistory]
 * onto a board: the display-only board viewer ([BoardProjector]), FEN
 * export ([FenExporter]), and draw detection ([DrawDetector]).
 *
 * Like [MoveHistory] itself this is legality-agnostic on purpose:
 * Stockfish is the legality oracle upstream, so [apply] just moves
 * pieces. Moves whose from-square is empty are skipped gracefully.
 */
internal class PositionReplay {
    val board: MutableMap<BoardSquare, ChessPiece> = startingBoard()
    var castling: CastlingRights = CastlingRights()
        private set
    var enPassantTarget: BoardSquare? = null
        private set
    var halfmoveClock: Int = 0
        private set
    var plies: Int = 0
        private set

    val sideToMove: ChessSide
        get() = if (plies % 2 == 0) ChessSide.WHITE else ChessSide.BLACK

    /**
     * Applies one UCI move and returns it as a [BoardMove], or null when
     * the move could not be parsed or its from-square is empty.
     */
    fun apply(uci: String): BoardMove? {
        plies += 1
        val move = ParsedMove.fromUci(uci) ?: return null
        val moving = board[move.from] ?: return null
        val target = board[move.to]
        val enPassantCapture =
            moving.type == PieceType.PAWN &&
                move.from.file != move.to.file &&
                target == null
        val isCapture = target != null || enPassantCapture

        castling = castling.afterMove(move, moving, target)
        enPassantTarget =
            if (moving.type == PieceType.PAWN && abs(move.to.rank - move.from.rank) == 2) {
                BoardSquare(move.from.file, (move.from.rank + move.to.rank) / 2)
            } else {
                null
            }
        halfmoveClock =
            if (moving.type == PieceType.PAWN || isCapture) 0 else halfmoveClock + 1

        board.remove(move.from)
        if (enPassantCapture) {
            board.remove(BoardSquare(move.to.file, move.from.rank))
        }
        if (moving.type == PieceType.KING && abs(move.to.file - move.from.file) == 2) {
            moveCastleRook(move)
        }
        board[move.to] = moving.promotedTo(move.promotion)
        return BoardMove(uci = uci, from = move.from, to = move.to)
    }

    /**
     * True when the recorded en-passant target could actually be taken:
     * an enemy pawn sits beside the pawn that just double-pushed. FIDE
     * position identity for repetition only distinguishes positions by
     * en passant when the capture is possible. (Pins are ignored — a
     * pinned pawn very rarely makes this over-distinguish, which only
     * under-counts repetitions, never over-counts.)
     */
    fun isEnPassantCapturable(): Boolean {
        val target = enPassantTarget ?: return false
        // The double-pushed pawn sits in front of the target square from
        // the mover's perspective; the capturer is the side to move now.
        val pushedRank = if (target.rank == 2) 3 else 4
        val pushedPawnSide = if (target.rank == 2) ChessSide.WHITE else ChessSide.BLACK
        for (df in intArrayOf(-1, 1)) {
            val file = target.file + df
            if (file !in 0..7) continue
            val piece = board[BoardSquare(file, pushedRank)] ?: continue
            if (piece.type == PieceType.PAWN && piece.side != pushedPawnSide) return true
        }
        return false
    }

    /**
     * FIDE repetition identity: piece placement + side to move +
     * castling rights + capturable en passant square.
     */
    fun repetitionKey(): String = buildString {
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                val piece = board[BoardSquare(file, rank)]
                append(piece?.toFenChar() ?: '.')
            }
        }
        append('|')
        append(if (sideToMove == ChessSide.WHITE) 'w' else 'b')
        append('|')
        append(castling.toFen())
        append('|')
        append(if (isEnPassantCapturable()) enPassantTarget!!.name else "-")
    }

    private fun moveCastleRook(move: ParsedMove) {
        val kingSide = move.to.file > move.from.file
        val rookFromFile = if (kingSide) 7 else 0
        val rookToFile = if (kingSide) 5 else 3
        val rook = board.remove(BoardSquare(rookFromFile, move.from.rank)) ?: return
        board[BoardSquare(rookToFile, move.from.rank)] = rook
    }

    internal data class ParsedMove(
        val from: BoardSquare,
        val to: BoardSquare,
        val promotion: Char?,
    ) {
        companion object {
            fun fromUci(uci: String): ParsedMove? {
                if (uci.length < 4) return null
                val from = BoardSquare.fromUci(uci.substring(0, 2)) ?: return null
                val to = BoardSquare.fromUci(uci.substring(2, 4)) ?: return null
                return ParsedMove(from = from, to = to, promotion = uci.getOrNull(4))
            }
        }
    }

    internal data class CastlingRights(
        val whiteKingSide: Boolean = true,
        val whiteQueenSide: Boolean = true,
        val blackKingSide: Boolean = true,
        val blackQueenSide: Boolean = true,
    ) {
        fun afterMove(
            move: ParsedMove,
            moving: ChessPiece,
            captured: ChessPiece?,
        ): CastlingRights {
            var next = this
            if (moving.type == PieceType.KING) {
                next = if (moving.side == ChessSide.WHITE) {
                    next.copy(whiteKingSide = false, whiteQueenSide = false)
                } else {
                    next.copy(blackKingSide = false, blackQueenSide = false)
                }
            }
            if (moving.type == PieceType.ROOK) {
                next = next.withoutRookRight(move.from, moving.side)
            }
            if (captured?.type == PieceType.ROOK) {
                next = next.withoutRookRight(move.to, captured.side)
            }
            return next
        }

        private fun withoutRookRight(square: BoardSquare, side: ChessSide): CastlingRights =
            when (side to square) {
                ChessSide.WHITE to BoardSquare(7, 0) -> copy(whiteKingSide = false)
                ChessSide.WHITE to BoardSquare(0, 0) -> copy(whiteQueenSide = false)
                ChessSide.BLACK to BoardSquare(7, 7) -> copy(blackKingSide = false)
                ChessSide.BLACK to BoardSquare(0, 7) -> copy(blackQueenSide = false)
                else -> this
            }

        fun toFen(): String = buildString {
            if (whiteKingSide) append('K')
            if (whiteQueenSide) append('Q')
            if (blackKingSide) append('k')
            if (blackQueenSide) append('q')
            if (length == 0) append('-')
        }
    }

    companion object {
        fun startingBoard(): MutableMap<BoardSquare, ChessPiece> {
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
    }
}

internal fun ChessPiece.toFenChar(): Char {
    val base = when (type) {
        PieceType.KING -> 'k'
        PieceType.QUEEN -> 'q'
        PieceType.ROOK -> 'r'
        PieceType.BISHOP -> 'b'
        PieceType.KNIGHT -> 'n'
        PieceType.PAWN -> 'p'
    }
    return if (side == ChessSide.WHITE) base.uppercaseChar() else base
}

internal fun ChessPiece.promotedTo(promotion: Char?): ChessPiece {
    if (type != PieceType.PAWN || promotion == null) return this
    val promotedType = when (promotion.lowercaseChar()) {
        'q' -> PieceType.QUEEN
        'r' -> PieceType.ROOK
        'b' -> PieceType.BISHOP
        'n' -> PieceType.KNIGHT
        else -> null
    } ?: return this
    return copy(type = promotedType)
}
