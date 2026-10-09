package com.ratherbeembed.rbe_chess.narrator

/**
 * SAN -> speech-ready words (narrative-sidekick SPEC sections 4 and 7).
 * A cheap TTS mangles "Nf3#"; this hands it "knight f three, checkmate".
 *
 * Pure table lookup. Unparseable input comes back unchanged rather than
 * throwing, so a surprise string is at worst read raw, never dropped.
 */
object SpokenSan {

    private val SAN = Regex("^([KQRBN])?([a-h])?([1-8])?(x)?([a-h][1-8])(?:=([QRBN]))?([+#])?$")
    private val CASTLE = Regex("^(O-O(?:-O)?)([+#])?$")

    private val NUMBER_WORDS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    )

    private val NATO = mapOf(
        'a' to "alpha", 'b' to "bravo", 'c' to "charlie", 'd' to "delta",
        'e' to "echo", 'f' to "foxtrot", 'g' to "golf", 'h' to "hotel",
    )

    /**
     * Expands one SAN move. [pawnWord] adds "pawn" before a quiet pawn move
     * (SPEC 7: omitted at L1, spoken at L2+). [phonetic] swaps file letters
     * for NATO words (SPEC 4 override hook, off by default).
     */
    fun expand(san: String, pawnWord: Boolean = false, phonetic: Boolean = false): String {
        val text = san.trim()
        CASTLE.matchEntire(text)?.let { m ->
            val side = if (m.groupValues[1] == "O-O") "castles kingside" else "castles queenside"
            return side + suffix(m.groupValues[2])
        }
        val m = SAN.matchEntire(text) ?: return text
        val piece = m.groupValues[1]
        val fromFile = m.groupValues[2]
        val fromRank = m.groupValues[3]
        val capture = m.groupValues[4].isNotEmpty()
        val dest = square(m.groupValues[5], phonetic)
        val promotion = m.groupValues[6]
        val words = mutableListOf<String>()
        if (piece.isEmpty()) {
            if (capture) {
                words += file(fromFile.ifEmpty { "?" }[0], phonetic)
            } else if (pawnWord) {
                words += "pawn"
            }
        } else {
            words += pieceWord(piece[0])
            val disambiguated = fromFile.isNotEmpty() || fromRank.isNotEmpty()
            when {
                fromFile.isNotEmpty() && fromRank.isNotEmpty() ->
                    words += "from ${square(fromFile + fromRank, phonetic)}"
                fromFile.isNotEmpty() -> words += "from ${file(fromFile[0], phonetic)}"
                fromRank.isNotEmpty() -> words += "from rank ${number(fromRank.toInt())}"
            }
            if (disambiguated && !capture) words += "to"
        }
        if (capture) words += "takes"
        words += dest
        if (promotion.isNotEmpty()) words += "promotes to ${pieceWord(promotion[0])}"
        return words.joinToString(" ") + suffix(m.groupValues[7])
    }

    /** "e2" -> "e two". */
    fun square(uci: String, phonetic: Boolean = false): String =
        "${file(uci[0], phonetic)} ${number(uci[1].digitToInt())}"

    /** 0..10 as words, larger numbers as digits. */
    fun number(n: Int): String = NUMBER_WORDS.getOrNull(n) ?: n.toString()

    fun pieceWord(letter: Char): String =
        when (letter.uppercaseChar()) {
            'K' -> "king"
            'Q' -> "queen"
            'R' -> "rook"
            'B' -> "bishop"
            'N' -> "knight"
            else -> "pawn"
        }

    private fun file(c: Char, phonetic: Boolean): String =
        if (phonetic) NATO[c] ?: c.toString() else c.toString()

    private fun suffix(mark: String): String =
        when (mark) {
            "+" -> ", check"
            "#" -> ", checkmate"
            else -> ""
        }
}
