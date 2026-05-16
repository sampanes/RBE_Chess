package com.ratherbeembed.rbe_chess.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        val engine = FakeStockfishEngine(script = listOf("e2e4", "g1f3", "f1c4"))
        engine.boot()
        assertEquals("e2e4", engine.bestMove(emptyList(), 100))
        assertEquals("g1f3", engine.bestMove(listOf("e2e4", "e7e5"), 100))
        assertEquals("f1c4", engine.bestMove(listOf("e2e4", "e7e5", "g1f3", "b8c6"), 100))
    }

    @Test
    fun `bestMove past script falls back to e2e4`() = runBlocking {
        val engine = FakeStockfishEngine(script = listOf("a2a3"))
        engine.boot()
        assertEquals("a2a3", engine.bestMove(emptyList(), 100))
        assertEquals("e2e4", engine.bestMove(emptyList(), 100))
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
        assertEquals("e2e4", engine.bestMove(emptyList(), 100))
    }
}
