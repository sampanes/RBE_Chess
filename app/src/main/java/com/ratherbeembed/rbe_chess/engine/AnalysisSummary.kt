package com.ratherbeembed.rbe_chess.engine

import com.ratherbeembed.rbe_chess.chess.ChessSide

private const val MATE_COMPARISON_CP = 1_000_000
private const val MATE_PLY_STEP_CP = 1_000

data class AnalysisSummary(
    val whiteCentipawns: Int?,
    val mate: MateScore?,
    val bestMove: String?,
    val principalVariation: List<String>,
) {
    val whiteComparisonCp: Int?
        get() =
            whiteCentipawns ?: mate?.let {
                val distance = it.plies * MATE_PLY_STEP_CP
                if (it.winner == ChessSide.WHITE) {
                    MATE_COMPARISON_CP - distance
                } else {
                    -MATE_COMPARISON_CP + distance
                }
            }

    companion object {
        fun fromRaw(info: UciAnalysisInfo, sideToMove: ChessSide): AnalysisSummary {
            val whiteCp =
                (info.score as? EngineScore.Centipawns)
                    ?.let { if (sideToMove == ChessSide.WHITE) it.value else -it.value }
            val mate =
                (info.score as? EngineScore.Mate)
                    ?.let { rawMate ->
                        val winner =
                            if (rawMate.plies >= 0) sideToMove else sideToMove.opposite()
                        MateScore(winner = winner, plies = kotlin.math.abs(rawMate.plies))
                    }
            return AnalysisSummary(
                whiteCentipawns = whiteCp,
                mate = mate,
                bestMove = info.principalVariation.firstOrNull(),
                principalVariation = info.principalVariation,
            )
        }
    }
}

data class MateScore(
    val winner: ChessSide,
    val plies: Int,
)

private fun ChessSide.opposite(): ChessSide =
    if (this == ChessSide.WHITE) ChessSide.BLACK else ChessSide.WHITE
