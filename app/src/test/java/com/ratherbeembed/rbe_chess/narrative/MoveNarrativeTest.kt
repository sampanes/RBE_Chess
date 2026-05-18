package com.ratherbeembed.rbe_chess.narrative

import com.ratherbeembed.rbe_chess.chess.MoveHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveNarrativeTest {

    @Test
    fun `quiet ordinary move has no extra narrative`() {
        assertNull(
            MoveNarrative.forMove(
                historyBefore = MoveHistory.EMPTY,
                move = "e2e4",
            ),
        )
    }

    @Test
    fun `capture names captured piece and square`() {
        val history = MoveHistory(listOf("e2e4", "d7d5"))

        assertEquals(
            "Takes pawn on D five.",
            MoveNarrative.forMove(history, "e4d5"),
        )
    }

    @Test
    fun `recapture on same square is a trade`() {
        val history = MoveHistory(listOf("e2e4", "d7d5", "e4d5"))

        assertEquals(
            "Trades pawns on D five.",
            MoveNarrative.forMove(history, "d8d5"),
        )
    }

    @Test
    fun `promotion gets compact queen phrase`() {
        assertEquals(
            "Queens.",
            MoveNarrative.forMove(
                historyBefore = MoveHistory(listOf("a2a7", "h7h6")),
                move = "a7a8q",
            ),
        )
    }

    @Test
    fun `castling gets compact phrase`() {
        val history = MoveHistory(listOf("g1f3", "g8f6", "f1e2", "b8c6"))

        assertEquals(
            "Castles.",
            MoveNarrative.forMove(history, "e1g1"),
        )
    }

    @Test
    fun `forced and only reply phrases append to move fact`() {
        val history = MoveHistory(listOf("e2e4", "d7d5"))

        assertEquals(
            "Mistake. Forced. Takes pawn on D five. Only reply: D eight to D five.",
            MoveNarrative.forMove(
                historyBefore = history,
                move = "e4d5",
                wasForced = true,
                onlyReply = "d8d5",
                emotionalPrefix = "Mistake.",
            ),
        )
    }

    @Test
    fun `latestFromHistory describes latest move`() {
        val history = MoveHistory(listOf("e2e4", "d7d5", "e4d5"))

        assertEquals("Takes pawn on D five.", MoveNarrative.latestFromHistory(history))
    }
}
