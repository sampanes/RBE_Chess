package com.ratherbeembed.rbe_chess.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SanTest {

    private fun san(fen: String, uci: String): String? =
        San.fromUci(requireNotNull(Position.fromFen(fen)), uci)

    @Test
    fun `opening moves`() {
        assertEquals(
            listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6", "O-O"),
            San.forHistory(
                MoveHistory(
                    listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5c6", "d7c6", "e1g1"),
                ),
            ),
        )
    }

    @Test
    fun `check and mate suffixes`() {
        assertEquals(
            listOf("f3", "e5", "g4", "Qh4#"),
            San.forHistory(MoveHistory(listOf("f2f3", "e7e5", "g2g4", "d8h4"))),
        )
        assertEquals("Bb5+", san("4k3/8/8/8/8/8/8/4KB2 w - - 0 1", "f1b5"))
    }

    @Test
    fun `queenside castling`() {
        assertEquals("O-O-O", san("r3k3/8/8/8/8/8/8/4K3 b q - 0 1", "e8c8"))
    }

    @Test
    fun `file disambiguation`() {
        // Knights on b1 and f3 can both reach d2.
        assertEquals("Nbd2", san("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1", "b1d2"))
    }

    @Test
    fun `rank disambiguation`() {
        // Rooks on e1 and e5 can both reach e3.
        assertEquals("R1e3", san("k7/8/8/4R3/8/8/8/4R2K w - - 0 1", "e1e3"))
    }

    @Test
    fun `full square disambiguation`() {
        // Queens on e4, h4 and h1 all reach e1; h4 shares a file with h1
        // and a rank with e4, so only the full square is unambiguous.
        assertEquals("Qh4e1", san("8/2k5/8/8/4Q2Q/8/8/K6Q w - - 0 1", "h4e1"))
    }

    @Test
    fun `pinned rival does not force disambiguation`() {
        // The c3 knight is pinned by the a5 bishop, so it is not a legal
        // rival for e2 and the g1 knight's move needs no disambiguator.
        assertEquals("Ne2", san("4k3/8/8/b7/8/2N5/8/4K1N1 w - - 0 1", "g1e2"))
    }

    @Test
    fun `promotion and en passant`() {
        assertEquals("e8=Q+", san("3k4/4P3/8/8/8/8/8/4K3 w - - 0 1", "e7e8q"))
        assertEquals("exd8=N", san("3q2k1/4P3/8/8/8/8/8/4K3 w - - 0 1", "e7d8n"))
        assertEquals("exd6", san("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", "e5d6"))
    }

    @Test
    fun `illegal move has no san`() {
        assertNull(san(Position.START_FEN, "e2e5"))
        assertNull(San.forHistory(MoveHistory(listOf("e2e4", "e2e4"))))
    }
}
