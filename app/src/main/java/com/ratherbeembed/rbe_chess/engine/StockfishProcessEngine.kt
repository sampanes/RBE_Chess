package com.ratherbeembed.rbe_chess.engine

import android.content.Context
import android.util.Log
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

    override suspend fun bestMove(uciMoves: List<String>, movetimeMs: Long): String {
        check(booted) { "engine not booted; call boot() first" }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val movesSuffix =
                    if (uciMoves.isEmpty()) "" else " moves " + uciMoves.joinToString(" ")
                send("position startpos$movesSuffix")
                send("go movetime $movetimeMs")
                val line = withTimeout(movetimeMs + 5_000L) {
                    readUntilStartsWith("bestmove ")
                }
                val parts = line.split(' ')
                require(parts.size >= 2 && parts[0] == "bestmove") {
                    "malformed bestmove line: '$line'"
                }
                parts[1]
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
        val r = reader ?: error("reader is null")
        while (true) {
            val line = r.readLine() ?: error("engine stdout closed before '$token'")
            Log.d(TAG, "<< $line")
            if (line.trim() == token) return line
        }
    }

    private fun readUntilStartsWith(prefix: String): String {
        val r = reader ?: error("reader is null")
        while (true) {
            val line = r.readLine() ?: error("engine stdout closed before '$prefix...'")
            Log.d(TAG, "<< $line")
            if (line.startsWith(prefix)) return line
        }
    }

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
