package com.ratherbeembed.rbe_chess.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawDetectorTest {

    private fun historyOf(vararg moves: String): MoveHistory =
        MoveHistory(moves.toList())

    private val knightShuffle = listOf("g1f3", "g8f6", "f3g1", "f6g8")

    private fun shuffled(times: Int): MoveHistory =
        MoveHistory((1..times).flatMap { knightShuffle })

    @Test
    fun `empty history has no draw`() {
        val status = DrawDetector.statusFor(MoveHistory.EMPTY)

        assertNull(status.automatic)
        assertNull(status.claimable)
        assertEquals(1, status.repetitionCount)
        assertEquals(0, status.halfmoveClock)
    }

    @Test
    fun `single repetition is not claimable`() {
        val status = DrawDetector.statusFor(shuffled(1))

        assertNull(status.automatic)
        assertNull(status.claimable)
        assertEquals(2, status.repetitionCount)
    }

    @Test
    fun `threefold repetition is claimable but not automatic`() {
        val status = DrawDetector.statusFor(shuffled(2))

        assertNull(status.automatic)
        assertEquals(ClaimableDraw.THREEFOLD_REPETITION, status.claimable)
        assertEquals(3, status.repetitionCount)
    }

    @Test
    fun `fivefold repetition is an automatic draw`() {
        val status = DrawDetector.statusFor(shuffled(4))

        assertEquals(AutomaticDraw.FIVEFOLD_REPETITION, status.automatic)
        assertNull(status.claimable)
        assertEquals(5, status.repetitionCount)
    }

    @Test
    fun `halfmove clock counts reversible moves and resets on pawn move`() {
        assertEquals(4, DrawDetector.statusFor(shuffled(1)).halfmoveClock)
        assertEquals(
            0,
            DrawDetector.statusFor(shuffled(1).append("e2e4")).halfmoveClock,
        )
    }

    @Test
    fun `lost castling rights distinguish otherwise equal positions`() {
        // King steps out and back: the position after ply 6 has the same
        // placement as after ply 2, but castling rights differ, so it is
        // not a repetition.
        val kingShuffle = historyOf("e2e3", "e7e6", "e1e2", "e8e7", "e2e1", "e7e8")

        assertEquals(1, DrawDetector.statusFor(kingShuffle).repetitionCount)

        // Once both kings have moved, further shuffles repeat the
        // rights-lost position and eventually reach threefold.
        val moves = kingShuffle.moves +
            listOf("e1e2", "e8e7", "e2e1", "e7e8") +
            listOf("e1e2", "e8e7", "e2e1", "e7e8")
        val status = DrawDetector.statusFor(MoveHistory(moves))

        assertEquals(3, status.repetitionCount)
        assertEquals(ClaimableDraw.THREEFOLD_REPETITION, status.claimable)
    }

    @Test
    fun `capturable en passant right distinguishes repetition`() {
        // After d7d5 white's e5 pawn could capture en passant, so that
        // position is distinct from the same placement once the right
        // has lapsed. Two knight shuffles only reach the lapsed position
        // twice.
        val moves = listOf("e2e4", "g8h6", "e4e5", "d7d5") +
            listOf("b1c3", "b8c6", "c3b1", "c6b8") +
            listOf("b1c3", "b8c6", "c3b1", "c6b8")
        val status = DrawDetector.statusFor(MoveHistory(moves))

        assertEquals(2, status.repetitionCount)
        assertNull(status.claimable)
    }

    @Test
    fun `non-capturable en passant does not distinguish repetition`() {
        // c7c5 leaves no white pawn adjacent, so the double-push position
        // already equals the shuffled ones: threefold.
        val moves = listOf("e2e4", "g8h6", "e4e5", "c7c5") +
            listOf("b1c3", "b8c6", "c3b1", "c6b8") +
            listOf("b1c3", "b8c6", "c3b1", "c6b8")
        val status = DrawDetector.statusFor(MoveHistory(moves))

        assertEquals(3, status.repetitionCount)
        assertEquals(ClaimableDraw.THREEFOLD_REPETITION, status.claimable)
    }

    @Test
    fun `classify applies fifty and seventy-five move thresholds`() {
        assertNull(DrawDetector.classify(1, 99, false).claimable)
        assertEquals(
            ClaimableDraw.FIFTY_MOVE_RULE,
            DrawDetector.classify(1, 100, false).claimable,
        )
        assertEquals(
            AutomaticDraw.SEVENTY_FIVE_MOVE_RULE,
            DrawDetector.classify(1, 150, false).automatic,
        )
        assertNull(DrawDetector.classify(1, 150, false).claimable)
    }

    @Test
    fun `classify prefers automatic over claimable`() {
        val status = DrawDetector.classify(5, 120, false)

        assertEquals(AutomaticDraw.FIVEFOLD_REPETITION, status.automatic)
        assertNull(status.claimable)
    }

    @Test
    fun `insufficient material table`() {
        fun board(vararg pieces: Pair<String, ChessPiece>): Map<BoardSquare, ChessPiece> =
            pieces.associate { (square, piece) -> BoardSquare.fromUci(square)!! to piece }

        val whiteKing = "e1" to ChessPiece(ChessSide.WHITE, PieceType.KING)
        val blackKing = "e8" to ChessPiece(ChessSide.BLACK, PieceType.KING)

        assertTrue(DrawDetector.isInsufficientMaterial(board(whiteKing, blackKing)))
        assertTrue(
            DrawDetector.isInsufficientMaterial(
                board(whiteKing, blackKing, "b1" to ChessPiece(ChessSide.WHITE, PieceType.KNIGHT)),
            ),
        )
        assertTrue(
            DrawDetector.isInsufficientMaterial(
                board(whiteKing, blackKing, "c8" to ChessPiece(ChessSide.BLACK, PieceType.BISHOP)),
            ),
        )
        // Same-color bishops (c1 and f8 are both dark squares).
        assertTrue(
            DrawDetector.isInsufficientMaterial(
                board(
                    whiteKing,
                    blackKing,
                    "c1" to ChessPiece(ChessSide.WHITE, PieceType.BISHOP),
                    "f8" to ChessPiece(ChessSide.BLACK, PieceType.BISHOP),
                ),
            ),
        )
        // Opposite-color bishops can still mate with help.
        assertFalse(
            DrawDetector.isInsufficientMaterial(
                board(
                    whiteKing,
                    blackKing,
                    "c1" to ChessPiece(ChessSide.WHITE, PieceType.BISHOP),
                    "c8" to ChessPiece(ChessSide.BLACK, PieceType.BISHOP),
                ),
            ),
        )
        // Knight vs knight allows helpmates.
        assertFalse(
            DrawDetector.isInsufficientMaterial(
                board(
                    whiteKing,
                    blackKing,
                    "b1" to ChessPiece(ChessSide.WHITE, PieceType.KNIGHT),
                    "b8" to ChessPiece(ChessSide.BLACK, PieceType.KNIGHT),
                ),
            ),
        )
        for (type in listOf(PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN)) {
            assertFalse(
                DrawDetector.isInsufficientMaterial(
                    board(whiteKing, blackKing, "d4" to ChessPiece(ChessSide.WHITE, type)),
                ),
            )
        }
    }

    @Test
    fun `statusFor reports insufficient material after captures strip the board`() {
        // Pseudo-moves: the replay is legality-agnostic (Stockfish vets
        // real games upstream), so both queens vacuum the board, white's
        // queen takes black's, and the black king takes white's queen,
        // leaving king versus king.
        val whiteQueen = listOf(
            "d1a7", "a7b7", "b7c7", "c7d7", "d7e7", "e7f7", "f7g7", "g7h7",
            "h7h8", "h8g8", "g8f8", "f8c8", "c8b8", "b8a8",
        )
        val blackQueen = listOf(
            "d8a2", "a2b2", "b2c2", "c2d2", "d2e2", "e2f2", "f2g2", "g2h2",
            "h2h1", "h1g1", "g1f1", "f1c1", "c1b1", "b1a1",
        )
        val moves = whiteQueen.zip(blackQueen).flatMap { (w, b) -> listOf(w, b) } +
            listOf("a8a1", "e8a1")
        val status = DrawDetector.statusFor(MoveHistory(moves))

        assertEquals(AutomaticDraw.INSUFFICIENT_MATERIAL, status.automatic)
        assertNull(status.claimable)
    }
}
