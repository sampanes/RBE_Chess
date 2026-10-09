package com.ratherbeembed.rbe_chess.narrator

import com.ratherbeembed.rbe_chess.chess.ChessSide.BLACK
import com.ratherbeembed.rbe_chess.chess.ChessSide.WHITE
import com.ratherbeembed.rbe_chess.chess.PieceType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden output from narrative-sidekick/examples/utterance-catalog.md and
 * session-play.md, row for row. If one of these changes, the catalog
 * changes with it.
 */
class NarratorGoldenTest {

    private val narrator = Narrator()

    private fun assertSays(expected: String, event: NarrationEvent, level: Int) =
        assertEquals(expected, narrator.narrate(event, level))

    private fun best() = QualityFacts(cpLoss = 0, isBest = true)
    private fun loss(cp: Int) = QualityFacts(cpLoss = cp)

    @Test
    fun entry() {
        assertSays("from e two", EntryEvent(EntryField.FROM, "e2"), 0)
        assertSays("to e four", EntryEvent(EntryField.TO, "e4"), 0)
        assertSays("to c six", EntryEvent(EntryField.TO, "c6"), 0)
    }

    @Test
    fun `confirm, self moves`() {
        val rows = listOf(
            Triple(MoveEvent(WHITE, "e4", quality = best()), "white e four.", "white pawn e four. best move."),
            Triple(MoveEvent(BLACK, "Nf3", quality = loss(35)), "black knight f three.", "black knight f three. fine."),
            Triple(
                MoveEvent(WHITE, "Bb5", quality = loss(90)),
                "white bishop b five, inaccuracy.",
                "white bishop b five. inaccuracy, lost about half a pawn.",
            ),
            Triple(
                MoveEvent(WHITE, "Qxa7", quality = loss(410)),
                "white queen takes a seven, blunder.",
                "white queen takes a seven. blunder, lost about 4 pawns.",
            ),
            Triple(MoveEvent(WHITE, "O-O", quality = loss(30)), "white castles kingside.", "white castles kingside. fine."),
            Triple(
                MoveEvent(WHITE, "e8=Q#", quality = best()),
                "white e eight promotes to queen, checkmate.",
                "white pawn e eight promotes to queen, checkmate. best move.",
            ),
            Triple(
                MoveEvent(WHITE, "Nbd2", quality = loss(30)),
                "white knight from b to d two.",
                "white knight from b to d two. fine.",
            ),
        )
        for ((event, l1, l2) in rows) {
            assertSays(l1, event, 1)
            assertSays(l2, event, 2)
        }
    }

    @Test
    fun `confirm, opponent moves are quieter at L1`() {
        val rows = listOf(
            Triple(
                MoveEvent(BLACK, "Bxc6+", self = false, quality = best()),
                "black bishop takes c six, check.",
                "black bishop takes c six, check. best move.",
            ),
            Triple(
                MoveEvent(BLACK, "Bb5", self = false, quality = loss(90)),
                "black bishop b five.",
                "black bishop b five. inaccuracy, lost about half a pawn.",
            ),
            Triple(
                MoveEvent(BLACK, "Nc3", self = false, quality = loss(160)),
                "black knight c three.",
                "black knight c three. mistake, lost about 1.5 pawns.",
            ),
            Triple(
                MoveEvent(BLACK, "Qxa7", self = false, quality = loss(410)),
                "black queen takes a seven, blunder.",
                "black queen takes a seven. blunder, lost about 4 pawns.",
            ),
        )
        for ((event, l1, l2) in rows) {
            assertSays(l1, event, 1)
            assertSays(l2, event, 2)
        }
    }

