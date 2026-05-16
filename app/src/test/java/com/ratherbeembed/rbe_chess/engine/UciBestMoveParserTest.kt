package com.ratherbeembed.rbe_chess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UciBestMoveParserTest {

    @Test
    fun `moveFromLine parses normal and promotion bestmoves`() {
        assertEquals("e2e4", UciBestMoveParser.moveFromLine("bestmove e2e4 ponder e7e5"))
        assertEquals("e7e8q", UciBestMoveParser.moveFromLine("bestmove e7e8q"))
    }

    @Test
    fun `moveFromLine parses none token`() {
        assertEquals("(none)", UciBestMoveParser.moveFromLine("bestmove (none)"))
    }

    @Test
    fun `moveFromLine ignores non-bestmove lines`() {
        assertNull(UciBestMoveParser.moveFromLine("info depth 1 score mate 0"))
        assertNull(UciBestMoveParser.moveFromLine("e2e4: 1"))
    }

    @Test
    fun `hasMateScore detects mate info`() {
        assertTrue(UciBestMoveParser.hasMateScore("info depth 0 score mate 0"))
        assertTrue(UciBestMoveParser.hasMateScore("info depth 3 score mate -2 nodes 1"))
        assertFalse(UciBestMoveParser.hasMateScore("info depth 1 score cp 0"))
    }
}
