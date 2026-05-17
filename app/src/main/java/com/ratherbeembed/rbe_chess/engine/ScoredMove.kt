package com.ratherbeembed.rbe_chess.engine

private const val MATE_SCORE_CP = 1_000_000
private const val MATE_PLY_STEP_CP = 1_000

data class ScoredMove(
    val uci: String,
    val score: EngineScore,
) {
    val comparisonScoreCp: Int get() = score.comparisonScoreCp
}

sealed interface EngineScore {
    val comparisonScoreCp: Int

    data class Centipawns(val value: Int) : EngineScore {
        override val comparisonScoreCp: Int = value
    }

    data class Mate(val plies: Int) : EngineScore {
        override val comparisonScoreCp: Int =
            when {
                plies > 0 -> MATE_SCORE_CP - (plies * MATE_PLY_STEP_CP)
                plies < 0 -> -MATE_SCORE_CP + (-plies * MATE_PLY_STEP_CP)
                else -> MATE_SCORE_CP
            }
    }
}
