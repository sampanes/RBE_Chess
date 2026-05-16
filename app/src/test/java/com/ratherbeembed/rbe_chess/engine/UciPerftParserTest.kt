package com.ratherbeembed.rbe_chess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UciPerftParserTest {

    @Test
    fun `moveFromLine parses normal moves`() {
        assertEquals("e2e4", UciPerftParser.moveFromLine("e2e4: 20"))
        assertEquals("g1f3", UciPerftParser.moveFromLine("  g1f3: 1  "))
    }

    @Test
    fun `moveFromLine parses promotion moves`() {
        assertEquals("e7e8q", UciPerftParser.moveFromLine("e7e8q: 1"))
        assertEquals("a2a1n", UciPerftParser.moveFromLine("a2a1n: 1"))
    }

    @Test
    fun `moveFromLine ignores non-move output`() {
        assertNull(UciPerftParser.moveFromLine("Nodes searched: 20"))
        assertNull(UciPerftParser.moveFromLine("info string NNUE loaded"))
        assertNull(UciPerftParser.moveFromLine("bestmove e2e4"))
    }
}
