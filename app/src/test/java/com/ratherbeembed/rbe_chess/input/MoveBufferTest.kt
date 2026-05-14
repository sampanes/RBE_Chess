package com.ratherbeembed.rbe_chess.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveBufferTest {

    @Test
    fun `default buffer displays a1a1 and all coords are unset`() {
        val b = MoveBuffer.DEFAULT
        assertEquals("a1a1", b.toUciString())
        assertNull(b.fromFileIdx)
        assertNull(b.fromRankIdx)
        assertNull(b.toFileIdx)
        assertNull(b.toRankIdx)
    }

    @Test
    fun `first D press selects a not b`() {
        val b = MoveBuffer.DEFAULT.cycleFromFile()
        assertEquals('a', b.fromFile)
        assertEquals("a1a1", b.toUciString())
    }

    @Test
    fun `second D press advances to b`() {
        val b = MoveBuffer.DEFAULT.cycleFromFile().cycleFromFile()
        assertEquals('b', b.fromFile)
    }

    @Test
    fun `D pressed 3 times yields c1 from default`() {
        val b = MoveBuffer.DEFAULT.cycleFromFile().cycleFromFile().cycleFromFile()
        assertEquals("c1a1", b.toUciString())
    }

    @Test
    fun `D pressed 8 times yields h`() {
        var b = MoveBuffer.DEFAULT
        repeat(8) { b = b.cycleFromFile() }
        assertEquals('h', b.fromFile)
    }

    @Test
    fun `D pressed 9 times wraps to a`() {
        var b = MoveBuffer.DEFAULT
        repeat(9) { b = b.cycleFromFile() }
        assertEquals('a', b.fromFile)
    }

    @Test
    fun `F pressed 9 times wraps to rank 1`() {
        var b = MoveBuffer.DEFAULT
        repeat(9) { b = b.cycleFromRank() }
        assertEquals(1, b.fromRank)
    }

    @Test
    fun `J pressed once selects a`() {
        val b = MoveBuffer.DEFAULT.cycleToFile()
        assertEquals('a', b.toFile)
    }

    @Test
    fun `K pressed once selects rank 1`() {
        val b = MoveBuffer.DEFAULT.cycleToRank()
        assertEquals(1, b.toRank)
    }

    @Test
    fun `each coordinate is independent`() {
        val b = MoveBuffer.DEFAULT
            .cycleFromFile().cycleFromFile().cycleFromFile()  // press 3 -> 'c'
            .cycleFromRank().cycleFromRank()                  // press 2 -> 2
            .cycleToFile().cycleToFile().cycleToFile().cycleToFile()  // press 4 -> 'd'
            .cycleToRank().cycleToRank().cycleToRank().cycleToRank().cycleToRank() // press 5 -> 5
        assertEquals("c2d5", b.toUciString())
    }

    @Test
    fun `untouched coords still render as a or 1 for the prompt`() {
        // Press only D three times. F, J, K untouched.
        val b = MoveBuffer.DEFAULT.cycleFromFile().cycleFromFile().cycleFromFile()
        assertEquals('c', b.fromFile)
        assertEquals(1, b.fromRank)
        assertEquals('a', b.toFile)
        assertEquals(1, b.toRank)
    }
}
