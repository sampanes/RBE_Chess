package com.ratherbeembed.rbe_chess.input

object MoveAutofill {

    fun onlyLegalMove(legalMoves: Set<String>): String? =
        legalMoves.singleOrNull()

    fun onlyLegalMoveFrom(legalMoves: Set<String>, fromSquare: String): String? {
        require(fromSquare.length == 2) {
            "expected 2-char source square, got '$fromSquare'"
        }
        return legalMoves
            .filter { it.length >= 4 && it.startsWith(fromSquare) }
            .singleOrNull()
    }
}
