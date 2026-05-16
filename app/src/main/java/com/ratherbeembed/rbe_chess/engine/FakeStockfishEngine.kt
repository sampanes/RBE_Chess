package com.ratherbeembed.rbe_chess.engine

/**
 * In-memory stand-in for [StockfishProcessEngine]. Lets the rest of the
 * app — and JVM unit tests — exercise the engine seam without spawning
 * a real Stockfish process. Returns canned bestmoves in the order
 * supplied; falls back to "e2e4" once the script is exhausted.
 */
class FakeStockfishEngine(
    private val script: List<BestMoveResult> =
        listOf(BestMoveResult.Move("e2e4"), BestMoveResult.Move("g1f3")),
    private val legalMovesByHistory: Map<List<String>, Set<String>> = emptyMap(),
    private val defaultLegalMoves: Set<String> = setOf("e2e4", "d2d4", "g1f3"),
) : StockfishEngine {

    private var booted = false
    private var idx = 0

    override suspend fun boot() {
        booted = true
    }

    override suspend fun bestMove(uciMoves: List<String>, movetimeMs: Long): BestMoveResult {
        check(booted) { "engine not booted" }
        val move = script.getOrElse(idx) { FALLBACK }
        idx += 1
        return move
    }

    override suspend fun legalMoves(uciMoves: List<String>): Set<String> {
        check(booted) { "engine not booted" }
        return legalMovesByHistory[uciMoves] ?: defaultLegalMoves
    }

    override fun shutdown() {
        booted = false
    }

    companion object {
        private val FALLBACK = BestMoveResult.Move("e2e4")
    }
}
