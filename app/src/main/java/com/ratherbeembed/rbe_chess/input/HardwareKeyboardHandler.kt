package com.ratherbeembed.rbe_chess.input

import android.view.KeyEvent

object HardwareKeyboardHandler {
    fun shouldIgnoreRepeat(keyCode: Int, repeatCount: Int): Boolean =
        repeatCount > 0 && toChessKey(keyCode) != ChessKey.IGNORED

    fun toChessKey(keyCode: Int): ChessKey = when (keyCode) {
        KeyEvent.KEYCODE_D -> ChessKey.D
        KeyEvent.KEYCODE_F -> ChessKey.F
        KeyEvent.KEYCODE_J -> ChessKey.J
        KeyEvent.KEYCODE_K -> ChessKey.K
        KeyEvent.KEYCODE_SPACE -> ChessKey.SPACE
        // Firmware v2 chord emissions. See KeyboardGrammar.ChessKey docs.
        KeyEvent.KEYCODE_U -> ChessKey.UNDO
        KeyEvent.KEYCODE_M -> ChessKey.TOGGLE_MANUAL
        KeyEvent.KEYCODE_R -> ChessKey.REPEAT_LAST
        KeyEvent.KEYCODE_N -> ChessKey.NEW_GAME
        else -> ChessKey.IGNORED
    }
}
