package com.ratherbeembed.rbe_chess.game

import android.view.KeyEvent
import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.GameEndReason
import com.ratherbeembed.rbe_chess.chess.GameTextExport
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.AnalysisSummary
import com.ratherbeembed.rbe_chess.engine.BestMoveResult
import com.ratherbeembed.rbe_chess.engine.EngineScore
import com.ratherbeembed.rbe_chess.engine.ScoredMove
import com.ratherbeembed.rbe_chess.engine.TerminalState
import com.ratherbeembed.rbe_chess.input.ChessKey
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.speech.BestMoveSpeaker
import com.ratherbeembed.rbe_chess.speech.SpeechSink
import com.ratherbeembed.rbe_chess.ui.AppPhase
import com.ratherbeembed.rbe_chess.ui.FinishedGameUiState
import com.ratherbeembed.rbe_chess.ui.GameMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameControllerTest {

    private class RecordingSink : SpeechSink {
        val spoken = mutableListOf<String>()
        val queued = mutableListOf<String>()

        override fun speak(text: String) {
            spoken += text
        }

        override fun speakQueued(text: String) {
            queued += text
            spoken += text
        }
    }

    private class Harness(scope: TestScope) {
        val engine = ScriptedEngine()
        val sink = RecordingSink()
        val exports = mutableListOf<GameTextExport>()
        var exportFailure: Throwable? = null
        val controller = GameController(
            scope = scope.backgroundScope,
            engine = engine,
            speaker = BestMoveSpeaker(sink),
            saveExport = { export ->
                exportFailure?.let { throw it }
                exports += export
                "Download/RBE Chess/${export.fileName}"
            },
        )
        val state get() = controller.state
        val lastSpoken get() = sink.spoken.last()

        fun press(vararg keys: ChessKey) = keys.forEach(controller::onMiniKey)

        /** Load [uci] into the buffer as if cycled in, then commit with Thumb. */
        fun commit(uci: String) {
            state.moveBuffer = MoveBuffer.DEFAULT.copyFromEngine(uci)
            press(ChessKey.SPACE)
        }

        fun startAsBlack() {
            press(ChessKey.J, ChessKey.SPACE)
            sink.spoken.clear()
            sink.queued.clear()
        }
    }

    private fun gameTest(body: suspend TestScope.(Harness) -> Unit) = runTest {
        body(Harness(this))
    }

    // --- Start menu ----------------------------------------------------------

    @Test
    fun `cold start announces the start menu`() = gameTest { h ->
        h.controller.announceStartup(restored = false)

        assertEquals(listOf("Start menu. Play as white"), h.sink.spoken)
    }

    @Test
    fun `menu navigation wraps in both directions`() = gameTest { h ->
        h.press(ChessKey.J)
        assertEquals(AppPhase.StartMenu(1), h.state.phase)
        assertEquals("Play as black", h.lastSpoken)

        h.press(ChessKey.J)
        assertEquals(AppPhase.StartMenu(0), h.state.phase)

        h.press(ChessKey.F)
        assertEquals(AppPhase.StartMenu(1), h.state.phase)
    }

    @Test
    fun `play as black starts without asking the engine`() = gameTest { h ->
        h.press(ChessKey.J, ChessKey.SPACE)
        runCurrent()

        assertEquals(AppPhase.InGame, h.state.phase)
        assertEquals(ChessSide.BLACK, h.state.playerSide)
        assertEquals("Playing as black", h.lastSpoken)
        assertTrue(h.engine.bestMoveRequests.isEmpty())
    }

    @Test
    fun `play as white in auto advance appends the engine opening`() = gameTest { h ->
        h.engine.replies += BestMoveResult.Move("e2e4")

        h.press(ChessKey.SPACE)
        runCurrent()

        assertEquals(listOf("e2e4"), h.state.moveHistory.moves)
        assertEquals("Your White played E two to E four. Waiting for Opponent Black.", h.lastSpoken)
        assertEquals("Bootstrap: engine=e2e4 (1 plies)", h.state.engineStatus)
    }

    @Test
    fun `play as white in manual mode only prefills the opening`() = gameTest { h ->
        h.state.gameMode = GameMode.Manual
        h.engine.replies += BestMoveResult.Move("d2d4")

        h.press(ChessKey.SPACE)
        runCurrent()

        assertTrue(h.state.moveHistory.moves.isEmpty())
        assertEquals("d2d4", h.state.moveBuffer.toUciString())
        assertEquals("Suggestion for Your White: D two to D four.", h.lastSpoken)
    }

    // --- Committing moves ----------------------------------------------------

    @Test
    fun `legal typed move in auto advance appends typed move and engine reply`() = gameTest { h ->
        h.startAsBlack()
        h.engine.replies += BestMoveResult.Move("e7e5")

        h.commit("e2e4")
        runCurrent()

        assertEquals(listOf("e2e4", "e7e5"), h.state.moveHistory.moves)
        assertNull(h.state.pendingMove)
        assertEquals(listOf(listOf("e2e4")), h.engine.bestMoveRequests)
        assertTrue(h.sink.spoken.contains("Opponent White played E two to E four. Calculating Your Black reply"))
        assertTrue(h.sink.queued.contains("Your Black played E seven to E five. Waiting for Opponent White."))
        assertEquals("Last: opp=e2e4 -> engine=e7e5 (2 plies)", h.state.engineStatus)
    }

    @Test
    fun `illegal move keeps history and the typed buffer`() = gameTest { h ->
        h.startAsBlack()

        h.commit("e2e5")
        runCurrent()

        assertTrue(h.state.moveHistory.moves.isEmpty())
        assertEquals("e2e5", h.state.moveBuffer.toUciString())
        assertNull(h.state.pendingMove)
        assertEquals("Illegal move. Waiting for Opponent White.", h.lastSpoken)
        assertTrue(h.engine.bestMoveRequests.isEmpty())
    }

    @Test
    fun `manual mode appends only the typed move and prefills the suggestion`() = gameTest { h ->
        h.startAsBlack()
        h.press(ChessKey.TOGGLE_MANUAL)
        assertEquals(GameMode.Manual, h.state.gameMode)
        assertEquals("Manual mode on. Waiting for Opponent White.", h.lastSpoken)
        h.engine.replies += BestMoveResult.Move("e7e5")

        h.commit("e2e4")
        runCurrent()

        assertEquals(listOf("e2e4"), h.state.moveHistory.moves)
        assertEquals("e7e5", h.state.moveBuffer.toUciString())
        assertTrue(
            h.sink.spoken.contains(
                "Opponent White played E two to E four. " +
                    "Suggestion for Your Black: E seven to E five. Waiting for Your Black.",
            ),
        )
    }

    @Test
    fun `typed move that gives check is announced as check`() = gameTest { h ->
        h.startAsBlack()
        h.engine.checkByHistory[listOf("e2e4")] = true
        h.engine.replies += BestMoveResult.Move("e7e5")

        h.commit("e2e4")
        runCurrent()

        assertTrue(
            h.sink.spoken.contains("Opponent White played E two to E four. Check. Calculating Your Black reply"),
        )
    }

    @Test
    fun `cycler input is ignored while the engine is thinking`() = gameTest { h ->
        h.startAsBlack()
        val gate = CompletableDeferred<Unit>()
        h.engine.replyGate = gate
        h.engine.replies += BestMoveResult.Move("e7e5")

        h.commit("e2e4")
        runCurrent()
        assertEquals("e2e4", h.state.pendingMove)

        h.press(ChessKey.D, ChessKey.J)
        assertEquals(MoveBuffer.DEFAULT, h.state.moveBuffer)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("e2e4", "e7e5"), h.state.moveHistory.moves)
    }

    @Test
    fun `undo during engine think cancels the reply silently`() = gameTest { h ->
        h.startAsBlack()
        val gate = CompletableDeferred<Unit>()
        h.engine.replyGate = gate

        h.commit("e2e4")
        runCurrent()
        h.press(ChessKey.UNDO)
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertTrue(h.state.moveHistory.moves.isEmpty())
        assertNull(h.state.pendingMove)
        assertEquals("Undo: history=0 plies", h.state.engineStatus)
    }

    @Test
    fun `a cancelled engine turn cannot clobber the next commit`() = gameTest { h ->
        h.startAsBlack()
        h.engine.replyGate = CompletableDeferred()

        h.commit("e2e4")
        runCurrent()
        h.press(ChessKey.UNDO)
        // New commit before the cancelled job has had a chance to unwind.
        h.commit("d2d4")
        runCurrent()

        assertEquals("d2d4", h.state.pendingMove)
        assertEquals("Engine: thinking on d2d4...", h.state.engineStatus)
    }

    @Test
    fun `undo after a reply drops the last pair of plies`() = gameTest { h ->
        h.startAsBlack()
        h.engine.replies += BestMoveResult.Move("e7e5")
        h.commit("e2e4")
        runCurrent()

        h.press(ChessKey.UNDO)

        assertTrue(h.state.moveHistory.moves.isEmpty())
        assertEquals("Undid last move. Waiting for Opponent White.", h.lastSpoken)
    }

    @Test
    fun `engine failure leaves history untouched and shows the error`() = gameTest { h ->
        h.startAsBlack()
        h.engine.bestMoveFailure = IllegalStateException("boom")

        h.commit("e2e4")
        runCurrent()

        assertTrue(h.state.moveHistory.moves.isEmpty())
        assertNull(h.state.pendingMove)
        assertEquals("Engine error after e2e4: boom", h.state.engineStatus)
    }

    // --- Promotion -----------------------------------------------------------

    @Test
    fun `promotion pauses for a piece pick then commits the chosen piece`() = gameTest { h ->
        h.startAsBlack()
        h.engine.legalByHistory[emptyList()] = setOf("e7e8q", "e7e8r", "e7e8b", "e7e8n", "a2a3")
        h.engine.replies += BestMoveResult.Move("a7a6")

        h.commit("e7e8")
        runCurrent()

        assertNotNull(h.state.promotionPick)
        assertTrue(h.state.moveHistory.moves.isEmpty())
        assertTrue(h.lastSpoken.startsWith("Promotion: E seven to E eight."))

        h.press(ChessKey.D) // Pinky = knight
        runCurrent()

        assertNull(h.state.promotionPick)
        assertEquals(listOf("e7e8n", "a7a6"), h.state.moveHistory.moves)
    }

    @Test
    fun `repeat during promotion repeats the piece prompt`() = gameTest { h ->
        h.startAsBlack()
        h.engine.legalByHistory[emptyList()] = setOf("e7e8q", "e7e8n")
        h.commit("e7e8")
        runCurrent()
        h.sink.spoken.clear()

        h.press(ChessKey.REPEAT_LAST)

        assertTrue(h.lastSpoken.startsWith("Promotion: E seven to E eight."))
    }

    // --- Game end and export -------------------------------------------------

    @Test
    fun `typed checkmate opens the finished menu and save exports`() = gameTest { h ->
        h.startAsBlack()
        h.engine.legalByHistory[listOf("e2e4")] = emptySet()
        h.engine.checkByHistory[listOf("e2e4")] = true

        h.commit("e2e4")
        runCurrent()

        assertEquals(TerminalState.CHECKMATE, h.state.terminalState)
        assertEquals(FinishedGameUiState(GameEndReason.CHECKMATE), h.state.finishedGame)
        assertTrue(h.sink.spoken.contains("Opponent White played E two to E four. Checkmate."))
        assertEquals("Save PGN/FEN", h.sink.queued.last())
        assertTrue(h.engine.bestMoveRequests.isEmpty())

        h.press(ChessKey.SPACE)

        assertEquals(1, h.exports.size)
        val path = h.state.finishedGame?.lastExportPath
        assertNotNull(path)
        assertEquals("Saved game export to $path", h.lastSpoken)
    }

    @Test
    fun `engine reporting no move after typed move ends the game`() = gameTest { h ->
        h.startAsBlack()
        h.engine.replies += BestMoveResult.Terminal(TerminalState.STALEMATE)

        h.commit("e2e4")
        runCurrent()

        assertEquals(listOf("e2e4"), h.state.moveHistory.moves)
        assertEquals(GameEndReason.STALEMATE, h.state.finishedGame?.reason)
        assertEquals("Stalemate after e2e4 (1 plies)", h.state.engineStatus)
    }

    @Test
    fun `engine reply that mates is announced and finishes the game`() = gameTest { h ->
        h.startAsBlack()
        h.engine.replies += BestMoveResult.Move("d8h4")
        h.engine.legalByHistory[listOf("e2e4", "d8h4")] = emptySet()
        h.engine.checkByHistory[listOf("e2e4", "d8h4")] = true

        h.commit("e2e4")
        runCurrent()

        assertEquals(TerminalState.CHECKMATE, h.state.terminalState)
        assertTrue(h.sink.queued.contains("Your Black played D eight to H four. Checkmate."))
        assertEquals("Last: opp=e2e4 -> engine=d8h4, Checkmate", h.state.engineStatus)
    }

    @Test
    fun `failed export is spoken`() = gameTest { h ->
        h.startAsBlack()
        h.press(ChessKey.NEW_GAME)
        h.exportFailure = IllegalStateException("disk full")

        h.press(ChessKey.SPACE)

        assertEquals("Could not save game export", h.lastSpoken)
        assertEquals("Export failed: disk full", h.state.engineStatus)
    }

    @Test
    fun `new game chord forfeits a live game then returns to the menu`() = gameTest { h ->
        h.startAsBlack()

        h.press(ChessKey.NEW_GAME)
        assertEquals(GameEndReason.FORFEIT, h.state.finishedGame?.reason)
        assertEquals("Game ended. Save PGN/FEN.", h.lastSpoken)

        h.press(ChessKey.NEW_GAME)
        assertEquals(AppPhase.StartMenu(0), h.state.phase)
        assertNull(h.state.finishedGame)
        assertEquals("New game. Play as white", h.lastSpoken)
    }

    @Test
    fun `finished menu cycles options and new game selection returns to menu`() = gameTest { h ->
        h.startAsBlack()
        h.press(ChessKey.NEW_GAME)

        h.press(ChessKey.J)
        assertEquals(1, h.state.finishedGame?.selectedIndex)
        assertEquals("New game", h.lastSpoken)

        h.press(ChessKey.SPACE)
        assertEquals(AppPhase.StartMenu(0), h.state.phase)
    }

    // --- Draw detection -------------------------------------------------------

    private val knightShuffle = listOf("g1f3", "g8f6", "f3g1", "f6g8")

    @Test
    fun `third occurrence of a position announces a claimable draw and play continues`() = gameTest { h ->
        h.startAsBlack()
        h.engine.defaultLegalMoves = knightShuffle.toSet()
        // User types white's knight moves; the engine answers with black's.
        repeat(2) {
            h.engine.replies += BestMoveResult.Move("g8f6")
            h.commit("g1f3")
            runCurrent()
            h.engine.replies += BestMoveResult.Move("f6g8")
            h.commit("f3g1")
            runCurrent()
        }

        assertEquals(8, h.state.moveHistory.size)
        assertEquals("A draw can be claimed by threefold repetition.", h.sink.queued.last())
        assertNull(h.state.finishedGame)
    }

    @Test
    fun `fifth occurrence after an engine reply ends the game as a draw`() = gameTest { h ->
        h.startAsBlack()
        h.engine.defaultLegalMoves = knightShuffle.toSet()
        // 14 plies played; the typed white retreat plus black's reply
        // bring the start position back for the fifth time.
        h.state.moveHistory = MoveHistory((knightShuffle + knightShuffle + knightShuffle + knightShuffle).take(14))
        h.engine.replies += BestMoveResult.Move("f6g8")

        h.commit("f3g1")
        runCurrent()

        assertEquals(16, h.state.moveHistory.size)
        assertEquals(TerminalState.DRAW_REPETITION, h.state.terminalState)
        assertEquals(GameEndReason.DRAW_REPETITION, h.state.finishedGame?.reason)
        assertTrue(h.sink.queued.contains("Your Black played F six to G eight. Draw by repetition."))
        assertEquals("Save PGN/FEN", h.sink.queued.last())
    }

    @Test
    fun `typed move that completes fivefold repetition ends the game without asking the engine`() = gameTest { h ->
        h.startAsBlack()
        h.engine.defaultLegalMoves = knightShuffle.toSet()
        h.state.playerSide = ChessSide.WHITE
        h.state.moveHistory = MoveHistory((knightShuffle + knightShuffle + knightShuffle + knightShuffle).dropLast(1))

        h.commit("f6g8")
        runCurrent()

        assertEquals(TerminalState.DRAW_REPETITION, h.state.terminalState)
        assertTrue(h.engine.bestMoveRequests.isEmpty())
        assertEquals("Draw by repetition after f6g8 (16 plies)", h.state.engineStatus)
    }

    // --- Cycler, prompts and autofill ---------------------------------------

    @Test
    fun `cycler keys build the buffer and speak each coordinate`() = gameTest { h ->
        h.startAsBlack()

        h.press(ChessKey.D, ChessKey.D, ChessKey.F, ChessKey.J, ChessKey.K, ChessKey.K, ChessKey.K)

        assertEquals("b1a3", h.state.moveBuffer.toUciString())
        assertEquals(7, h.sink.spoken.size)
    }

    @Test
    fun `inactivity prompt speaks the buffer after the pause`() = gameTest { h ->
        h.startAsBlack()
        h.press(ChessKey.J)
        val countAfterPress = h.sink.spoken.size

        advanceTimeBy(INACTIVITY_PROMPT_MS - 1)
        runCurrent()
        assertEquals(countAfterPress, h.sink.spoken.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals("Opponent White move: A one to A one?", h.lastSpoken)
    }

    @Test
    fun `only legal move after the engine reply is prefilled`() = gameTest { h ->
        h.startAsBlack()
        h.engine.replies += BestMoveResult.Move("e7e5")
        h.engine.legalByHistory[listOf("e2e4", "e7e5")] = setOf("g1f3")

        h.commit("e2e4")
        runCurrent()

        assertEquals("g1f3", h.state.moveBuffer.toUciString())
        assertEquals("Only legal move: G one to F three.", h.sink.queued.last())
    }

    @Test
    fun `clearly best engine move is prefilled with its score gap`() = gameTest { h ->
        h.startAsBlack()
        h.engine.replies += BestMoveResult.Move("e7e5")
        h.engine.scoredByHistory[listOf("e2e4", "e7e5")] = listOf(
            ScoredMove("g1f3", EngineScore.Centipawns(200)),
            ScoredMove("d2d4", EngineScore.Centipawns(50)),
        )

        h.commit("e2e4")
        runCurrent()

        assertEquals("g1f3", h.state.moveBuffer.toUciString())
        assertTrue(h.state.engineStatus.startsWith("Suggestion: g1f3 [gap 150 cp]"))
    }

    @Test
    fun `selecting a piece with one legal move fills in its destination`() = gameTest { h ->
        h.startAsBlack()
        // e2 has exactly one legal move in the default set (e2e4).
        repeat(5) { h.press(ChessKey.D) }
        repeat(2) { h.press(ChessKey.F) }

        advanceTimeBy(SOURCE_AUTOFILL_DELAY_MS)
        runCurrent()

        assertEquals("e2e4", h.state.moveBuffer.toUciString())
        assertTrue(h.sink.queued.contains("Only move from selected piece: E two to E four."))
    }

    @Test
    fun `new input before the source delay cancels the source autofill`() = gameTest { h ->
        h.startAsBlack()
        repeat(5) { h.press(ChessKey.D) }
        repeat(2) { h.press(ChessKey.F) }
        advanceTimeBy(SOURCE_AUTOFILL_DELAY_MS / 2)
        h.press(ChessKey.J)

        advanceTimeBy(SOURCE_AUTOFILL_DELAY_MS)
        runCurrent()

        assertEquals("e2a1", h.state.moveBuffer.toUciString())
    }

    // --- Hardware keys and battery -------------------------------------------

    @Test
    fun `battery report keys are consumed and update the display`() = gameTest { h ->
        h.startAsBlack()

        val consumed = listOf(KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_8)
            .map(h.controller::onKeyDown)

        assertEquals(listOf(true, true, true, true), consumed)
        assertEquals(88, h.state.batteryPct)
        assertEquals(MoveBuffer.DEFAULT, h.state.moveBuffer)
    }

    @Test
    fun `hardware chess keys are routed and other keys are not consumed`() = gameTest { h ->
        h.startAsBlack()

        assertTrue(h.controller.onKeyDown(KeyEvent.KEYCODE_D))
        assertFalse(h.controller.onKeyDown(KeyEvent.KEYCODE_A))
        assertEquals("a1a1", h.state.moveBuffer.toUciString())
    }

    @Test
    fun `repeated low battery reports speak one warning`() = gameTest { h ->
        h.startAsBlack()

        h.controller.onMockBattery() // 88
        h.controller.onMockBattery() // 19
        h.controller.onMockBattery() // 4

        assertEquals("Mock battery report: 4%", h.state.engineStatus)
        assertEquals(1, h.sink.spoken.count { it.startsWith("Keypad battery") })
    }

    // --- Repeat ladder (narrative-sidekick) ---------------------------------

    private fun analysis(whiteCp: Int, vararg pv: String) =
        AnalysisSummary(whiteCentipawns = whiteCp, mate = null, bestMove = pv.first(), principalVariation = pv.toList())

    private fun Harness.scriptOpeningAnalyses() {
        engine.analysisByHistory[emptyList()] = analysis(30, "e2e4", "e7e5")
        engine.analysisByHistory[listOf("e2e4")] = analysis(30, "e7e5", "g1f3")
        engine.analysisByHistory[listOf("e2e4", "e7e5")] = analysis(35, "g1f3", "b8c6", "f1b5")
    }

    @Test
    fun `repeat ladder climbs from classic replay to sidekick L2 and L3`() = gameTest { h ->
        h.startAsBlack()
        h.scriptOpeningAnalyses()
        h.engine.replies += BestMoveResult.Move("e7e5")
        h.commit("e2e4")
        runCurrent()
        h.sink.spoken.clear()

        h.press(ChessKey.REPEAT_LAST)
        assertTrue(h.lastSpoken, h.lastSpoken.startsWith("Your Black played E seven to E five."))

        h.press(ChessKey.REPEAT_LAST)
        assertEquals("black pawn e five. best move. engine: knight f three, plus 0.4.", h.lastSpoken)

        h.press(ChessKey.REPEAT_LAST)
        val l3 = "black pawn e five. best move. engine: knight f three, plus 0.4. " +
            "then knight c six, then bishop b five."
        assertEquals(l3, h.lastSpoken)

        h.press(ChessKey.REPEAT_LAST)
        assertEquals(l3, h.lastSpoken)
    }

    @Test
    fun `a new move resets the repeat ladder`() = gameTest { h ->
        h.startAsBlack()
        h.scriptOpeningAnalyses()
        h.engine.replies += BestMoveResult.Move("e7e5")
        h.commit("e2e4")
        runCurrent()
        h.press(ChessKey.REPEAT_LAST, ChessKey.REPEAT_LAST)

        h.engine.legalByHistory[listOf("e2e4", "e7e5")] = setOf("g1f3")
        h.engine.replies += BestMoveResult.Move("b8c6")
        h.commit("g1f3")
        runCurrent()
        h.sink.spoken.clear()

        h.press(ChessKey.REPEAT_LAST)
        assertTrue(h.lastSpoken, h.lastSpoken.startsWith("Your Black played B eight to C six."))
    }

    @Test
    fun `manual mode flags the typed move's quality at L2`() = gameTest { h ->
        h.startAsBlack()
        h.press(ChessKey.TOGGLE_MANUAL)
        h.engine.analysisByHistory[emptyList()] = analysis(30, "d2d4", "d7d5")
        h.engine.analysisByHistory[listOf("e2e4")] = analysis(-60, "e7e5", "g1f3")
        h.commit("e2e4")
        runCurrent()

        h.press(ChessKey.REPEAT_LAST, ChessKey.REPEAT_LAST)

        assertEquals(
            "white pawn e four. inaccuracy, lost about half a pawn. engine: e five, plus 0.6.",
            h.lastSpoken,
        )
    }

    @Test
    fun `repeat ladder without analysis still names the move`() = gameTest { h ->
        h.startAsBlack()
        h.engine.replies += BestMoveResult.Move("e7e5")
        h.commit("e2e4")
        runCurrent()

        h.press(ChessKey.REPEAT_LAST, ChessKey.REPEAT_LAST)

        assertEquals("black pawn e five.", h.lastSpoken)
    }

    @Test
    fun `repeat ladder falls back to the classic replay for a non-chess history`() = gameTest { h ->
        h.startAsBlack()
        h.engine.legalByHistory[emptyList()] = setOf("e2e5")
        h.commit("e2e5")
        runCurrent()
        h.sink.spoken.clear()

        h.press(ChessKey.REPEAT_LAST, ChessKey.REPEAT_LAST)

        assertEquals(h.sink.spoken[0], h.sink.spoken[1])
    }

    // --- Session restore ------------------------------------------------------

    @Test
    fun `restored live game announces whose turn it is and can repeat the last move`() = gameTest { h ->
        val snapshot = GameState().apply {
            phase = AppPhase.InGame
            playerSide = ChessSide.BLACK
            moveHistory = MoveHistory(listOf("e2e4", "e7e5"))
        }.toSnapshot()

        h.controller.restore(snapshot)
        h.controller.announceStartup(restored = true)

        assertEquals("Resumed: history=2 plies", h.state.engineStatus)
        assertEquals("Resumed game. Waiting for Opponent White.", h.lastSpoken)

        h.press(ChessKey.REPEAT_LAST)
        assertTrue(h.lastSpoken.startsWith("Your Black played E seven to E five. Waiting for Opponent White."))
    }

    @Test
    fun `restored finished game reopens the finished menu`() = gameTest { h ->
        val snapshot = GameState().apply {
            phase = AppPhase.InGame
            moveHistory = MoveHistory(listOf("f2f3", "e7e5", "g2g4", "d8h4"))
            terminalState = TerminalState.CHECKMATE
        }.toSnapshot()

        h.controller.restore(snapshot)
        h.controller.announceStartup(restored = true)

        assertEquals(GameEndReason.CHECKMATE, h.state.finishedGame?.reason)
        assertEquals("Resumed game. Save PGN/FEN.", h.lastSpoken)
    }

    @Test
    fun `restore clamps out-of-range menu indexes`() = gameTest { h ->
        val snapshot = GameState().apply {
            phase = AppPhase.StartMenu(selectedIndex = 9)
        }.toSnapshot()

        h.controller.restore(snapshot)

        assertEquals(AppPhase.StartMenu(1), h.state.phase)
    }
}
