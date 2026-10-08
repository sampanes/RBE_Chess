package com.ratherbeembed.rbe_chess.game

import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.AnalysisSummary
import com.ratherbeembed.rbe_chess.engine.StockfishEngine
import com.ratherbeembed.rbe_chess.narrative.MoveNarrative
import com.ratherbeembed.rbe_chess.narrative.NarrativeTone

private const val NARRATIVE_ANALYSIS_MOVETIME_MS = 600L

/**
 * Remembers the narrative phrase appended to Repeat Last, and caches the
 * most recent engine analysis so consecutive plies don't re-analyze the
 * shared position (the "after" of one ply is the "before" of the next).
 */
internal class NarrativeTracker(private val engine: StockfishEngine) {

    private class CachedAnalysis(val history: MoveHistory, val summary: AnalysisSummary)

    var latest: String? = null
        private set

    private var cachedAnalysis: CachedAnalysis? = null

    suspend fun analysisFor(history: MoveHistory): AnalysisSummary? {
        cachedAnalysis
            ?.takeIf { it.history == history }
            ?.let { return it.summary }
        return engine.analyzePosition(
            uciMoves = history.moves,
            movetimeMs = NARRATIVE_ANALYSIS_MOVETIME_MS,
        )?.also {
            cachedAnalysis = CachedAnalysis(history, it)
        }
    }

    fun rememberMove(
        historyBefore: MoveHistory,
        move: String,
        mover: ChessSide,
        wasForced: Boolean,
        onlyReply: String?,
        beforeAnalysis: AnalysisSummary?,
        afterAnalysis: AnalysisSummary?,
    ) {
        latest = MoveNarrative.forMove(
            historyBefore = historyBefore,
            move = move,
            wasForced = wasForced,
            onlyReply = onlyReply,
            emotionalPrefix = NarrativeTone.emotionalPrefix(
                before = beforeAnalysis,
                after = afterAnalysis,
                mover = mover,
            ),
        )
    }

    /** Geometry-only narrative for the last ply (no engine tone available). */
    fun rememberLatestOf(history: MoveHistory) {
        latest = MoveNarrative.latestFromHistory(history)
    }

    fun invalidateAnalysis() {
        cachedAnalysis = null
    }

    fun clear() {
        latest = null
        cachedAnalysis = null
    }
}
