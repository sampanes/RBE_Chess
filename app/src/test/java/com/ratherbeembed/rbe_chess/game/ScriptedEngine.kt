package com.ratherbeembed.rbe_chess.game

import com.ratherbeembed.rbe_chess.engine.AnalysisSummary
import com.ratherbeembed.rbe_chess.engine.BestMoveResult
import com.ratherbeembed.rbe_chess.engine.ScoredMove
import com.ratherbeembed.rbe_chess.engine.StockfishEngine
import kotlinx.coroutines.CompletableDeferred

/**
 * Fully scriptable engine for controller tests. Positions are keyed by the
 * UCI move list that reaches them. [replyGate], when set, holds bestMove()
 * open so a test can act while the engine is "thinking".
 */
class ScriptedEngine : StockfishEngine {
    val replies = ArrayDeque<BestMoveResult>()
    var replyGate: CompletableDeferred<Unit>? = null
    var bestMoveFailure: Throwable? = null
    var defaultLegalMoves: Set<String> = setOf("e2e4", "d2d4", "g1f3", "e7e5", "d7d5", "b8c6")
    val legalByHistory = mutableMapOf<List<String>, Set<String>>()
    val checkByHistory = mutableMapOf<List<String>, Boolean>()
    val scoredByHistory = mutableMapOf<List<String>, List<ScoredMove>>()
    val bestMoveRequests = mutableListOf<List<String>>()

    override suspend fun boot() = Unit

    override suspend fun bestMove(uciMoves: List<String>, movetimeMs: Long): BestMoveResult {
        bestMoveRequests += uciMoves
        replyGate?.await()
        bestMoveFailure?.let { throw it }
        return replies.removeFirstOrNull() ?: BestMoveResult.Move("a7a6")
    }

    override suspend fun analyzePosition(uciMoves: List<String>, movetimeMs: Long): AnalysisSummary? = null

    override suspend fun legalMoves(uciMoves: List<String>): Set<String> =
        legalByHistory[uciMoves] ?: defaultLegalMoves

    override suspend fun isSideToMoveInCheck(uciMoves: List<String>): Boolean =
        checkByHistory[uciMoves] ?: false

    override suspend fun scoredMoves(
        uciMoves: List<String>,
        candidates: Set<String>,
        movetimeMs: Long,
    ): List<ScoredMove> =
        (scoredByHistory[uciMoves] ?: emptyList()).filter { it.uci in candidates }

    override fun shutdown() = Unit
}
