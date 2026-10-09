package com.ratherbeembed.rbe_chess.narrator

import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.AnalysisSummary
import kotlin.math.max

/**
 * Bridges the game loop's cached [AnalysisSummary] values (white-relative,
 * from short narrative analyses) to the narrator's side-relative facts.
 */
object AnalysisFacts {

    /**
     * Quality of [move] by [mover]: the eval drop from the position before
     * (best play) to the position after, from the mover's view. A move that
     * matches the analysis's best move counts as best regardless of noise
     * between the two short searches. Null without both analyses.
     */
    fun quality(
        before: AnalysisSummary?,
        after: AnalysisSummary?,
        mover: ChessSide,
        move: String,
    ): QualityFacts? {
        if (before == null || after == null) return null
        val isBest = before.bestMove == move
        val opponent = if (mover == ChessSide.WHITE) ChessSide.BLACK else ChessSide.WHITE
        val hadMate = before.mate?.winner == mover
        val keepsMate = after.mate?.winner == mover
        val facedMate = before.mate?.winner == opponent
        val facesMate = after.mate?.winner == opponent
        val missedMate = hadMate && !keepsMate && !isBest
        val allowedMate = facesMate && !facedMate && !isBest
        if (before.mate != null || after.mate != null) {
            return QualityFacts(cpLoss = 0, isBest = isBest, missedMate = missedMate, allowedMate = allowedMate)
        }
        val beforeCp = before.whiteCentipawns ?: return null
        val afterCp = after.whiteCentipawns ?: return null
        val sign = if (mover == ChessSide.WHITE) 1 else -1
        val loss = if (isBest) 0 else max(0, sign * (beforeCp - afterCp))
        return QualityFacts(cpLoss = loss, isBest = isBest)
    }

    /**
     * The engine's pick for the side to move after [history], from that
     * position's analysis: best move, principal variation and eval.
     */
    fun engineEvent(history: MoveHistory, analysis: AnalysisSummary?): EngineEvent? {
        val summary = analysis ?: return null
        val best = summary.bestMove ?: return null
        val stm = if (history.size % 2 == 0) ChessSide.WHITE else ChessSide.BLACK
        // MateScore.plies carries UCI's raw `score mate N`, which counts
        // moves, so it maps straight onto "mate in N".
        val eval = summary.mate?.let { mate ->
            Eval(mate = if (mate.winner == stm) mate.plies else -mate.plies)
        } ?: summary.whiteCentipawns?.let { cp ->
            Eval(cp = if (stm == ChessSide.WHITE) cp else -cp)
        } ?: Eval()
        return NarrationFacts.engineEvent(history, best, eval, pv = summary.principalVariation)
    }
}
