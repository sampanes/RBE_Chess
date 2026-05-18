package com.ratherbeembed.rbe_chess.engine

/**
 * Sole seam between the rest of the app and the chess engine. Per
 * AGENT_NOTES: the UI / speech / pocket layers must never touch
 * process management — they go through this interface.
 *
 * Step 3 only needs `boot` and `bestMove` to prove the round-trip;
 * later steps (engine settings, position threading, multi-PV) extend
 * this surface.
 */
interface StockfishEngine {

    /**
     * Bring the engine up: spawn the process, send `uci`, wait for
     * `uciok`, then send `isready` and wait for `readyok`. Idempotent
     * — calling twice is a no-op after the first success.
     */
    suspend fun boot()

    /**
     * Synchronous-ish UCI exchange for a single position:
     *   1. `position startpos moves <uciMoves...>`
     *   2. `go movetime <movetimeMs>`
     *   3. block until `bestmove <uci>` arrives
     *
     * Returns either a bestmove UCI string (e.g. `"e2e4"` or `"e7e8q"`)
     * or a terminal result when Stockfish replies `bestmove (none)`.
     * Throws if the engine is not booted or the exchange times out.
     */
    suspend fun bestMove(uciMoves: List<String>, movetimeMs: Long): BestMoveResult

    /**
     * Analyze the position reached by [uciMoves] without applying the returned
     * best move. Scores are normalized to White POV before they leave the
     * engine wrapper.
     */
    suspend fun analyzePosition(uciMoves: List<String>, movetimeMs: Long): AnalysisSummary?

    /**
     * Return legal UCI moves from the position reached by [uciMoves].
     * Used to reject keypad-entered moves before they mutate app history.
     */
    suspend fun legalMoves(uciMoves: List<String>): Set<String>

    /**
     * True when the side to move in the position reached by [uciMoves] is
     * currently in check. Used for ordinary, non-terminal check speech.
     */
    suspend fun isSideToMoveInCheck(uciMoves: List<String>): Boolean

    /**
     * Score the supplied legal candidate moves from the position reached by
     * [uciMoves], ordered best-first by Stockfish's MultiPV ranking.
     */
    suspend fun scoredMoves(
        uciMoves: List<String>,
        candidates: Set<String>,
        movetimeMs: Long,
    ): List<ScoredMove>

    /** Shut down the engine process. Safe to call repeatedly. */
    fun shutdown()
}
