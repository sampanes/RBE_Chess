package com.ratherbeembed.rbe_chess.narrative

import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.engine.AnalysisSummary

object NarrativeTone {
    fun emotionalPrefix(
        before: AnalysisSummary?,
        after: AnalysisSummary?,
        mover: ChessSide,
    ): String? {
        val beforeCp = before?.whiteComparisonCp ?: return null
        val afterCp = after?.whiteComparisonCp ?: return null
        val whiteGain = afterCp - beforeCp
        val moverGain = if (mover == ChessSide.WHITE) whiteGain else -whiteGain
        return when {
            moverGain <= -200 -> "Blunder."
            moverGain <= -100 -> "Mistake."
            moverGain >= 200 -> "Great move."
            moverGain >= 100 -> "Sharp."
            else -> null
        }
    }
}
