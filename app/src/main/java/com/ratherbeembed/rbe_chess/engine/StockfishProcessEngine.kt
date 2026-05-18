package com.ratherbeembed.rbe_chess.engine

import android.content.Context
import android.util.Log
import com.ratherbeembed.rbe_chess.chess.ChessSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

private const val TAG = "RBE_SF"
private const val BIN_NAME = "libstockfish.so"

/**
 * Real engine: spawns the Stockfish binary that ships in
 * `app/src/main/jniLibs/arm64-v8a/libstockfish.so` and talks UCI to it
 * over the child process's stdin/stdout.
 *
 * Concurrency: a [Mutex] serializes boot and bestMove so the UCI
 * protocol stays in lock-step. Step 3 only needs one outstanding
 * exchange at a time. stderr is merged into stdout (via
 * `redirectErrorStream`) and any non-UCI lines are skipped during
 * `readUntil` — Stockfish prints NNUE-load info etc. before the first
 * `uciok`.
 *
 * Timeouts are coroutine-level via [withTimeout]; the underlying
 * blocking `readLine()` may not honor cancellation immediately, but
 * Stockfish always responds within `movetime` + a few hundred ms in
 * practice, so the PoC accepts that limitation.
 */
class StockfishProcessEngine(context: Context) : StockfishEngine {

    private val binaryPath: String =
        File(context.applicationInfo.nativeLibraryDir, BIN_NAME).absolutePath

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val lock = Mutex()
    @Volatile private var booted = false

