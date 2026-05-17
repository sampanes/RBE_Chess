package com.ratherbeembed.rbe_chess.input

import com.ratherbeembed.rbe_chess.engine.EngineScore
import com.ratherbeembed.rbe_chess.engine.ScoredMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MoveAutofillTest {

    @Test
    fun `onlyLegalMove returns the move when exactly one legal move exists`() {
        assertEquals("e2e4", MoveAutofill.onlyLegalMove(setOf("e2e4")))
    }

    @Test
    fun `onlyLegalMove returns null when there are zero or many legal moves`() {
        assertNull(MoveAutofill.onlyLegalMove(emptySet()))
        assertNull(MoveAutofill.onlyLegalMove(setOf("e2e4", "d2d4")))
    }

    @Test
    fun `onlyLegalMoveFrom returns the only legal move from a selected source`() {
        val legal = setOf("e2e4", "d2d4", "g1f3")

        assertEquals("e2e4", MoveAutofill.onlyLegalMoveFrom(legal, "e2"))
    }

    @Test
    fun `onlyLegalMoveFrom returns null when selected source has multiple moves`() {
        val legal = setOf("e2e3", "e2e4", "g1f3")

        assertNull(MoveAutofill.onlyLegalMoveFrom(legal, "e2"))
    }

    @Test
    fun `onlyLegalMoveFrom returns null when selected source has no moves`() {
        val legal = setOf("e2e4", "g1f3")

        assertNull(MoveAutofill.onlyLegalMoveFrom(legal, "a1"))
    }

    @Test
    fun `clearBestScoredMove returns top move when score gap is large enough`() {
        val scored = listOf(
            ScoredMove("e2e4", EngineScore.Centipawns(82)),
            ScoredMove("d2d4", EngineScore.Centipawns(-24)),
            ScoredMove("g1f3", EngineScore.Centipawns(8)),
        )

        assertEquals(
            EvaluationAutofill(uci = "e2e4", scoreGapCp = 74),
            MoveAutofill.clearBestScoredMove(scored, minimumScoreGapCp = 70),
        )
    }

    @Test
    fun `clearBestScoredMove returns null when top two scores are close`() {
        val scored = listOf(
            ScoredMove("e2e4", EngineScore.Centipawns(52)),
            ScoredMove("d2d4", EngineScore.Centipawns(24)),
        )

        assertNull(MoveAutofill.clearBestScoredMove(scored, minimumScoreGapCp = 100))
    }

    @Test
    fun `clearBestScoredMove requires a next-best comparison`() {
        val scored = listOf(ScoredMove("e2e4", EngineScore.Centipawns(120)))

        assertNull(MoveAutofill.clearBestScoredMove(scored, minimumScoreGapCp = 100))
    }

    @Test
    fun `clearBestScoredMove treats mating moves as clearly better than centipawn moves`() {
        val scored = listOf(
            ScoredMove("h5f7", EngineScore.Mate(2)),
            ScoredMove("e2e4", EngineScore.Centipawns(900)),
        )

        assertEquals(
            EvaluationAutofill(uci = "h5f7", scoreGapCp = 997_100),
            MoveAutofill.clearBestScoredMove(scored, minimumScoreGapCp = 100),
        )
    }

    @Test
    fun `clearBestScoredMove rejects negative score gap thresholds`() {
        assertThrows(IllegalArgumentException::class.java) {
            MoveAutofill.clearBestScoredMove(emptyList(), minimumScoreGapCp = -1)
        }
    }
}
