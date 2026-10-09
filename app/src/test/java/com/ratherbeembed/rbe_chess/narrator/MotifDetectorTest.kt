package com.ratherbeembed.rbe_chess.narrator

import com.ratherbeembed.rbe_chess.chess.ChessMove
import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.chess.PieceType
import com.ratherbeembed.rbe_chess.chess.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MotifDetectorTest {

    private fun motifs(fen: String, uci: String): List<Motif> =
        MotifDetector.detect(requireNotNull(Position.fromFen(fen)), requireNotNull(ChessMove.fromUci(uci)))

    @Test
    fun `knight fork of king and rook`() {
        assertEquals(
            listOf(Motif(MotifKind.FORK, listOf(PieceType.KING, PieceType.ROOK))),
            motifs("2r3k1/8/8/3N4/8/8/8/4K3 w - - 0 1", "d5e7"),
        )
    }

    @Test
    fun `attacking two pawns is not a fork`() {
        assertEquals(
            emptyList<Motif>(),
            motifs("4k3/8/2p1p3/2p1p3/8/8/1N6/4K3 w - - 0 1", "b2d3").filter { it.kind == MotifKind.FORK },
        )
    }

    @Test
    fun `bishop pins knight to king`() {
        assertEquals(
            listOf(Motif(MotifKind.PIN, listOf(PieceType.KNIGHT))),
            motifs("4kb2/8/8/8/8/2N5/8/4K3 b - - 0 1", "f8b4"),
        )
    }

    @Test
    fun `a pin the pinned piece can break by capture is not announced`() {
        // The e2 rook may take the e5 rook along the pin line, and nothing
        // defends e5, so the honest fact is a hanging rook, not a pin.
        assertEquals(
            listOf(Motif(MotifKind.HANGING, listOf(PieceType.ROOK))),
            motifs("7k/8/8/r7/8/8/4R3/4K3 b - - 0 1", "a5e5"),
        )
    }

    @Test
    fun `an existing pin is not re-announced`() {
        // The c3 knight is already pinned by the b4 bishop; sliding the
        // bishop back to a5 keeps the same pin.
        assertEquals(
            emptyList<Motif>(),
            motifs("4k3/8/8/8/1b6/2N5/8/4K3 b - - 0 1", "b4a5"),
        )
    }

    @Test
    fun `queen walking into a pawn capture is hanging`() {
        assertEquals(
            listOf(Motif(MotifKind.HANGING, listOf(PieceType.QUEEN))),
            motifs("4k3/8/8/4p3/8/8/8/3QK3 w - - 0 1", "d1d4"),
        )
    }

    @Test
    fun `rook takes rook with recapture is an even trade`() {
        assertEquals(
            listOf(Motif(MotifKind.TRADE, listOf(PieceType.ROOK), material = 0)),
            motifs("3rk3/8/8/3r4/8/8/8/3RK3 w - - 0 1", "d1d5"),
        )
    }

    @Test
    fun `quiet opening move has no motifs`() {
        assertEquals(emptyList<Motif>(), MotifDetector.detect(Position.START, ChessMove.fromUci("e2e4")!!))
    }

    @Test
    fun `facts builder produces san, side and motifs from history`() {
        val history = MoveHistory(listOf("e2e4", "e7e5", "g1f3", "b8c6"))
        val event = requireNotNull(NarrationFacts.moveEvent(history, "f1b5", self = false))
        assertEquals(MoveEvent(ChessSide.WHITE, "Bb5", self = false), event)
        assertEquals("white bishop b five.", Narrator().narrate(event, 1))
    }

    @Test
    fun `facts builder converts the engine pv to san`() {
        val event = requireNotNull(
            NarrationFacts.engineEvent(
                history = MoveHistory(listOf("e2e4")),
                uci = "e7e5",
                eval = Eval(cp = 20),
                pv = listOf("e7e5", "g1f3", "b8c6", "zz99"),
                alt = "c7c5" to Eval(cp = 25),
            ),
        )
        assertEquals(listOf("Nf3", "Nc6"), event.line)
        assertEquals(
            "engine: e five, plus 0.2. then knight f three, then knight c six. or c five, plus 0.3.",
            Narrator().narrate(event, 3),
        )
    }

    @Test
    fun `facts builder names the reverted move and rejects illegal input`() {
        val reverted = NarrationFacts.revertedMove(MoveHistory(listOf("e2e4", "e7e5", "g1f3")))
        assertEquals(RevertedMove(ChessSide.WHITE, "Nf3"), reverted)
        assertNull(NarrationFacts.moveEvent(MoveHistory(listOf("e2e4")), "e2e4"))
        assertNull(NarrationFacts.revertedMove(MoveHistory.EMPTY))
    }
}
