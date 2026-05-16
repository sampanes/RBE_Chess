package com.ratherbeembed.rbe_chess.input

import androidx.compose.runtime.Immutable

/**
 * The four under-construction coordinates of a from-to move. Each index is
 * null until the user first presses its cycle button, then 0..7. Display
 * treats null as 0 (so an untouched coord shows as 'a' or '1' in the
 * inactivity prompt), but pressing distinguishes the two so the first press
 * lands on 'a' / '1' instead of advancing past it.
 *
 * @Immutable so Compose treats this as a stable parameter and recomposes
 * AppRoot when a new instance is passed in. Without the annotation, Kotlin
 * 2.x stability inference doesn't always classify data classes with
 * nullable primitive fields as stable, and strong-skipping can suppress
 * recomposition.
 */
@Immutable
data class MoveBuffer(
    val fromFileIdx: Int? = null,
    val fromRankIdx: Int? = null,
    val toFileIdx: Int? = null,
    val toRankIdx: Int? = null,
    val fromFileReadPending: Boolean = false,
    val fromRankReadPending: Boolean = false,
    val toFileReadPending: Boolean = false,
    val toRankReadPending: Boolean = false,
) {
    val fromFile: Char get() = 'a' + (fromFileIdx ?: 0)
    val fromRank: Int get() = (fromRankIdx ?: 0) + 1
    val toFile: Char get() = 'a' + (toFileIdx ?: 0)
    val toRank: Int get() = (toRankIdx ?: 0) + 1

    val fromSquareOrNull: String?
        get() =
            if (fromFileIdx != null && fromRankIdx != null) {
                "$fromFile$fromRank"
            } else {
                null
            }

    fun cycleFromFile(): MoveBuffer =
        if (fromFileIdx != null && fromFileReadPending) {
            copy(fromFileReadPending = false)
        } else {
            copy(
                fromFileIdx = advance(fromFileIdx),
                fromFileReadPending = false,
            )
        }

    fun cycleFromRank(): MoveBuffer =
        if (fromRankIdx != null && fromRankReadPending) {
            copy(fromRankReadPending = false)
        } else {
            copy(
                fromRankIdx = advance(fromRankIdx),
                fromRankReadPending = false,
            )
        }

    fun cycleToFile(): MoveBuffer =
        if (toFileIdx != null && toFileReadPending) {
            copy(toFileReadPending = false)
        } else {
            copy(
                toFileIdx = advance(toFileIdx),
                toFileReadPending = false,
            )
        }

    fun cycleToRank(): MoveBuffer =
        if (toRankIdx != null && toRankReadPending) {
            copy(toRankReadPending = false)
        } else {
            copy(
                toRankIdx = advance(toRankIdx),
                toRankReadPending = false,
            )
        }

    fun copyFromEngine(uci: String): MoveBuffer {
        require(uci.length == 4 || uci.length == 5) {
            "expected 4- or 5-char UCI, got '$uci'"
        }
        val fromFile = fileToIndex(uci[0])
        val fromRank = rankToIndex(uci[1])
        val toFile = fileToIndex(uci[2])
        val toRank = rankToIndex(uci[3])
        return copy(
            fromFileIdx = fromFile,
            fromRankIdx = fromRank,
            toFileIdx = toFile,
            toRankIdx = toRank,
            fromFileReadPending = true,
            fromRankReadPending = true,
            toFileReadPending = true,
            toRankReadPending = true,
        )
    }

    fun toUciString(): String = "$fromFile$fromRank$toFile$toRank"

    private fun advance(idx: Int?): Int = if (idx == null) 0 else (idx + 1) % 8

    private fun fileToIndex(file: Char): Int {
        val idx = file.lowercaseChar() - 'a'
        require(idx in 0..7) { "file out of range: $file" }
        return idx
    }

    private fun rankToIndex(rank: Char): Int {
        val idx = rank.digitToIntOrNull()?.minus(1)
        require(idx != null && idx in 0..7) { "rank out of range: $rank" }
        return idx
    }

    companion object {
        val DEFAULT: MoveBuffer = MoveBuffer()
    }
}
