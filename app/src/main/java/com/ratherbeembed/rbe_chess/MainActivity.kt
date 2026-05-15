package com.ratherbeembed.rbe_chess

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.StockfishProcessEngine
import com.ratherbeembed.rbe_chess.input.ChessKey
import com.ratherbeembed.rbe_chess.input.GrammarAction
import com.ratherbeembed.rbe_chess.input.HardwareKeyboardHandler
import com.ratherbeembed.rbe_chess.input.KeyboardGrammar
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.pocket.PocketModeController
import com.ratherbeembed.rbe_chess.pocket.PocketModeState
import com.ratherbeembed.rbe_chess.speech.BestMoveSpeaker
import com.ratherbeembed.rbe_chess.speech.SpeechOutput
import com.ratherbeembed.rbe_chess.ui.AppRoot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "RBE_CHESS"
private const val INACTIVITY_PROMPT_MS = 2_500L
private const val ENGINE_MOVETIME_MS = 1_000L

class MainActivity : ComponentActivity() {
    private var moveBuffer by mutableStateOf(MoveBuffer.DEFAULT)
    private var pocketMode by mutableStateOf(PocketModeState.Normal)
    private var moveHistory by mutableStateOf(MoveHistory.EMPTY)
    private var engineStatus by mutableStateOf("Engine: idle")
    private lateinit var speechOutput: SpeechOutput
    private lateinit var speaker: BestMoveSpeaker
    private lateinit var pocketController: PocketModeController
    private lateinit var engine: StockfishProcessEngine
    private var inactivityJob: Job? = null
    private var engineJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechOutput = SpeechOutput(this)
        speaker = BestMoveSpeaker(speechOutput)
        pocketController = PocketModeController(this)
        engine = StockfishProcessEngine(this)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                AppRoot(
                    buffer = moveBuffer,
                    pocketMode = pocketMode,
                    history = moveHistory,
                    engineStatus = engineStatus,
                    onEnterPocketMode = ::enterPocketMode,
                    onExitPocketMode = ::exitPocketMode,
                    onTestStockfish = ::testStockfish,
                )
            }
        }
    }

    override fun onDestroy() {
        inactivityJob?.cancel()
        inactivityJob = null
        engineJob?.cancel()
        engineJob = null
        engine.shutdown()
        pocketController.exit()
        speechOutput.shutdown()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val key = HardwareKeyboardHandler.toChessKey(event.keyCode)
            if (key != ChessKey.IGNORED) {
                val action = KeyboardGrammar.translate(key)
                if (action == GrammarAction.Commit && engineJob?.isActive == true) {
                    // Engine is mid-think; ignore the press entirely so the
                    // current buffer stays visible and TTS isn't double-fired.
                    Log.d(TAG, "Commit ignored — engine still calculating")
                    return true
                }
                val before = moveBuffer
                val after = KeyboardGrammar.apply(action, before)
                moveBuffer = after
                handleAction(action, before, after)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleAction(action: GrammarAction, before: MoveBuffer, after: MoveBuffer) {
        inactivityJob?.cancel()
        inactivityJob = null
        when (action) {
            GrammarAction.CycleFromFile -> {
                speaker.speakFilePress(after.fromFile)
                Log.d(TAG, "from-file -> ${after.fromFile} buffer=${after.toUciString()}")
                scheduleInactivityPrompt(after)
            }
            GrammarAction.CycleFromRank -> {
                speaker.speakRankPress(after.fromRank)
                Log.d(TAG, "from-rank -> ${after.fromRank} buffer=${after.toUciString()}")
                scheduleInactivityPrompt(after)
            }
            GrammarAction.CycleToFile -> {
                speaker.speakFilePress(after.toFile)
                Log.d(TAG, "to-file -> ${after.toFile} buffer=${after.toUciString()}")
                scheduleInactivityPrompt(after)
            }
            GrammarAction.CycleToRank -> {
                speaker.speakRankPress(after.toRank)
                Log.d(TAG, "to-rank -> ${after.toRank} buffer=${after.toUciString()}")
                scheduleInactivityPrompt(after)
            }
            GrammarAction.Commit -> {
                commitMove(before.toUciString())
            }
            GrammarAction.Ignored -> Unit
        }
    }

    /**
     * Step 4 commit: speak "Calculating", boot the engine if needed, ask
     * Stockfish for the bestmove given (current history + opponent move),
     * speak it, and auto-advance both plies into [moveHistory]. On engine
     * error we surface the message but leave [moveHistory] untouched so the
     * user can retry without a corrupt move list.
     */
    private fun commitMove(opponentMove: String) {
        speaker.speakCommit()
        val historyForEngine = moveHistory.append(opponentMove)
        engineStatus = "Engine: thinking on $opponentMove..."
        Log.d(TAG, "Commit $opponentMove (history -> ${historyForEngine.moves})")
        engineJob = lifecycleScope.launch {
            try {
                engine.boot()
                val best = engine.bestMove(
                    uciMoves = historyForEngine.moves,
                    movetimeMs = ENGINE_MOVETIME_MS,
                )
                speaker.speakBestMove(best)
                moveHistory = historyForEngine.append(best)
                engineStatus = "Last: opp=$opponentMove → engine=$best (${moveHistory.size} plies)"
                Log.d(TAG, "Step 4 reply: opp=$opponentMove eng=$best history=${moveHistory.moves}")
            } catch (t: Throwable) {
                engineStatus = "Engine error after $opponentMove: ${t.message}"
                Log.e(TAG, "engine bestmove failed", t)
            }
        }
    }

    private fun scheduleInactivityPrompt(buffer: MoveBuffer) {
        inactivityJob = lifecycleScope.launch {
            delay(INACTIVITY_PROMPT_MS)
            speaker.speakInactivityPrompt(buffer)
        }
    }

    private fun enterPocketMode() {
        pocketController.enter()
        pocketMode = PocketModeState.Pocket
        Log.d(TAG, "Pocket Mode ON")
    }

    private fun exitPocketMode() {
        pocketController.exit()
        pocketMode = PocketModeState.Normal
        Log.d(TAG, "Pocket Mode OFF")
    }

    private fun testStockfish() {
        if (engineJob?.isActive == true) {
            Log.d(TAG, "Stockfish test already running, ignoring")
            return
        }
        engineStatus = "Engine: booting..."
        engineJob = lifecycleScope.launch {
            try {
                engine.boot()
                engineStatus = "Engine: thinking (movetime ${ENGINE_MOVETIME_MS} ms)..."
                val move = engine.bestMove(uciMoves = emptyList(), movetimeMs = ENGINE_MOVETIME_MS)
                engineStatus = "Engine bestmove from startpos: $move"
                Log.d(TAG, "Stockfish PoC bestmove = $move")
                speaker.speakBestMove(move)
            } catch (t: Throwable) {
                engineStatus = "Engine error: ${t.message}"
                Log.e(TAG, "Stockfish PoC failed", t)
            }
        }
    }
}
