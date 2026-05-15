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
     * Returns the bestmove UCI string (e.g. `"e2e4"` or `"e7e8q"`).
     * Throws if the engine is not booted or the exchange times out.
     */
    suspend fun bestMove(uciMoves: List<String>, movetimeMs: Long): String

    /** Shut down the engine process. Safe to call repeatedly. */
    fun shutdown()
}
