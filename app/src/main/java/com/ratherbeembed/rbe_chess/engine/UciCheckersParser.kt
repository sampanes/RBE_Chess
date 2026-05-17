package com.ratherbeembed.rbe_chess.engine

object UciCheckersParser {
    private const val PREFIX = "Checkers:"

    fun hasCheckers(line: String): Boolean? {
        val trimmed = line.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        return trimmed.removePrefix(PREFIX).isNotBlank()
    }
}
