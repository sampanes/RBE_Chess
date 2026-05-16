package com.ratherbeembed.rbe_chess.input

enum class MiniKeyboardButton {
    PINKY,
    RING,
    MIDDLE,
    INDEX,
    THUMB,
}

data class MiniKeyboardResult(
    val key: ChessKey,
    val thumbHeld: Boolean,
)

object MiniKeyboardInput {
    fun tap(button: MiniKeyboardButton, thumbHeld: Boolean): MiniKeyboardResult =
        when {
            button == MiniKeyboardButton.THUMB -> MiniKeyboardResult(
                key = ChessKey.SPACE,
                thumbHeld = false,
            )
            thumbHeld -> MiniKeyboardResult(
                key = chordKey(button),
                thumbHeld = false,
            )
            else -> MiniKeyboardResult(
                key = directKey(button),
                thumbHeld = false,
            )
        }

    private fun directKey(button: MiniKeyboardButton): ChessKey =
        when (button) {
            MiniKeyboardButton.PINKY -> ChessKey.D
            MiniKeyboardButton.RING -> ChessKey.F
            MiniKeyboardButton.MIDDLE -> ChessKey.J
            MiniKeyboardButton.INDEX -> ChessKey.K
            MiniKeyboardButton.THUMB -> ChessKey.SPACE
        }

    private fun chordKey(button: MiniKeyboardButton): ChessKey =
        when (button) {
            MiniKeyboardButton.PINKY -> ChessKey.UNDO
            MiniKeyboardButton.RING -> ChessKey.TOGGLE_MANUAL
            MiniKeyboardButton.MIDDLE -> ChessKey.REPEAT_LAST
            MiniKeyboardButton.INDEX -> ChessKey.NEW_GAME
            MiniKeyboardButton.THUMB -> ChessKey.SPACE
        }
}
