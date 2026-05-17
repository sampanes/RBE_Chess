package com.ratherbeembed.rbe_chess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UciScoredMoveParserTest {

    @Test
    fun `scoredMoveFromInfoLine parses centipawn score with multipv and pv move`() {
        val parsed = UciScoredMoveParser.scoredMoveFromInfoLine(
            "info depth 12 multipv 2 score cp -34 nodes 12345 pv g1f3 d7d5",
        )

        assertEquals(
            UciScoredMoveInfo(
                multipv = 2,
                move = ScoredMove("g1f3", EngineScore.Centipawns(-34)),
            ),
            parsed,
        )
    }

    @Test
    fun `scoredMoveFromInfoLine parses mate score`() {
        val parsed = UciScoredMoveParser.scoredMoveFromInfoLine(
            "info depth 8 multipv 1 score mate 3 pv h5f7",
        )

        assertEquals(
            UciScoredMoveInfo(
                multipv = 1,
                move = ScoredMove("h5f7", EngineScore.Mate(3)),
            ),
            parsed,
        )
    }

    @Test
    fun `scoredMoveFromInfoLine defaults missing multipv to one`() {
        val parsed = UciScoredMoveParser.scoredMoveFromInfoLine(
            "info depth 4 score cp 12 pv e2e4 e7e5",
        )

        assertEquals(
            UciScoredMoveInfo(
                multipv = 1,
                move = ScoredMove("e2e4", EngineScore.Centipawns(12)),
            ),
            parsed,
        )
    }

    @Test
    fun `scoredMoveFromInfoLine ignores lines without score or pv`() {
        assertNull(UciScoredMoveParser.scoredMoveFromInfoLine("bestmove e2e4"))
        assertNull(UciScoredMoveParser.scoredMoveFromInfoLine("info depth 1 score cp 0"))
        assertNull(UciScoredMoveParser.scoredMoveFromInfoLine("info depth 1 pv e2e4"))
    }
}
