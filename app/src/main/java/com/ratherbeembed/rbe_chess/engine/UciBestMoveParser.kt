package com.ratherbeembed.rbe_chess.engine

object UciBestMoveParser {
    private val bestMoveLine = Regex("""^\s*bestmove\s+(\S+).*$""")
    private val mateScore = Regex("""\bscore\s+mate\s+-?\d+\b""")

    fun moveFromLine(line: String): String? =
        bestMoveLine.matchEntire(line)?.groupValues?.get(1)

    fun hasMateScore(line: String): Boolean =
        mateScore.containsMatchIn(line)
}
