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
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import com.ratherbeembed.rbe_chess.engine.StockfishProcessEngine
import com.ratherbeembed.rbe_chess.export.GameExportStore
import com.ratherbeembed.rbe_chess.game.GameController
import com.ratherbeembed.rbe_chess.game.GameLog
import com.ratherbeembed.rbe_chess.pocket.PocketModeController
import com.ratherbeembed.rbe_chess.pocket.PocketModeState
import com.ratherbeembed.rbe_chess.session.SessionStore
import com.ratherbeembed.rbe_chess.speech.BestMoveSpeaker
import com.ratherbeembed.rbe_chess.speech.SpeechOutput
import com.ratherbeembed.rbe_chess.ui.AppRoot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "RBE_CHESS"

private object AndroidGameLog : GameLog {
    override fun d(message: String) {
        Log.d(TAG, message)
    }

    override fun e(message: String, error: Throwable) {
        Log.e(TAG, message, error)
    }
}

/**
 * Android shell: owns the TTS, Stockfish process, Pocket Mode window flags
 * and session storage, and forwards keys to [GameController], which holds
 * all game logic.
 */
class MainActivity : ComponentActivity() {
    private var pocketMode by mutableStateOf(PocketModeState.Normal)
    private lateinit var speechOutput: SpeechOutput
    private lateinit var pocketController: PocketModeController
    private lateinit var engine: StockfishProcessEngine
    private lateinit var controller: GameController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechOutput = SpeechOutput(this)
        pocketController = PocketModeController(this)
        engine = StockfishProcessEngine(this)
        val exportStore = GameExportStore(this)
        val sessionStore = SessionStore(this)
        controller = GameController(
            scope = lifecycleScope,
            engine = engine,
            speaker = BestMoveSpeaker(speechOutput),
            saveExport = exportStore::save,
            log = AndroidGameLog,
        )

        val snapshot = sessionStore.load()
        snapshot?.let(controller::restore)

        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            val state = controller.state
            MaterialTheme(colorScheme = colors) {
                AppRoot(
                    phase = state.phase,
                    buffer = state.moveBuffer,
                    pocketMode = pocketMode,
                    history = state.moveHistory,
                    pendingMove = state.pendingMove,
                    promotionBaseMove = state.promotionPick?.baseMove,
                    finishedGame = state.finishedGame,
                    engineStatus = state.engineStatus,
                    gameMode = state.gameMode,
                    playerSide = state.playerSide,
                    batteryPct = state.batteryPct,
                    miniKeyboardVisible = state.miniKeyboardVisible,
                    onToggleMiniKeyboard = controller::toggleMiniKeyboard,
                    onMiniKey = controller::onMiniKey,
                    onMockBattery = controller::onMockBattery,
                    onEnterPocketMode = ::enterPocketMode,
                    onExitPocketMode = ::exitPocketMode,
                )
            }
        }

        lifecycleScope.launch {
            snapshotFlow { controller.state.toSnapshot() }
                .distinctUntilChanged()
                .collect(sessionStore::save)
        }
        controller.announceStartup(restored = snapshot != null)
    }

    override fun onDestroy() {
        controller.shutdown()
        engine.shutdown()
        pocketController.exit()
        speechOutput.shutdown()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && controller.onKeyDown(event.keyCode)) {
            return true
        }
        return super.dispatchKeyEvent(event)
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
}
