package com.ratherbeembed.rbe_chess.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardProjectorTest {

    @Test
    fun `initial board has normal back ranks and pawns`() {
        val board = BoardProjector.fromHistory(MoveHistory.EMPTY)

        assertEquals(ChessPiece(ChessSide.WHITE, PieceType.KING), board.at("e1"))
        assertEquals(ChessPiece(ChessSide.BLACK, PieceType.KING), board.at("e8"))
        assertEquals(ChessPiece(ChessSide.WHITE, PieceType.PAWN), board.at("a2"))
        assertEquals(ChessPiece(ChessSide.BLACK, PieceType.PAWN), board.at("h7"))
    }

    @Test
    fun `simple move updates source target and last move`() {
        val board = BoardProjector.fromHistory(MoveHistory.EMPTY.append("e2e4"))

        assertNull(board.at("e2"))
        assertEquals(ChessPiece(ChessSide.WHITE, PieceType.PAWN), board.at("e4"))
        assertEquals("e2e4", board.lastMove?.uci)
        assertEquals(BoardSquare.fromUci("e2"), board.lastMove?.from)
        assertEquals(BoardSquare.fromUci("e4"), board.lastMove?.to)
    }

    @Test
    fun `capture replaces occupied target`() {
        val history = MoveHistory.EMPTY
            .append("e2e4")
            .append("d7d5")
            .append("e4d5")
        val board = BoardProjector.fromHistory(history)

        assertNull(board.at("e4"))
        assertEquals(ChessPiece(ChessSide.WHITE, PieceType.PAWN), board.at("d5"))
    }

    @Test
    fun `castling moves rook with king`() {
        val history = MoveHistory.EMPTY
            .append("g1f3")
            .append("g8f6")
            .append("f1e2")
            .append("b8c6")
            .append("e1g1")
        val board = BoardProjector.fromHistory(history)

        assertEquals(ChessPiece(ChessSide.WHITE, PieceType.KING), board.at("g1"))
        assertEquals(ChessPiece(ChessSide.WHITE, PieceType.ROOK), board.at("f1"))
        assertNull(board.at("e1"))
        assertNull(board.at("h1"))
    }

    @Test
    fun `promotion replaces pawn with requested piece`() {
        val board = BoardProjector.fromHistory(MoveHistory.EMPTY.append("a2a8q"))

        assertEquals(ChessPiece(ChessSide.WHITE, PieceType.QUEEN), board.at("a8"))
        assertNull(board.at("a2"))
    }

    @Test
    fun `en passant removes the captured pawn`() {
        val history = MoveHistory.EMPTY
            .append("e2e4")
            .append("a7a6")
            .append("e4e5")
            .append("d7d5")
            .append("e5d6")
        val board = BoardProjector.fromHistory(history)

        assertEquals(ChessPiece(ChessSide.WHITE, PieceType.PAWN), board.at("d6"))
        assertNull(board.at("d5"))
        assertNull(board.at("e5"))
    }

    @Test
    fun `invalid moves are ignored without clearing previous last move`() {
        val history = MoveHistory.EMPTY
            .append("e2e4")
            .append("z9z1")
            .append("e2e5")
        val board = BoardProjector.fromHistory(history)

        assertEquals(ChessPiece(ChessSide.WHITE, PieceType.PAWN), board.at("e4"))
        assertEquals("e2e4", board.lastMove?.uci)
    }

    private fun BoardSnapshot.at(square: String): ChessPiece? {
        val parsed = BoardSquare.fromUci(square) ?: error("bad square $square")
        return pieceAt(parsed.file, parsed.rank)
    }
}
