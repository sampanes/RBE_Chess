package com.ratherbeembed.rbe_chess.input

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardGrammarTest {

    @Test
    fun `D maps to CycleFromFile`() {
        assertEquals(GrammarAction.CycleFromFile, KeyboardGrammar.translate(ChessKey.D))
    }

    @Test
    fun `F maps to CycleFromRank`() {
        assertEquals(GrammarAction.CycleFromRank, KeyboardGrammar.translate(ChessKey.F))
    }

    @Test
    fun `J maps to CycleToFile`() {
        assertEquals(GrammarAction.CycleToFile, KeyboardGrammar.translate(ChessKey.J))
    }

    @Test
    fun `K maps to CycleToRank`() {
        assertEquals(GrammarAction.CycleToRank, KeyboardGrammar.translate(ChessKey.K))
    }

    @Test
    fun `SPACE maps to Commit`() {
        assertEquals(GrammarAction.Commit, KeyboardGrammar.translate(ChessKey.SPACE))
    }

    @Test
    fun `IGNORED maps to Ignored`() {
        assertEquals(GrammarAction.Ignored, KeyboardGrammar.translate(ChessKey.IGNORED))
    }

    @Test
    fun `apply CycleFromFile on default selects a`() {
        val after = KeyboardGrammar.apply(GrammarAction.CycleFromFile, MoveBuffer.DEFAULT)
        assertEquals("a1a1", after.toUciString())
        assertEquals(0, after.fromFileIdx)
    }

    @Test
    fun `apply Commit resets all coords to unset`() {
        val populated = MoveBuffer.DEFAULT
            .cycleFromFile().cycleFromFile()  // press 2 -> 'b'
            .cycleToRank().cycleToRank()      // press 2 -> 2
        assertEquals("b1a2", populated.toUciString())
        val after = KeyboardGrammar.apply(GrammarAction.Commit, populated)
        assertEquals(MoveBuffer.DEFAULT, after)
    }

    @Test
    fun `apply Ignored leaves the buffer unchanged`() {
        val populated = MoveBuffer.DEFAULT.cycleFromFile()
        val after = KeyboardGrammar.apply(GrammarAction.Ignored, populated)
        assertEquals(populated, after)
    }
}
