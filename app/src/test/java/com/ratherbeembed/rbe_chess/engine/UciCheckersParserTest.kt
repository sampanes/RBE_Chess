package com.ratherbeembed.rbe_chess.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UciCheckersParserTest {

    @Test
    fun `hasCheckers parses non-empty checkers line as check`() {
        assertTrue(UciCheckersParser.hasCheckers("Checkers: h4")!!)
        assertTrue(UciCheckersParser.hasCheckers("  Checkers: e2 g2  ")!!)
    }

    @Test
    fun `hasCheckers parses empty checkers line as no check`() {
        assertFalse(UciCheckersParser.hasCheckers("Checkers:")!!)
        assertFalse(UciCheckersParser.hasCheckers("  Checkers:   ")!!)
    }

    @Test
    fun `hasCheckers ignores unrelated lines`() {
        assertNull(UciCheckersParser.hasCheckers("Fen: rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
        assertNull(UciCheckersParser.hasCheckers("bestmove e2e4"))
    }
}
