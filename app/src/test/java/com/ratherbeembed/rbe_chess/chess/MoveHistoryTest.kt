package com.ratherbeembed.rbe_chess.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveHistoryTest {

    @Test
    fun `EMPTY has size 0 and no moves`() {
        assertEquals(0, MoveHistory.EMPTY.size)
        assertTrue(MoveHistory.EMPTY.moves.isEmpty())
    }

    @Test
    fun `append returns a new instance with the move added`() {
        val a = MoveHistory.EMPTY
        val b = a.append("e2e4")
        assertEquals(listOf("e2e4"), b.moves)
        assertEquals(1, b.size)
    }

    @Test
    fun `append does not mutate the original`() {
        val a = MoveHistory.EMPTY
        val b = a.append("e2e4")
        assertNotSame(a, b)
        assertEquals(0, a.size)
        assertEquals(1, b.size)
    }

    @Test
    fun `append preserves order across multiple plies`() {
        val h = MoveHistory.EMPTY
            .append("e2e4")
            .append("e7e5")
            .append("g1f3")
            .append("b8c6")
        assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6"), h.moves)
        assertEquals(4, h.size)
    }

    @Test
    fun `EMPTY companion is the canonical empty instance`() {
        assertSame(MoveHistory.EMPTY, MoveHistory.EMPTY)
        assertEquals(MoveHistory.EMPTY, MoveHistory(emptyList()))
    }

    @Test
    fun `append accepts promotion-suffixed UCI verbatim`() {
        val h = MoveHistory.EMPTY.append("e7e8q")
        assertEquals(listOf("e7e8q"), h.moves)
    }
}
