package com.ratherbeembed.rbe_chess.engine

object UciPerftParser {
    private val moveLine = Regex("""^\s*([a-h][1-8][a-h][1-8][qrbn]?)\s*:\s*\d+\s*$""")

    fun moveFromLine(line: String): String? =
        moveLine.matchEntire(line)?.groupValues?.get(1)
}
