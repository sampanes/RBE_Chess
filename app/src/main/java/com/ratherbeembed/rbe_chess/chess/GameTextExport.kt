package com.ratherbeembed.rbe_chess.chess

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
            GameEndReason.STALEMATE,
            GameEndReason.DRAW_REPETITION,
            GameEndReason.DRAW_MOVE_RULE,
            GameEndReason.DRAW_MATERIAL,
            -> "1/2-1/2"
            GameEndReason.FORFEIT -> "*"
        }
}

object FenExporter {
    fun fromHistory(history: MoveHistory): String {
        val replay = PositionReplay()
        for (uci in history.moves) {
            replay.apply(uci)
        }

        val activeColor = if (history.size % 2 == 0) "w" else "b"
        val fullmoveNumber = history.size / 2 + 1
        return listOf(
            boardPlacement(replay.board),
            activeColor,
            replay.castling.toFen(),
            replay.enPassantTarget?.name ?: "-",
            replay.halfmoveClock.toString(),
            fullmoveNumber.toString(),
        ).joinToString(" ")
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
}