    @Test
    fun `engine ladder`() {
        val e4 = EngineEvent(
            WHITE, "e4", Eval(cp = 30),
            line = listOf("e5", "Nf3"),
            alt = Alternative("c4", Eval(cp = 20)),
        )
        assertSays("engine: e four.", e4, 1)
        assertSays("engine: e four, plus 0.3.", e4, 2)
        assertSays("engine: e four, plus 0.3. then e five, then knight f three. or c four, plus 0.2.", e4, 3)

        val c5 = EngineEvent(BLACK, "c5", Eval(cp = -15), line = listOf("Nf3"))
        assertSays("engine: c five.", c5, 1)
        assertSays("engine: c five, minus 0.2.", c5, 2)
        assertSays("engine: c five, minus 0.2. then knight f three.", c5, 3)

        val mate = EngineEvent(WHITE, "Qh5", Eval(mate = 3), line = listOf("g6", "Qxg6"))
        assertSays("engine: queen h five.", mate, 1)
        assertSays("engine: queen h five, mate in three.", mate, 2)
        assertSays("engine: queen h five, mate in three. then g six, then queen takes g six.", mate, 3)

        val wdl = EngineEvent(WHITE, "Rd8", Eval(cp = 720), wdl = Wdl(850, 130, 20))
        assertSays("engine: rook d eight.", wdl, 1)
        assertSays("engine: rook d eight, plus 7.2.", wdl, 2)
        assertSays("engine: rook d eight, plus 7.2. winning four times out of five.", wdl, 3)
    }

    @Test
    fun `confirm with motifs`() {
        val fork = MoveEvent(
            WHITE, "Ne7", quality = best(),
            motifs = listOf(Motif(MotifKind.FORK, listOf(PieceType.KING, PieceType.ROOK))),
        )
        assertSays("white knight e seven, forking king and rook.", fork, 1)
        assertSays("white knight e seven, forking king and rook. best move.", fork, 2)

        val pin = MoveEvent(
            BLACK, "Bb5", self = false, quality = loss(30),
            motifs = listOf(Motif(MotifKind.PIN, listOf(PieceType.KNIGHT))),
        )
        assertSays("black bishop b five, pinning the knight.", pin, 1)
        assertSays("black bishop b five, pinning the knight. fine.", pin, 2)

        val trade = MoveEvent(
            WHITE, "Rxd5",
            motifs = listOf(Motif(MotifKind.TRADE, listOf(PieceType.ROOK), material = 0)),
        )
        assertSays("white rook takes d five.", trade, 1)
        assertSays("white rook takes d five. trade, even.", trade, 2)

        val sacrifice = MoveEvent(
            WHITE, "Qxh7", quality = loss(900),
            motifs = listOf(Motif(MotifKind.SACRIFICE, confident = false)),
        )
        assertSays("white queen takes h seven, blunder.", sacrifice, 1)
        assertSays("white queen takes h seven. blunder, lost about 9 pawns.", sacrifice, 2)
    }

    @Test
    fun system() {
        assertSays("game over.", SystemEvent(SystemAction.ENDGAME), 1)
        assertSays("game over, white wins.", SystemEvent(SystemAction.ENDGAME, result = GameResult.WHITE), 1)
        assertSays("new game.", SystemEvent(SystemAction.RESTART), 1)
        assertSays("undo.", SystemEvent(SystemAction.UNDO), 1)
        assertSays(
            "took back white knight f three.",
            SystemEvent(SystemAction.UNDO, reverted = RevertedMove(WHITE, "Nf3")),
            1,
        )
        assertSays(
            "white to move, plus 0.3, move 14.",
            SystemEvent(SystemAction.STATUS, sideToMove = WHITE, eval = Eval(cp = 30), moveNumber = 14),
            1,
        )
    }

    @Test
    fun `session play transcript`() {
        assertSays("from e two", EntryEvent(EntryField.FROM, "e2"), 0)
        assertSays("to e four", EntryEvent(EntryField.TO, "e4"), 0)
        val blackE4 = MoveEvent(BLACK, "e4", self = false, quality = loss(30))
        val engineE5 = EngineEvent(WHITE, "e5", Eval(cp = 0), line = listOf("Nf3", "Nc6"))
        assertSays("black e four.", blackE4, 1)
        assertSays("engine: e five.", engineE5, 1)
        assertSays("black pawn e four. fine.", blackE4, 2)
        assertSays("engine: e five, level.", engineE5, 2)
        assertSays("black pawn e four. fine.", blackE4, 3)
        assertSays("engine: e five, level. then knight f three, then knight c six.", engineE5, 3)
    }
}
