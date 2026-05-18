package com.ratherbeembed.rbe_chess.engine

data class UciAnalysisInfo(
    val score: EngineScore,
    val principalVariation: List<String>,
)

object UciAnalysisParser {
    fun analysisFromInfoLine(line: String): UciAnalysisInfo? {
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

        return UciAnalysisInfo(
            score = score,
            principalVariation = tokens.drop(pvIndex + 1),
        )
    }
}
