package com.ratherbeembed.rbe_chess.engine

import com.ratherbeembed.rbe_chess.chess.ChessSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UciAnalysisParserTest {

    @Test
    fun `analysisFromInfoLine parses centipawn score and full pv`() {
        val parsed = UciAnalysisParser.analysisFromInfoLine(
            "info depth 12 score cp -34 nodes 12345 pv g1f3 d7d5 c2c4",
        )

        assertEquals(
            UciAnalysisInfo(
                score = EngineScore.Centipawns(-34),
                principalVariation = listOf("g1f3", "d7d5", "c2c4"),
            ),
            parsed,
        )
    }

    @Test
    fun `analysisFromInfoLine parses mate score`() {
        val parsed = UciAnalysisParser.analysisFromInfoLine(
            "info depth 8 score mate -2 pv h5f7",
        )

        assertEquals(
            UciAnalysisInfo(
                score = EngineScore.Mate(-2),
                principalVariation = listOf("h5f7"),
            ),
            parsed,
        )
    }

    @Test
    fun `analysisFromInfoLine ignores lines without score or pv`() {
        assertNull(UciAnalysisParser.analysisFromInfoLine("bestmove e2e4"))
        assertNull(UciAnalysisParser.analysisFromInfoLine("info depth 1 score cp 0"))
        assertNull(UciAnalysisParser.analysisFromInfoLine("info depth 1 pv e2e4"))
    }

    @Test
    fun `analysis summary normalizes centipawns to white pov`() {
        val raw = UciAnalysisInfo(
            score = EngineScore.Centipawns(80),
            principalVariation = listOf("e7e5", "g1f3"),
        )

        assertEquals(
            AnalysisSummary(
                whiteCentipawns = -80,
                mate = null,
                bestMove = "e7e5",
                principalVariation = listOf("e7e5", "g1f3"),
            ),
            AnalysisSummary.fromRaw(raw, sideToMove = ChessSide.BLACK),
        )
    }

    @Test
    fun `analysis summary normalizes mate winner`() {
        val raw = UciAnalysisInfo(
            score = EngineScore.Mate(3),
            principalVariation = listOf("d8h4"),
        )

        assertEquals(
            AnalysisSummary(
                whiteCentipawns = null,
                mate = MateScore(winner = ChessSide.BLACK, plies = 3),
                bestMove = "d8h4",
                principalVariation = listOf("d8h4"),
            ),
            AnalysisSummary.fromRaw(raw, sideToMove = ChessSide.BLACK),
        )
    }
}
