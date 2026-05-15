package com.ratherbeembed.rbe_chess.speech

import com.ratherbeembed.rbe_chess.input.MoveBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpokenMoveFormatterTest {

    @Test
    fun `spokenFile uppercases lowercase file letter`() {
        assertEquals("A", SpokenMoveFormatter.spokenFile('a'))
        assertEquals("H", SpokenMoveFormatter.spokenFile('h'))
    }

    @Test
    fun `spokenRank spells out digits 1 to 8`() {
        assertEquals("one", SpokenMoveFormatter.spokenRank(1))
        assertEquals("two", SpokenMoveFormatter.spokenRank(2))
        assertEquals("eight", SpokenMoveFormatter.spokenRank(8))
    }

    @Test
    fun `spokenRank rejects out-of-range`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpokenMoveFormatter.spokenRank(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpokenMoveFormatter.spokenRank(9)
        }
    }

    @Test
    fun `spokenSquare formats file and rank`() {
        assertEquals("E two", SpokenMoveFormatter.spokenSquare('e', 2))
        assertEquals("B eight", SpokenMoveFormatter.spokenSquare('b', 8))
    }

    @Test
    fun `spokenInactivityPrompt on default buffer reads A one to A one`() {
        val prompt = SpokenMoveFormatter.spokenInactivityPrompt(MoveBuffer.DEFAULT)
        assertEquals("Saw A one to A one?", prompt)
    }

    @Test
    fun `spokenInactivityPrompt on populated buffer`() {
        val b = MoveBuffer.DEFAULT
            .cycleFromFile().cycleFromFile().cycleFromFile() // 'c'
            .cycleFromRank()                                  // 1
            .cycleToFile()                                    // 'a'
            .cycleToRank()                                    // 1
        val prompt = SpokenMoveFormatter.spokenInactivityPrompt(b)
        assertEquals("Saw C one to A one?", prompt)
    }

    @Test
    fun `spokenBestMove without promotion`() {
        assertEquals(
            "Best move: B eight to C six.",
            SpokenMoveFormatter.spokenBestMove("b8c6"),
        )
    }

    @Test
    fun `spokenBestMove with queen promotion`() {
        assertEquals(
            "Best move: E seven to E eight, queen.",
            SpokenMoveFormatter.spokenBestMove("e7e8q"),
        )
    }

    @Test
    fun `spokenBestMove with knight promotion`() {
        assertEquals(
            "Best move: A seven to A eight, knight.",
            SpokenMoveFormatter.spokenBestMove("a7a8n"),
        )
    }

    @Test
    fun `spokenBestMove rejects malformed UCI`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpokenMoveFormatter.spokenBestMove("e2")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpokenMoveFormatter.spokenBestMove("e7e8x")
        }
    }
}
