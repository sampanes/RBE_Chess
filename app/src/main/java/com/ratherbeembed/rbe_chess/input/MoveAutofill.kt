package com.ratherbeembed.rbe_chess.input

import com.ratherbeembed.rbe_chess.engine.ScoredMove

data class EvaluationAutofill(
    val uci: String,
    val scoreGapCp: Int,
)

object MoveAutofill {

    fun onlyLegalMove(legalMoves: Set<String>): String? =
        legalMoves.singleOrNull()

    fun onlyLegalMoveFrom(legalMoves: Set<String>, fromSquare: String): String? {
        require(fromSquare.length == 2) {
            "expected 2-char source square, got '$fromSquare'"
        }
        return legalMoves
            .filter { it.length >= 4 && it.startsWith(fromSquare) }
            .singleOrNull()
    }

    fun clearBestScoredMove(
        scoredMoves: List<ScoredMove>,
        minimumScoreGapCp: Int,
    ): EvaluationAutofill? {
        require(minimumScoreGapCp >= 0) {
            "minimum score gap must be non-negative"
        }
        val ranked = scoredMoves
            .groupBy { it.uci }
            .map { (_, scores) -> scores.maxBy { it.comparisonScoreCp } }
            .sortedByDescending { it.comparisonScoreCp }

        if (ranked.size < 2) return null

        val scoreGap = ranked[0].comparisonScoreCp - ranked[1].comparisonScoreCp
        return if (scoreGap >= minimumScoreGapCp) {
            EvaluationAutofill(uci = ranked[0].uci, scoreGapCp = scoreGap)
        } else {
            null
        }
    }
}
