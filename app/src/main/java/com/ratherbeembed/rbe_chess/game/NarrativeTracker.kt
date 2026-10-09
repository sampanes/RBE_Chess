package com.ratherbeembed.rbe_chess.game

import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.AnalysisSummary
import com.ratherbeembed.rbe_chess.engine.StockfishEngine
import com.ratherbeembed.rbe_chess.narrative.MoveNarrative
import com.ratherbeembed.rbe_chess.narrative.NarrativeTone
import com.ratherbeembed.rbe_chess.narrator.AnalysisFacts
import com.ratherbeembed.rbe_chess.narrator.EngineEvent
import com.ratherbeembed.rbe_chess.narrator.MoveEvent
import com.ratherbeembed.rbe_chess.narrator.NarrationFacts
import com.ratherbeembed.rbe_chess.narrator.Narrator

private const val NARRATIVE_ANALYSIS_MOVETIME_MS = 600L
private const val MAX_DETAIL_LEVEL = 3

/**
 * Remembers the narrative phrase appended to Repeat Last, and caches the
 * most recent engine analysis so consecutive plies don't re-analyze the
 * shared position (the "after" of one ply is the "before" of the next).
 *
 * Also owns the repeat ladder (narrative-sidekick SPEC section 5): the
 * first Repeat Last after a board change is the classic replay; each
 * further press climbs to the sidekick narrator's L2, then L3, for the
 * last move and the engine's pick in the resulting position.
 */
internal class NarrativeTracker(private val engine: StockfishEngine) {

    private class CachedAnalysis(val history: MoveHistory, val summary: AnalysisSummary)

    var latest: String? = null
        private set

    private var cachedAnalysis: CachedAnalysis? = null
    private val narrator = Narrator()
    private var detailMove: MoveEvent? = null
    private var detailEngine: EngineEvent? = null
    private var repeatPresses = 0

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
        self: Boolean,
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
        detailMove = NarrationFacts.moveEvent(
            historyBefore = historyBefore,
            uci = move,
            self = self,
            quality = AnalysisFacts.quality(beforeAnalysis, afterAnalysis, mover, move),
        )
        detailEngine = AnalysisFacts.engineEvent(historyBefore.append(move), afterAnalysis)
        repeatPresses = 0
    }

    /** Geometry-only narrative for the last ply (no engine tone available). */
    fun rememberLatestOf(history: MoveHistory) {
        latest = MoveNarrative.latestFromHistory(history)
        detailMove = history.moves.lastOrNull()?.let {
            NarrationFacts.moveEvent(MoveHistory(history.moves.dropLast(1)), it)
        }
        detailEngine = null
        repeatPresses = 0
    }

    /**
     * Counts one Repeat Last press. Returns null when the caller should
     * play the classic replay (first press, or nothing to detail), else
     * the sidekick text for the next rung of the ladder.
     */
    fun nextRepeatDetail(): String? {
        repeatPresses += 1
        if (repeatPresses == 1) return null
        val move = detailMove ?: return null
        val level = minOf(repeatPresses, MAX_DETAIL_LEVEL)
        return listOfNotNull(
            narrator.narrate(move, level),
            detailEngine?.let { narrator.narrate(it, level) },
        ).joinToString(" ")
    }

    fun invalidateAnalysis() {
        cachedAnalysis = null
    }

    fun clear() {
        latest = null
        cachedAnalysis = null
        detailMove = null
        detailEngine = null
        repeatPresses = 0
    }
}
