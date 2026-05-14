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
import com.ratherbeembed.rbe_chess.input.ChessKey
import com.ratherbeembed.rbe_chess.input.GrammarAction
import com.ratherbeembed.rbe_chess.input.HardwareKeyboardHandler
import com.ratherbeembed.rbe_chess.input.KeyboardGrammar
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.ui.AppRoot

private const val TAG = "RBE_CHESS"

class MainActivity : ComponentActivity() {
    private var moveBuffer by mutableStateOf(MoveBuffer.DEFAULT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                AppRoot(buffer = moveBuffer)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val key = HardwareKeyboardHandler.toChessKey(event.keyCode)
            if (key != ChessKey.IGNORED) {
                val action = KeyboardGrammar.translate(key)
                val before = moveBuffer
                moveBuffer = KeyboardGrammar.apply(action, before)
                if (action == GrammarAction.Commit) {
                    Log.d(TAG, "Commit: ${before.toUciString()}, reset to ${moveBuffer.toUciString()}")
                } else {
                    Log.d(TAG, "Key=$key action=$action buffer=${moveBuffer.toUciString()}")
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
