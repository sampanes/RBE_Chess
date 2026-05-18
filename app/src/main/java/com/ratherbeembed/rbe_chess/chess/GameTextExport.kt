package com.ratherbeembed.rbe_chess.chess

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class GameTextExport(
    val fileName: String,
    val text: String,
    val fen: String,
    val pgn: String,
)

object GameTextExporter {
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun build(
        history: MoveHistory,
        reason: GameEndReason,
        generatedAt: LocalDateTime = LocalDateTime.now(),
    ): GameTextExport {
        val fen = FenExporter.fromHistory(history)
        val pgn = pgnText(
            history = history,
            reason = reason,
            date = generatedAt.toLocalDate(),
            fen = fen,
        )
        val fileName = "RBE-Chess-${generatedAt.format(fileTimestamp)}.txt"
        val text = buildString {
            appendLine("RBE Chess Export")
            appendLine("Generated: $generatedAt")
            appendLine("Termination: ${reason.termination}")
            appendLine()
            appendLine("FEN:")
            appendLine(fen)
            appendLine()
            appendLine("PGN:")
            appendLine(pgn)
        }
        return GameTextExport(fileName = fileName, text = text, fen = fen, pgn = pgn)
    }

    private fun pgnText(
        history: MoveHistory,
        reason: GameEndReason,
        date: LocalDate,
        fen: String,
    ): String {
        val result = resultFor(history, reason)
        return buildString {
            appendLine("""[Event "RBE Chess"]""")
            appendLine("""[Site "RBE Chess Android"]""")
            appendLine("""[Date "${date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}"]""")
            appendLine("""[Result "$result"]""")
            appendLine("""[Termination "${reason.termination}"]""")
            appendLine("""[CurrentFEN "$fen"]""")
            appendLine("""[MoveFormat "UCI"]""")
            appendLine()
            append(movetext(history, result))
        }
    }

    private fun movetext(history: MoveHistory, result: String): String {
        if (history.moves.isEmpty()) return result
        val body = history.moves
            .chunked(2)
            .mapIndexed { idx, pair ->
                buildString {
                    append("${idx + 1}. ${pair[0]}")
                    if (pair.size > 1) append(" ${pair[1]}")
                }
            }
            .joinToString(" ")
        return "$body $result"
    }

    private fun resultFor(history: MoveHistory, reason: GameEndReason): String =
        when (reason) {
            GameEndReason.CHECKMATE ->
                if (history.size % 2 == 1) "1-0" else "0-1"
            GameEndReason.STALEMATE -> "1/2-1/2"
            GameEndReason.FORFEIT -> "*"
        }
}

object FenExporter {
    fun fromHistory(history: MoveHistory): String {
        val board = startingBoard()
        var castling = CastlingRights()
        var enPassant = "-"
        var halfmoveClock = 0

        for (uci in history.moves) {
            val move = ParsedMove.fromUci(uci) ?: continue
            val moving = board[move.from] ?: continue
            val target = board[move.to]
            val enPassantCapture =
                moving.type == PieceType.PAWN &&
                    move.from.file != move.to.file &&
                    target == null
            val isCapture = target != null || enPassantCapture

            castling = castling.afterMove(move, moving, target)
            enPassant =
                if (moving.type == PieceType.PAWN && abs(move.to.rank - move.from.rank) == 2) {
                    BoardSquare(move.from.file, (move.from.rank + move.to.rank) / 2).name
                } else {
                    "-"
                }
            halfmoveClock =
                if (moving.type == PieceType.PAWN || isCapture) 0 else halfmoveClock + 1

            applyMove(board, move, moving, enPassantCapture)
        }

        val activeColor = if (history.size % 2 == 0) "w" else "b"
        val fullmoveNumber = history.size / 2 + 1
        return listOf(
            boardPlacement(board),
            activeColor,
            castling.toFen(),
            enPassant,
            halfmoveClock.toString(),
            fullmoveNumber.toString(),
        ).joinToString(" ")
    }

    private data class ParsedMove(
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

    private data class CastlingRights(
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

    private fun applyMove(
        board: MutableMap<BoardSquare, ChessPiece>,
        move: ParsedMove,
        moving: ChessPiece,
        enPassantCapture: Boolean,
    ) {
        board.remove(move.from)
        if (enPassantCapture) {
            board.remove(BoardSquare(move.to.file, move.from.rank))
        }
        if (moving.type == PieceType.KING && abs(move.to.file - move.from.file) == 2) {
            moveCastleRook(board, move)
        }
        board[move.to] = moving.promotedTo(move.promotion)
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

    private fun boardPlacement(board: Map<BoardSquare, ChessPiece>): String =
        (7 downTo 0).joinToString("/") { rank ->
            buildString {
                var empty = 0
                for (file in 0..7) {
                    val piece = board[BoardSquare(file, rank)]
                    if (piece == null) {
                        empty += 1
                    } else {
                        if (empty > 0) {
                            append(empty)
                            empty = 0
                        }
                        append(piece.toFenChar())
                    }
                }
                if (empty > 0) append(empty)
            }
        }

    private fun ChessPiece.toFenChar(): Char {
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

    private fun ChessPiece.promotedTo(promotion: Char?): ChessPiece {
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
}
