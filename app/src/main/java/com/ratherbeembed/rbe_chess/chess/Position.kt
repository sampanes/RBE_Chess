package com.ratherbeembed.rbe_chess.chess

import kotlin.math.abs

/** One move in board terms. [promotion] is set only for pawn promotions. */
internal data class ChessMove(
    val from: BoardSquare,
    val to: BoardSquare,
    val promotion: PieceType? = null,
) {
    val uci: String
        get() = from.name + to.name + (promotion?.let { promotionChar(it).toString() } ?: "")

    companion object {
        fun fromUci(uci: String): ChessMove? {
            val parsed = PositionReplay.ParsedMove.fromUci(uci) ?: return null
            if (uci.length > 5) return null
            val promotion = when (parsed.promotion?.lowercaseChar()) {
                null -> null
                'q' -> PieceType.QUEEN
                'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP
                'n' -> PieceType.KNIGHT
                else -> return null
            }
            return ChessMove(parsed.from, parsed.to, promotion)
        }

        private fun promotionChar(type: PieceType): Char =
            when (type) {
                PieceType.QUEEN -> 'q'
                PieceType.ROOK -> 'r'
                PieceType.BISHOP -> 'b'
                PieceType.KNIGHT -> 'n'
                else -> error("not a promotion piece: $type")
            }
    }
}

/**
 * Immutable chess position with full legal-move generation.
 *
 * [PositionReplay] stays the legality-agnostic replay behind the board
 * view, FEN export and draw detection; Stockfish remains the legality
 * oracle for the game loop. This class exists for the narration layer,
 * which needs chess knowledge UCI does not hand back: SAN disambiguation,
 * check/mate suffixes, and attack maps for fork/pin/hanging detection.
 *
 * Verified by perft against the standard reference counts (PositionTest).
 */
