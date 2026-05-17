package com.ratherbeembed.rbe_chess.engine

data class UciScoredMoveInfo(
    val multipv: Int,
    val move: ScoredMove,
)

object UciScoredMoveParser {
    fun scoredMoveFromInfoLine(line: String): UciScoredMoveInfo? {
        val tokens = line.trim().split(Regex("""\s+"""))
        if (tokens.firstOrNull() != "info") return null

        val scoreIndex = tokens.indexOf("score")
        val pvIndex = tokens.indexOf("pv")
        if (scoreIndex == -1 || pvIndex == -1 || pvIndex + 1 >= tokens.size) return null
        if (scoreIndex + 2 >= tokens.size) return null

        val score = when (tokens[scoreIndex + 1]) {
            "cp" -> tokens[scoreIndex + 2].toIntOrNull()?.let(EngineScore::Centipawns)
            "mate" -> tokens[scoreIndex + 2].toIntOrNull()?.let(EngineScore::Mate)
            else -> null
        } ?: return null

        val multipvIndex = tokens.indexOf("multipv")
        val multipv =
            if (multipvIndex != -1 && multipvIndex + 1 < tokens.size) {
                tokens[multipvIndex + 1].toIntOrNull() ?: 1
            } else {
                1
            }

        return UciScoredMoveInfo(
            multipv = multipv,
            move = ScoredMove(
                uci = tokens[pvIndex + 1],
                score = score,
            ),
        )
    }
}
