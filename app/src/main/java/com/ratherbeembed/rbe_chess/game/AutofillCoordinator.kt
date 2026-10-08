package com.ratherbeembed.rbe_chess.game

import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.StockfishEngine
import com.ratherbeembed.rbe_chess.input.MoveAutofill
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

internal const val SOURCE_AUTOFILL_DELAY_MS = INACTIVITY_PROMPT_MS
private const val AUTOFILL_MOVETIME_MS = 600L
private const val AUTOFILL_SCORE_MARGIN_CP = 100

/**
 * Background move prefill. Two triggers:
 *  - after the board changes: fill the only legal move, or a move the engine
 *    rates clearly best;
 *  - after the user settles on a source square: same, restricted to moves
 *    from that square.
 * Results are dropped unless the board and buffer are untouched since the
 * request, so autofill never overwrites newer keypad input.
 */
internal class AutofillCoordinator(
    private val scope: CoroutineScope,
    private val engine: StockfishEngine,
    private val state: GameState,
    private val log: GameLog,
    private val apply: (uci: String, reason: String, detail: String?) -> Unit,
) {
    private val job = JobSlot()

    fun cancel() = job.cancel()

    fun afterBoardChange() {
        val history = state.moveHistory
        val buffer = state.moveBuffer
        launchGuarded("post-move autofill failed") {
            engine.boot()
            val legalMoves = engine.legalMoves(history.moves)
            fill(history, buffer, legalMoves, forcedReason = "Only legal move")
        }
    }

    fun afterSourceSelected(buffer: MoveBuffer) {
        val fromSquare = buffer.fromSquareOrNull ?: return
        val history = state.moveHistory
        launchGuarded("source-move autofill failed") {
            delay(SOURCE_AUTOFILL_DELAY_MS)
            if (!state.isUnchangedSince(history, buffer)) return@launchGuarded
            engine.boot()
            val candidates = engine.legalMoves(history.moves)
                .filter { it.length >= 4 && it.startsWith(fromSquare) }
                .toSet()
            fill(history, buffer, candidates, forcedReason = "Only move from selected piece")
        }
    }

    private suspend fun fill(
        history: MoveHistory,
        buffer: MoveBuffer,
        candidates: Set<String>,
        forcedReason: String,
    ) {
        val forced = MoveAutofill.onlyLegalMove(candidates)
        if (forced != null) {
            if (state.isUnchangedSince(history, buffer)) apply(forced, forcedReason, null)
            return
        }
        if (candidates.size < 2) return
        val suggestion = MoveAutofill.clearBestScoredMove(
            scoredMoves = engine.scoredMoves(
                uciMoves = history.moves,
                candidates = candidates,
                movetimeMs = AUTOFILL_MOVETIME_MS,
            ),
            minimumScoreGapCp = AUTOFILL_SCORE_MARGIN_CP,
        ) ?: return
        if (state.isUnchangedSince(history, buffer)) {
            apply(suggestion.uci, "Suggestion", "gap ${suggestion.scoreGapCp} cp")
        }
    }

    private fun launchGuarded(failure: String, block: suspend () -> Unit) {
        job.launch(scope) {
            try {
                block()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                log.e(failure, t)
            }
        }
    }
}