internal class Position private constructor(
    private val squares: Array<ChessPiece?>,
    val sideToMove: ChessSide,
    val castling: PositionReplay.CastlingRights,
    val enPassantTarget: BoardSquare?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {

    fun pieceAt(square: BoardSquare): ChessPiece? = squares[index(square)]

    /** Every occupied square, in a1, b1, ... h8 order. */
    val occupied: List<Pair<BoardSquare, ChessPiece>>
        get() = squares.indices.mapNotNull { i -> squares[i]?.let { square(i) to it } }

    fun kingSquare(side: ChessSide): BoardSquare? =
        squares.indices.firstOrNull { i ->
            squares[i]?.let { it.side == side && it.type == PieceType.KING } == true
        }?.let(::square)

    fun inCheck(side: ChessSide = sideToMove): Boolean {
        val king = kingSquare(side) ?: return false
        return isAttacked(index(king), side.opponent())
    }

    fun isAttacked(square: BoardSquare, by: ChessSide): Boolean = isAttacked(index(square), by)

    /** Squares holding a piece of [by] that attacks [square]. */
    fun attackers(square: BoardSquare, by: ChessSide): List<BoardSquare> {
        val target = index(square)
        return squares.indices.filter { i ->
            squares[i]?.side == by && target in attackTargets(i)
        }.map(::square)
    }

    /**
     * Squares the piece on [square] attacks: pawn diagonals, knight and
     * king steps, and slider rays up to and including the first blocker.
     * Empty for an empty square.
     */
    fun attacksFrom(square: BoardSquare): List<BoardSquare> =
        attackTargets(index(square)).map(::square)

    fun legalMoves(): List<ChessMove> {
        val us = sideToMove
        return pseudoLegalMoves().filter { move ->
            val next = play(move)
            val king = next.kingSquare(us)
            king == null || !next.isAttacked(index(king), us.opponent())
        }
    }

    fun isLegal(move: ChessMove): Boolean = move in legalMoves()

    fun isCheckmate(): Boolean = inCheck() && legalMoves().isEmpty()

    fun isStalemate(): Boolean = !inCheck() && legalMoves().isEmpty()

    /**
     * Plays [move] without checking legality. Callers that hold untrusted
     * input should go through [playUci] instead.
     */
    fun play(move: ChessMove): Position {
        val from = index(move.from)
        val to = index(move.to)
        val next = squares.copyOf()
        val moving = next[from] ?: return this
        val captured = next[to]
        val isPawn = moving.type == PieceType.PAWN
        val enPassantCapture = isPawn && move.from.file != move.to.file && captured == null
        if (enPassantCapture) {
            next[index(BoardSquare(move.to.file, move.from.rank))] = null
        }
        if (moving.type == PieceType.KING && abs(move.to.file - move.from.file) == 2) {
            val kingSide = move.to.file > move.from.file
            val rookFrom = index(BoardSquare(if (kingSide) 7 else 0, move.from.rank))
            val rookTo = index(BoardSquare(if (kingSide) 5 else 3, move.from.rank))
            next[rookTo] = next[rookFrom]
            next[rookFrom] = null
        }
        next[from] = null
        next[to] = if (isPawn && move.promotion != null) moving.copy(type = move.promotion) else moving
        val doublePush = isPawn && abs(move.to.rank - move.from.rank) == 2
        return Position(
            squares = next,
            sideToMove = sideToMove.opponent(),
            castling = castling.afterMove(
                PositionReplay.ParsedMove(move.from, move.to, null),
                moving,
                captured,
            ),
            enPassantTarget = if (doublePush) {
                BoardSquare(move.from.file, (move.from.rank + move.to.rank) / 2)
            } else {
                null
            },
            halfmoveClock = if (isPawn || captured != null || enPassantCapture) 0 else halfmoveClock + 1,
            fullmoveNumber = if (sideToMove == ChessSide.BLACK) fullmoveNumber + 1 else fullmoveNumber,
        )
    }

    /** Plays a UCI move if it is legal here, else returns null. */
    fun playUci(uci: String): Position? {
        val move = ChessMove.fromUci(uci) ?: return null
        return if (isLegal(move)) play(move) else null
    }

    fun toFen(): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val piece = squares[rank * 8 + file]
                if (piece == null) {
                    empty += 1
                } else {
                    if (empty > 0) append(empty).also { empty = 0 }
                    append(piece.toFenChar())
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
        append(if (sideToMove == ChessSide.WHITE) " w " else " b ")
        append(castling.toFen())
        append(' ')
        append(enPassantTarget?.name ?: "-")
        append(' ')
        append(halfmoveClock)
        append(' ')
        append(fullmoveNumber)
    }

    private fun pseudoLegalMoves(): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        for (i in squares.indices) {
            val piece = squares[i] ?: continue
            if (piece.side != sideToMove) continue
            when (piece.type) {
                PieceType.PAWN -> addPawnMoves(i, moves)
                PieceType.KING -> {
                    addTargetMoves(i, attackTargets(i), moves)
                    addCastlingMoves(i, moves)
                }
                else -> addTargetMoves(i, attackTargets(i), moves)
            }
        }
        return moves
    }

    private fun addTargetMoves(from: Int, targets: List<Int>, moves: MutableList<ChessMove>) {
        for (to in targets) {
            if (squares[to]?.side == sideToMove) continue
            moves += ChessMove(square(from), square(to))
        }
    }

    private fun addPawnMoves(from: Int, moves: MutableList<ChessMove>) {
        val file = from % 8
        val rank = from / 8
        val dir = if (sideToMove == ChessSide.WHITE) 1 else -1
        val startRank = if (sideToMove == ChessSide.WHITE) 1 else 6
        val lastRank = if (sideToMove == ChessSide.WHITE) 7 else 0

        fun addWithPromotions(to: Int) {
            if (to / 8 == lastRank) {
                for (type in PROMOTION_TYPES) moves += ChessMove(square(from), square(to), type)
            } else {
                moves += ChessMove(square(from), square(to))
            }
        }

        val one = rank + dir
        if (one in 0..7 && squares[one * 8 + file] == null) {
            addWithPromotions(one * 8 + file)
            val two = rank + 2 * dir
            if (rank == startRank && squares[two * 8 + file] == null) {
                moves += ChessMove(square(from), square(two * 8 + file))
            }
        }
        val epIndex = enPassantTarget?.let(::index)
        for (to in attackTargets(from)) {
            val target = squares[to]
            if ((target != null && target.side != sideToMove) || to == epIndex) {
                addWithPromotions(to)
            }
        }
    }

    private fun addCastlingMoves(from: Int, moves: MutableList<ChessMove>) {
        val white = sideToMove == ChessSide.WHITE
        val homeRank = if (white) 0 else 7
        if (from != homeRank * 8 + 4) return
        val them = sideToMove.opponent()
        if (isAttacked(from, them)) return
        val kingSide = if (white) castling.whiteKingSide else castling.blackKingSide
        val queenSide = if (white) castling.whiteQueenSide else castling.blackQueenSide
        val rook = ChessPiece(sideToMove, PieceType.ROOK)
        val base = homeRank * 8
        if (kingSide &&
            squares[base + 7] == rook &&
            squares[base + 5] == null && squares[base + 6] == null &&
            !isAttacked(base + 5, them) && !isAttacked(base + 6, them)
        ) {
            moves += ChessMove(square(from), square(base + 6))
        }
        if (queenSide &&
            squares[base] == rook &&
            squares[base + 1] == null && squares[base + 2] == null && squares[base + 3] == null &&
            !isAttacked(base + 3, them) && !isAttacked(base + 2, them)
        ) {
            moves += ChessMove(square(from), square(base + 2))
        }
    }

    private fun attackTargets(from: Int): List<Int> {
        val piece = squares[from] ?: return emptyList()
        val file = from % 8
        val rank = from / 8
        return when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (piece.side == ChessSide.WHITE) 1 else -1
                listOf(-1, 1).mapNotNull { df -> offset(file + df, rank + dir) }
            }
            PieceType.KNIGHT -> KNIGHT_STEPS.mapNotNull { (df, dr) -> offset(file + df, rank + dr) }
            PieceType.KING -> KING_STEPS.mapNotNull { (df, dr) -> offset(file + df, rank + dr) }
            PieceType.BISHOP -> rays(file, rank, DIAGONALS)
            PieceType.ROOK -> rays(file, rank, ORTHOGONALS)
            PieceType.QUEEN -> rays(file, rank, DIAGONALS + ORTHOGONALS)
        }
    }

    private fun rays(file: Int, rank: Int, directions: List<Pair<Int, Int>>): List<Int> {
        val out = mutableListOf<Int>()
        for ((df, dr) in directions) {
            var f = file + df
            var r = rank + dr
            while (f in 0..7 && r in 0..7) {
                val i = r * 8 + f
                out += i
                if (squares[i] != null) break
                f += df
                r += dr
            }
        }
        return out
    }

    private fun isAttacked(target: Int, by: ChessSide): Boolean {
        val file = target % 8
        val rank = target / 8
        val pawnRank = if (by == ChessSide.WHITE) rank - 1 else rank + 1
        for (df in intArrayOf(-1, 1)) {
            if (pieceAt(file + df, pawnRank) == ChessPiece(by, PieceType.PAWN)) return true
        }
        for ((df, dr) in KNIGHT_STEPS) {
            if (pieceAt(file + df, rank + dr) == ChessPiece(by, PieceType.KNIGHT)) return true
        }
        for ((df, dr) in KING_STEPS) {
            if (pieceAt(file + df, rank + dr) == ChessPiece(by, PieceType.KING)) return true
        }
        if (sliderHits(file, rank, ORTHOGONALS, by, PieceType.ROOK)) return true
        return sliderHits(file, rank, DIAGONALS, by, PieceType.BISHOP)
    }

    private fun sliderHits(
        file: Int,
        rank: Int,
        directions: List<Pair<Int, Int>>,
        by: ChessSide,
        slider: PieceType,
    ): Boolean {
        for ((df, dr) in directions) {
            var f = file + df
            var r = rank + dr
            while (f in 0..7 && r in 0..7) {
                val piece = squares[r * 8 + f]
                if (piece != null) {
                    if (piece.side == by && (piece.type == slider || piece.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                f += df
                r += dr
            }
        }
        return false
    }

    private fun pieceAt(file: Int, rank: Int): ChessPiece? =
        if (file in 0..7 && rank in 0..7) squares[rank * 8 + file] else null

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        val START: Position = requireNotNull(fromFen(START_FEN))

        private val PROMOTION_TYPES =
            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        private val KNIGHT_STEPS = listOf(
            1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2,
        )
        private val KING_STEPS = listOf(
            1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1,
        )
        private val ORTHOGONALS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val DIAGONALS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

        private fun index(square: BoardSquare): Int = square.rank * 8 + square.file

        private fun square(index: Int): BoardSquare = BoardSquare(index % 8, index / 8)

        private fun offset(file: Int, rank: Int): Int? =
            if (file in 0..7 && rank in 0..7) rank * 8 + file else null

        /** Parses a FEN; the halfmove and fullmove fields are optional. */
        fun fromFen(fen: String): Position? {
            val fields = fen.trim().split(Regex("\\s+"))
            if (fields.size < 4) return null
            val rows = fields[0].split('/')
            if (rows.size != 8) return null
            val squares = arrayOfNulls<ChessPiece>(64)
            for ((row, text) in rows.withIndex()) {
                val rank = 7 - row
                var file = 0
                for (c in text) {
                    if (c.isDigit()) {
                        file += c.digitToInt()
                    } else {
                        if (file > 7) return null
                        squares[rank * 8 + file] = pieceFromFen(c) ?: return null
                        file += 1
                    }
                }
                if (file != 8) return null
            }
            val side = when (fields[1]) {
                "w" -> ChessSide.WHITE
                "b" -> ChessSide.BLACK
                else -> return null
            }
            val rights = fields[2]
            val castling = PositionReplay.CastlingRights(
                whiteKingSide = 'K' in rights,
                whiteQueenSide = 'Q' in rights,
                blackKingSide = 'k' in rights,
                blackQueenSide = 'q' in rights,
            )
            val enPassant = if (fields[3] == "-") null else BoardSquare.fromUci(fields[3]) ?: return null
            return Position(
                squares = squares,
                sideToMove = side,
                castling = castling,
                enPassantTarget = enPassant,
                halfmoveClock = fields.getOrNull(4)?.toIntOrNull() ?: 0,
                fullmoveNumber = fields.getOrNull(5)?.toIntOrNull() ?: 1,
            )
        }

        /**
         * Replays [history] from the start position. Returns null if any
         * move is illegal -- the game loop only records Stockfish-legal
         * moves, so null means the history did not come from the app.
         */
        fun fromHistory(history: MoveHistory): Position? =
            history.moves.fold(START as Position?) { position, uci -> position?.playUci(uci) }

        private fun pieceFromFen(c: Char): ChessPiece? {
            val side = if (c.isUpperCase()) ChessSide.WHITE else ChessSide.BLACK
            val type = when (c.lowercaseChar()) {
                'k' -> PieceType.KING
                'q' -> PieceType.QUEEN
                'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP
                'n' -> PieceType.KNIGHT
                'p' -> PieceType.PAWN
                else -> return null
            }
            return ChessPiece(side, type)
        }
    }
}

internal fun ChessSide.opponent(): ChessSide =
    if (this == ChessSide.WHITE) ChessSide.BLACK else ChessSide.WHITE
