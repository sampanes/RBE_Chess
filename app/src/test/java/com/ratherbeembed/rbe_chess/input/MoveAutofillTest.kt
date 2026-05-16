package com.ratherbeembed.rbe_chess.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveAutofillTest {

    @Test
    fun `onlyLegalMove returns the move when exactly one legal move exists`() {
        assertEquals("e2e4", MoveAutofill.onlyLegalMove(setOf("e2e4")))
    }

    @Test
    fun `onlyLegalMove returns null when there are zero or many legal moves`() {
        assertNull(MoveAutofill.onlyLegalMove(emptySet()))
        assertNull(MoveAutofill.onlyLegalMove(setOf("e2e4", "d2d4")))
    }

    @Test
    fun `onlyLegalMoveFrom returns the only legal move from a selected source`() {
        val legal = setOf("e2e4", "d2d4", "g1f3")

        assertEquals("e2e4", MoveAutofill.onlyLegalMoveFrom(legal, "e2"))
    }

    @Test
    fun `onlyLegalMoveFrom returns null when selected source has multiple moves`() {
        val legal = setOf("e2e3", "e2e4", "g1f3")

        assertNull(MoveAutofill.onlyLegalMoveFrom(legal, "e2"))
    }

    @Test
    fun `onlyLegalMoveFrom returns null when selected source has no moves`() {
        val legal = setOf("e2e4", "g1f3")

        assertNull(MoveAutofill.onlyLegalMoveFrom(legal, "a1"))
    }
}