    override suspend fun boot() {
        if (booted) return
        lock.withLock {
            if (booted) return@withLock
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Spawning Stockfish from $binaryPath")
                val p = ProcessBuilder(binaryPath)
                    .redirectErrorStream(true)
                    .start()
                process = p
                writer = BufferedWriter(OutputStreamWriter(p.outputStream))
                reader = BufferedReader(InputStreamReader(p.inputStream))
                send("uci")
                readUntilEquals("uciok")
                send("isready")
                readUntilEquals("readyok")
                booted = true
                Log.d(TAG, "Stockfish ready (uciok + readyok)")
            }
        }
    }

    override suspend fun bestMove(uciMoves: List<String>, movetimeMs: Long): BestMoveResult {
        check(booted) { "engine not booted; call boot() first" }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val movesSuffix =
                    if (uciMoves.isEmpty()) "" else " moves " + uciMoves.joinToString(" ")
                send("position startpos$movesSuffix")
                send("go movetime $movetimeMs")
                var sawMateScore = false
                val move: String = withTimeout(movetimeMs + 5_000L) {
                    while (true) {
                        val line = readLineLogged()
                        if (UciBestMoveParser.hasMateScore(line)) {
                            sawMateScore = true
                        }
                        val parsed = UciBestMoveParser.moveFromLine(line)
                        if (parsed != null) return@withTimeout parsed
                    }
                    error("unreachable")
                }
                if (move == "(none)") {
                    val state =
                        if (sawMateScore) TerminalState.CHECKMATE else TerminalState.STALEMATE
                    BestMoveResult.Terminal(state)
                } else {
                    BestMoveResult.Move(move)
                }
            }
        }
    }

    override suspend fun analyzePosition(
        uciMoves: List<String>,
        movetimeMs: Long,
    ): AnalysisSummary? {
        check(booted) { "engine not booted; call boot() first" }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val movesSuffix =
                    if (uciMoves.isEmpty()) "" else " moves " + uciMoves.joinToString(" ")
                send("position startpos$movesSuffix")
                send("go movetime $movetimeMs")
                var latest: UciAnalysisInfo? = null
                withTimeout(movetimeMs + 5_000L) {
                    while (true) {
                        val line = readLineLogged()
                        UciAnalysisParser.analysisFromInfoLine(line)
                            ?.let { latest = it }
                        if (UciBestMoveParser.moveFromLine(line) != null) break
                    }
                }
                latest?.let {
                    AnalysisSummary.fromRaw(
                        info = it,
                        sideToMove = sideToMove(uciMoves),
                    )
                }
            }
        }
    }

    override suspend fun legalMoves(uciMoves: List<String>): Set<String> {
        check(booted) { "engine not booted; call boot() first" }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val movesSuffix =
                    if (uciMoves.isEmpty()) "" else " moves " + uciMoves.joinToString(" ")
                val moves = mutableSetOf<String>()
                send("position startpos$movesSuffix")
                send("go perft 1")
                withTimeout(5_000L) {
                    while (true) {
                        val line = readLineLogged()
                        UciPerftParser.moveFromLine(line)?.let { moves.add(it) }
                        if (line.startsWith("Nodes searched:")) break
                    }
                }
                moves
            }
        }
    }

    override suspend fun isSideToMoveInCheck(uciMoves: List<String>): Boolean {
        check(booted) { "engine not booted; call boot() first" }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val movesSuffix =
                    if (uciMoves.isEmpty()) "" else " moves " + uciMoves.joinToString(" ")
                send("position startpos$movesSuffix")
                send("d")
                withTimeout(5_000L) {
                    while (true) {
                        val line = readLineLogged()
                        UciCheckersParser.hasCheckers(line)?.let { return@withTimeout it }
                    }
                    error("unreachable")
                }
            }
        }
    }

    override suspend fun scoredMoves(
        uciMoves: List<String>,
        candidates: Set<String>,
        movetimeMs: Long,
    ): List<ScoredMove> {
        check(booted) { "engine not booted; call boot() first" }
        if (candidates.isEmpty()) return emptyList()
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val movesSuffix =
                    if (uciMoves.isEmpty()) "" else " moves " + uciMoves.joinToString(" ")
                val multiPv = minOf(candidates.size, 2)
                val latestByMultiPv = linkedMapOf<Int, ScoredMove>()
                send("position startpos$movesSuffix")
                send("setoption name MultiPV value $multiPv")
                send("isready")
                readUntilEquals("readyok")
                try {
                    send(
                        "go movetime $movetimeMs searchmoves " +
                            candidates.sorted().joinToString(" "),
                    )
                    withTimeout(movetimeMs + 5_000L) {
                        while (true) {
                            val line = readLineLogged()
                            UciScoredMoveParser.scoredMoveFromInfoLine(line)
                                ?.takeIf { it.move.uci in candidates }
                                ?.let { latestByMultiPv[it.multipv] = it.move }
                            if (UciBestMoveParser.moveFromLine(line) != null) break
                        }
                    }
                    latestByMultiPv
                        .toSortedMap()
                        .values
                        .toList()
                } finally {
                    send("setoption name MultiPV value 1")
                    send("isready")
                    readUntilEquals("readyok")
                }
            }
        }
    }

    private fun send(cmd: String) {
        val w = writer ?: error("writer is null")
        Log.d(TAG, ">> $cmd")
        w.write(cmd)
        w.write("\n")
        w.flush()
    }

    private fun readUntilEquals(token: String): String {
        while (true) {
            val line = readLineLogged()
            if (line.trim() == token) return line
        }
    }

    private fun readLineLogged(): String {
        val r = reader ?: error("reader is null")
        val line = r.readLine() ?: error("engine stdout closed")
        Log.d(TAG, "<< $line")
        return line
    }

    private fun sideToMove(uciMoves: List<String>): ChessSide =
        if (uciMoves.size % 2 == 0) ChessSide.WHITE else ChessSide.BLACK

    override fun shutdown() {
        booted = false
        try { writer?.write("quit\n"); writer?.flush() } catch (_: Throwable) {}
        try { writer?.close() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        try { process?.destroy() } catch (_: Throwable) {}
        writer = null
        reader = null
        process = null
        Log.d(TAG, "Stockfish process shut down")
    }
}
