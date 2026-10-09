package com.ratherbeembed.rbe_chess.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionTest {

    private fun perft(position: Position, depth: Int): Long {
        if (depth == 0) return 1
        val moves = position.legalMoves()
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(position.play(it), depth - 1) }
    }

    private fun fen(value: String): Position = requireNotNull(Position.fromFen(value)) { value }

    // Reference counts: chessprogramming.org "Perft Results".

    @Test
    fun `perft from the start position`() {
        assertEquals(20L, perft(Position.START, 1))
        assertEquals(400L, perft(Position.START, 2))
        assertEquals(8_902L, perft(Position.START, 3))
        assertEquals(197_281L, perft(Position.START, 4))
    }

    @Test
    fun `perft kiwipete covers castling, en passant and promotion`() {
        val kiwipete = fen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48L, perft(kiwipete, 1))
        assertEquals(2_039L, perft(kiwipete, 2))
        assertEquals(97_862L, perft(kiwipete, 3))
    }

    @Test
    fun `perft position 3 covers en passant discovered checks`() {
        val position = fen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14L, perft(position, 1))
        assertEquals(191L, perft(position, 2))
        assertEquals(2_812L, perft(position, 3))
        assertEquals(43_238L, perft(position, 4))
    }

    @Test
    fun `perft position 4 covers promotions with check`() {
        val position = fen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(6L, perft(position, 1))
        assertEquals(264L, perft(position, 2))
        assertEquals(9_467L, perft(position, 3))
    }

    @Test
    fun `perft position 5`() {
        val position = fen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(44L, perft(position, 1))
        assertEquals(1_486L, perft(position, 2))
        assertEquals(62_379L, perft(position, 3))
    }

    @Test
    fun `perft position 6`() {
        val position = fen("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10")
        assertEquals(46L, perft(position, 1))
        assertEquals(2_079L, perft(position, 2))
        assertEquals(89_890L, perft(position, 3))
    }

    @Test
    fun `fen round trips`() {
        val text = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(text, fen(text).toFen())
        assertEquals(Position.START_FEN, Position.START.toFen())
    }

    @Test
    fun `history replay matches the projected board and fen`() {
        val history = MoveHistory(listOf("e2e4", "c7c5", "g1f3", "d7d6", "e1e2"))
        val position = requireNotNull(Position.fromHistory(history))
        assertEquals(
            "rnbqkbnr/pp2pppp/3p4/2p5/4P3/5N2/PPPPKPPP/RNBQ1B1R b kq - 1 3",
            position.toFen(),
        )
        val projected = BoardProjector.fromHistory(history).pieces
        assertEquals(projected, position.occupied.toMap())
    }

    @Test
    fun `illegal move in history yields null`() {
        assertNull(Position.fromHistory(MoveHistory(listOf("e2e5"))))
        assertNull(Position.START.playUci("e1e2"))
        assertNull(Position.START.playUci("zz"))
    }

    @Test
    fun `fools mate is checkmate and a bare stalemate is stalemate`() {
        val mated = requireNotNull(
            Position.fromHistory(MoveHistory(listOf("f2f3", "e7e5", "g2g4", "d8h4"))),
        )
        assertTrue(mated.inCheck())
        assertTrue(mated.isCheckmate())
        assertFalse(mated.isStalemate())

        val stalemate = fen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertFalse(stalemate.inCheck())
        assertTrue(stalemate.isStalemate())
    }

    @Test
    fun `attackers and attacksFrom see through nothing and stop at blockers`() {
        val position = fen("4k3/8/8/3r4/8/8/3R4/4K3 w - - 0 1")
        val d5 = BoardSquare.fromUci("d5")!!
        val d2 = BoardSquare.fromUci("d2")!!
        assertEquals(listOf(d2), position.attackers(d5, ChessSide.WHITE))
        val fromD2 = position.attacksFrom(d2).map { it.name }.toSet()
        assertTrue("d5" in fromD2)
        assertFalse("d6" in fromD2)
        assertTrue("d1" in fromD2)
        assertFalse("e1" in fromD2)
        assertNotNull(position.kingSquare(ChessSide.BLACK))
    }
}
