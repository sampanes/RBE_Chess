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
import com.ratherbeembed.rbe_chess.input.ChessKey
import com.ratherbeembed.rbe_chess.input.GrammarAction
import com.ratherbeembed.rbe_chess.input.HardwareKeyboardHandler
import com.ratherbeembed.rbe_chess.input.KeyboardGrammar
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.speech.BestMoveSpeaker
import com.ratherbeembed.rbe_chess.speech.SpeechOutput
import com.ratherbeembed.rbe_chess.ui.AppRoot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "RBE_CHESS"
private const val INACTIVITY_PROMPT_MS = 2_500L

class MainActivity : ComponentActivity() {
    private var moveBuffer by mutableStateOf(MoveBuffer.DEFAULT)
    private lateinit var speechOutput: SpeechOutput
    private lateinit var speaker: BestMoveSpeaker
    private var inactivityJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechOutput = SpeechOutput(this)
        speaker = BestMoveSpeaker(speechOutput)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                AppRoot(buffer = moveBuffer)
            }
        }
    }

    override fun onDestroy() {
        inactivityJob?.cancel()
        inactivityJob = null
        speechOutput.shutdown()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val key = HardwareKeyboardHandler.toChessKey(event.keyCode)
            if (key != ChessKey.IGNORED) {
                val action = KeyboardGrammar.translate(key)
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
                Log.d(TAG, "Commit ${before.toUciString()} (engine wiring is step 4)")
            }
            GrammarAction.Ignored -> Unit
        }
    }

    private fun scheduleInactivityPrompt(buffer: MoveBuffer) {
        inactivityJob = lifecycleScope.launch {
            delay(INACTIVITY_PROMPT_MS)
            speaker.speakInactivityPrompt(buffer)
        }
    }
}
