package com.ratherbeembed.rbe_chess.input

import android.view.KeyEvent

object HardwareKeyboardHandler {
    fun toChessKey(keyCode: Int): ChessKey = when (keyCode) {
        KeyEvent.KEYCODE_D -> ChessKey.D
        KeyEvent.KEYCODE_F -> ChessKey.F
        KeyEvent.KEYCODE_J -> ChessKey.J
        KeyEvent.KEYCODE_K -> ChessKey.K
        KeyEvent.KEYCODE_SPACE -> ChessKey.SPACE
        else -> ChessKey.IGNORED
    }
}
