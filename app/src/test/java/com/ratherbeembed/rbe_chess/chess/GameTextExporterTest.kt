package com.ratherbeembed.rbe_chess.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class GameTextExporterTest {

    @Test
    fun `starting position FEN is complete`() {
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            FenExporter.fromHistory(MoveHistory.EMPTY),
        )
    }

    @Test
    fun `FEN tracks active color en passant and move counters`() {
        val history = MoveHistory.EMPTY
            .append("e2e4")
            .append("e7e5")
            .append("g1f3")

        assertEquals(
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
            FenExporter.fromHistory(history),
        )
    }

    @Test
    fun `FEN includes en passant target after double pawn push`() {
        val history = MoveHistory.EMPTY.append("e2e4")

        assertEquals(
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            FenExporter.fromHistory(history),
        )
    }

    @Test
    fun `FEN removes castling rights after rook move`() {
        val history = MoveHistory.EMPTY.append("h1h3")

        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/7R/PPPPPPPP/RNBQKBN1 b Qkq - 1 1",
            FenExporter.fromHistory(history),
        )
    }

    @Test
    fun `export contains FEN and PGN style UCI movetext`() {
        val history = MoveHistory.EMPTY
            .append("e2e4")
            .append("e7e5")
            .append("g1f3")
        val export = GameTextExporter.build(
            history = history,
            reason = GameEndReason.FORFEIT,
            generatedAt = LocalDateTime.of(2026, 5, 17, 12, 34, 56),
        )

        assertEquals("RBE-Chess-20260517-123456.txt", export.fileName)
        assertTrue(export.text.contains("FEN:"))
        assertTrue(export.text.contains("""[MoveFormat "UCI"]"""))
        assertTrue(export.pgn.contains("1. e2e4 e7e5 2. g1f3 *"))
    }

    @Test
    fun `draw reasons export a half point result and their termination`() {
        val export = GameTextExporter.build(
            history = MoveHistory.EMPTY.append("e2e4"),
            reason = GameEndReason.DRAW_REPETITION,
            generatedAt = LocalDateTime.of(2026, 7, 6, 12, 34, 56),
        )

        assertTrue(export.pgn.contains("""[Result "1/2-1/2"]"""))
        assertTrue(export.pgn.contains("""[Termination "fivefold repetition"]"""))
        assertTrue(
            GameTextExporter.build(
                history = MoveHistory.EMPTY.append("e2e4"),
                reason = GameEndReason.DRAW_MATERIAL,
                generatedAt = LocalDateTime.of(2026, 7, 6, 12, 34, 56),
            ).pgn.contains("""[Result "1/2-1/2"]"""),
        )
    }

    @Test
    fun `checkmate result is credited to side that just moved`() {
        val whiteMate = GameTextExporter.build(
            history = MoveHistory.EMPTY.append("f2f3"),
            reason = GameEndReason.CHECKMATE,
            generatedAt = LocalDateTime.of(2026, 5, 17, 12, 34, 56),
        )
        val blackMate = GameTextExporter.build(
            history = MoveHistory.EMPTY.append("f2f3").append("e7e5"),
            reason = GameEndReason.CHECKMATE,
            generatedAt = LocalDateTime.of(2026, 5, 17, 12, 34, 56),
        )

        assertTrue(whiteMate.pgn.contains("""[Result "1-0"]"""))
        assertTrue(blackMate.pgn.contains("""[Result "0-1"]"""))
    }
}
