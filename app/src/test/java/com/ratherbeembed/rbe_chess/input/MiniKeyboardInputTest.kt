package com.ratherbeembed.rbe_chess.input

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniKeyboardInputTest {

    @Test
    fun `direct mini taps map to cycler keys`() {
        assertEquals(
            MiniKeyboardResult(ChessKey.D, thumbHeld = false),
            MiniKeyboardInput.tap(MiniKeyboardButton.PINKY, thumbHeld = false),
        )
        assertEquals(
            MiniKeyboardResult(ChessKey.F, thumbHeld = false),
            MiniKeyboardInput.tap(MiniKeyboardButton.RING, thumbHeld = false),
        )
        assertEquals(
            MiniKeyboardResult(ChessKey.J, thumbHeld = false),
            MiniKeyboardInput.tap(MiniKeyboardButton.MIDDLE, thumbHeld = false),
        )
        assertEquals(
            MiniKeyboardResult(ChessKey.K, thumbHeld = false),
            MiniKeyboardInput.tap(MiniKeyboardButton.INDEX, thumbHeld = false),
        )
    }

    @Test
    fun `thumb tap maps to commit and clears held state`() {
        assertEquals(
            MiniKeyboardResult(ChessKey.SPACE, thumbHeld = false),
            MiniKeyboardInput.tap(MiniKeyboardButton.THUMB, thumbHeld = false),
        )
        assertEquals(
            MiniKeyboardResult(ChessKey.SPACE, thumbHeld = false),
            MiniKeyboardInput.tap(MiniKeyboardButton.THUMB, thumbHeld = true),
        )
    }

    @Test
    fun `thumb-held mini taps map to chord keys and clear held state`() {
        assertEquals(
            MiniKeyboardResult(ChessKey.UNDO, thumbHeld = false),
            MiniKeyboardInput.tap(MiniKeyboardButton.PINKY, thumbHeld = true),
        )
        assertEquals(
            MiniKeyboardResult(ChessKey.TOGGLE_MANUAL, thumbHeld = false),
            MiniKeyboardInput.tap(MiniKeyboardButton.RING, thumbHeld = true),
        )
        assertEquals(
            MiniKeyboardResult(ChessKey.REPEAT_LAST, thumbHeld = false),
            MiniKeyboardInput.tap(MiniKeyboardButton.MIDDLE, thumbHeld = true),
        )
        assertEquals(
            MiniKeyboardResult(ChessKey.NEW_GAME, thumbHeld = false),
            MiniKeyboardInput.tap(MiniKeyboardButton.INDEX, thumbHeld = true),
        )
    }
}
