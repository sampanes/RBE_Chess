package com.ratherbeembed.rbe_chess.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeStockfishEngineTest {

    @Test
    fun `bestMove before boot throws`() {
        val engine = FakeStockfishEngine()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { engine.bestMove(emptyList(), 100) }
        }
    }

    @Test
    fun `boot then bestMove returns scripted moves in order`() = runBlocking {
        val engine = FakeStockfishEngine(
            script = listOf(
                BestMoveResult.Move("e2e4"),
                BestMoveResult.Move("g1f3"),
                BestMoveResult.Move("f1c4"),
            ),
        )
        engine.boot()
        assertEquals(BestMoveResult.Move("e2e4"), engine.bestMove(emptyList(), 100))
        assertEquals(BestMoveResult.Move("g1f3"), engine.bestMove(listOf("e2e4", "e7e5"), 100))
        assertEquals(
            BestMoveResult.Move("f1c4"),
            engine.bestMove(listOf("e2e4", "e7e5", "g1f3", "b8c6"), 100),
        )
    }

    @Test
    fun `bestMove past script falls back to e2e4`() = runBlocking {
        val engine = FakeStockfishEngine(script = listOf(BestMoveResult.Move("a2a3")))
        engine.boot()
        assertEquals(BestMoveResult.Move("a2a3"), engine.bestMove(emptyList(), 100))
        assertEquals(BestMoveResult.Move("e2e4"), engine.bestMove(emptyList(), 100))
    }

    @Test
    fun `bestMove can return terminal result`() = runBlocking {
        val engine = FakeStockfishEngine(
            script = listOf(BestMoveResult.Terminal(TerminalState.CHECKMATE)),
        )

        engine.boot()

        assertEquals(
            BestMoveResult.Terminal(TerminalState.CHECKMATE),
            engine.bestMove(listOf("f2f3", "e7e5", "g2g4", "d8h4"), 100),
        )
    }

    @Test
    fun `legalMoves returns scripted moves for exact history`() = runBlocking {
        val history = listOf("e2e4", "d7d5")
        val engine = FakeStockfishEngine(
            legalMovesByHistory = mapOf(history to setOf("e4d5", "g1f3")),
        )

        engine.boot()

        assertEquals(setOf("e4d5", "g1f3"), engine.legalMoves(history))
    }

    @Test
    fun `legalMoves before boot throws`() {
        val engine = FakeStockfishEngine()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { engine.legalMoves(emptyList()) }
        }
    }

    @Test
    fun `isSideToMoveInCheck returns scripted check state`() = runBlocking {
        val checkedHistory = listOf("e2e4", "e7e5", "d1h5")
        val engine = FakeStockfishEngine(
            checkByHistory = mapOf(checkedHistory to true),
        )

        engine.boot()

        assertTrue(engine.isSideToMoveInCheck(checkedHistory))
        assertFalse(engine.isSideToMoveInCheck(emptyList()))
    }

    @Test
    fun `isSideToMoveInCheck before boot throws`() {
        val engine = FakeStockfishEngine()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { engine.isSideToMoveInCheck(emptyList()) }
        }
    }

    @Test
    fun `scoredMoves filters and sorts candidate scores`() = runBlocking {
        val history = listOf("e2e4", "e7e5")
        val engine = FakeStockfishEngine(
            scoredMovesByHistory = mapOf(
                history to listOf(
                    ScoredMove("g1f3", EngineScore.Centipawns(20)),
                    ScoredMove("f1c4", EngineScore.Centipawns(80)),
                    ScoredMove("d2d4", EngineScore.Centipawns(40)),
                ),
            ),
        )

        engine.boot()

        assertEquals(
            listOf(
                ScoredMove("f1c4", EngineScore.Centipawns(80)),
                ScoredMove("g1f3", EngineScore.Centipawns(20)),
            ),
            engine.scoredMoves(
                uciMoves = history,
                candidates = setOf("g1f3", "f1c4"),
                movetimeMs = 100,
            ),
        )
    }

    @Test
    fun `scoredMoves before boot throws`() {
        val engine = FakeStockfishEngine()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                engine.scoredMoves(
                    uciMoves = emptyList(),
                    candidates = setOf("e2e4"),
                    movetimeMs = 100,
                )
            }
        }
    }

    @Test
    fun `shutdown then bestMove throws`() {
        val engine = FakeStockfishEngine()
        runBlocking { engine.boot() }
        engine.shutdown()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { engine.bestMove(emptyList(), 100) }
        }
    }

    @Test
    fun `boot is idempotent`() = runBlocking {
        val engine = FakeStockfishEngine()
        engine.boot()
        engine.boot()
        assertEquals(BestMoveResult.Move("e2e4"), engine.bestMove(emptyList(), 100))
    }
}
