package com.ratherbeembed.rbe_chess.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromotionPickStateTest {

    @Test
    fun `fromLegalMoves detects promotion candidates for the entered base move`() {
        val state = PromotionPickState.fromLegalMoves(
            baseMove = "e7e8",
            legalMoves = setOf("e7e8q", "e7e8r", "e7e8b", "e7e8n", "a2a3"),
        )

        assertEquals("e7e8", state?.baseMove)
        assertEquals(
            setOf(
                PromotionPiece.QUEEN,
                PromotionPiece.ROOK,
                PromotionPiece.BISHOP,
                PromotionPiece.KNIGHT,
            ),
            state?.legalPieces,
        )
    }

    @Test
    fun `fromLegalMoves ignores other promotion squares`() {
        val state = PromotionPickState.fromLegalMoves(
            baseMove = "e7e8",
            legalMoves = setOf("a7a8q", "a7a8r", "e7f8q"),
        )

        assertNull(state)
    }

    @Test
    fun `fromLegalMoves returns null for ordinary legal moves`() {
        val state = PromotionPickState.fromLegalMoves(
            baseMove = "e2e4",
            legalMoves = setOf("e2e4", "g1f3"),
        )

        assertNull(state)
    }

    @Test
    fun `direct keys choose the mapped promotion piece`() {
        val state = PromotionPickState(
            baseMove = "e7e8",
            legalPieces = setOf(
                PromotionPiece.QUEEN,
                PromotionPiece.ROOK,
                PromotionPiece.BISHOP,
                PromotionPiece.KNIGHT,
            ),
        )

        assertEquals("e7e8n", state.choose(ChessKey.D))
        assertEquals("e7e8b", state.choose(ChessKey.F))
        assertEquals("e7e8r", state.choose(ChessKey.J))
        assertEquals("e7e8q", state.choose(ChessKey.K))
        assertEquals("e7e8q", state.choose(ChessKey.SPACE))
    }

    @Test
    fun `non picker keys do not choose a promotion piece`() {
        val state = PromotionPickState(
            baseMove = "e7e8",
            legalPieces = setOf(PromotionPiece.QUEEN),
        )

        assertNull(state.choose(ChessKey.REPEAT_LAST))
    }

    @Test
    fun `unavailable promotion pieces are rejected`() {
        val state = PromotionPickState(
            baseMove = "e7e8",
            legalPieces = setOf(PromotionPiece.QUEEN),
        )

        assertNull(state.choose(ChessKey.D))
    }
}
