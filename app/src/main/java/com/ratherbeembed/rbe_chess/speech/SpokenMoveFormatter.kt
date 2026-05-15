package com.ratherbeembed.rbe_chess.speech

import com.ratherbeembed.rbe_chess.input.MoveBuffer

/**
 * Pure-Kotlin formatter that turns chess primitives into the strings TTS will
 * speak. Kept free of Android types so it can be unit-tested on the JVM.
 *
 * Style follows the addendum's bestmove example: capital file letter +
 * spelled-out rank digit ("E two", "B eight"). Promotion suffix is appended
 * after a comma.
 */
object SpokenMoveFormatter {

    private val DIGIT_WORDS = arrayOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight",
    )

    private val PROMO_WORDS = mapOf(
        'q' to "queen",
        'r' to "rook",
        'b' to "bishop",
        'n' to "knight",
    )

    /** Per-press feedback for D / J. "a" -> "A". */
    fun spokenFile(file: Char): String = file.uppercaseChar().toString()

    /** Per-press feedback for F / K. 1 -> "one", 8 -> "eight". */
    fun spokenRank(rank: Int): String {
        require(rank in 1..8) { "rank out of range: $rank" }
        return DIGIT_WORDS[rank - 1]
    }

    /** "e2" -> "E two". */
    fun spokenSquare(file: Char, rank: Int): String =
        "${spokenFile(file)} ${spokenRank(rank)}"

    /**
     * Inactivity prompt for the partially-entered move buffer. Phrased as
     * "Saw <from> to <to>?" so it reads as confirmation of the opponent's
     * move the app detected, not a command directed at the user.
     * Untouched coords surface as 'a' / 1 per [MoveBuffer]'s display rules,
     * so an untouched buffer reads as "Saw A one to A one?".
     */
    fun spokenInactivityPrompt(buffer: MoveBuffer): String {
        val from = spokenSquare(buffer.fromFile, buffer.fromRank)
        val to = spokenSquare(buffer.toFile, buffer.toRank)
        return "Saw $from to $to?"
    }

    fun spokenUciMove(uci: String): String {
        require(uci.length == 4 || uci.length == 5) {
            "expected 4- or 5-char UCI, got '$uci'"
        }
        val from = spokenSquare(uci[0], uci[1].digitToInt())
        val to = spokenSquare(uci[2], uci[3].digitToInt())
        val core = "$from to $to"
        return if (uci.length == 5) {
            val piece = PROMO_WORDS[uci[4].lowercaseChar()]
                ?: throw IllegalArgumentException("unknown promotion piece in '$uci'")
            "$core, $piece"
        } else {
            core
        }
    }

    /**
     * Bestmove phrase. UCI is 4 chars (e.g. "b8c6") or 5 with a promotion
     * piece suffix (e.g. "e7e8q"). Examples from the addendum:
     *   b8c6  -> "Best move: B eight to C six."
     *   e7e8q -> "Best move: E seven to E eight, queen."
     */
    fun spokenBestMove(uci: String): String {
        require(uci.length == 4 || uci.length == 5) {
            "expected 4- or 5-char UCI, got '$uci'"
        }
        return "Best move: ${spokenUciMove(uci)}."
    }
}
