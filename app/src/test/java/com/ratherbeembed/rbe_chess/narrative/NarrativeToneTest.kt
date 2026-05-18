package com.ratherbeembed.rbe_chess.narrative

import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.engine.AnalysisSummary
import com.ratherbeembed.rbe_chess.engine.MateScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NarrativeToneTest {

    @Test
    fun `white gain is good for white mover`() {
        assertEquals(
            "Great move.",
            NarrativeTone.emotionalPrefix(
                before = cp(20),
                after = cp(240),
                mover = ChessSide.WHITE,
            ),
        )
    }

    @Test
    fun `white gain is bad for black mover`() {
        assertEquals(
            "Blunder.",
            NarrativeTone.emotionalPrefix(
                before = cp(20),
                after = cp(240),
                mover = ChessSide.BLACK,
            ),
        )
    }

    @Test
    fun `white loss is good for black mover`() {
        assertEquals(
            "Sharp.",
            NarrativeTone.emotionalPrefix(
                before = cp(40),
                after = cp(-80),
                mover = ChessSide.BLACK,
            ),
        )
    }

    @Test
    fun `small eval change is neutral`() {
        assertNull(
            NarrativeTone.emotionalPrefix(
                before = cp(20),
                after = cp(85),
                mover = ChessSide.WHITE,
            ),
        )
    }

    @Test
    fun `mate scores compare through normalized winner`() {
        assertEquals(
            "Great move.",
            NarrativeTone.emotionalPrefix(
                before = cp(300),
                after = mate(ChessSide.WHITE, plies = 3),
                mover = ChessSide.WHITE,
            ),
        )
    }

    private fun cp(value: Int) =
        AnalysisSummary(
            whiteCentipawns = value,
            mate = null,
            bestMove = null,
            principalVariation = emptyList(),
        )

    private fun mate(winner: ChessSide, plies: Int) =
        AnalysisSummary(
            whiteCentipawns = null,
            mate = MateScore(winner = winner, plies = plies),
            bestMove = null,
            principalVariation = emptyList(),
        )
}
