package com.ratherbeembed.rbe_chess.input

/**
 * The four under-construction coordinates of a from-to move. Each index is
 * null until the user first presses its cycle button, then 0..7. Display
 * treats null as 0 (so an untouched coord shows as 'a' or '1' in the
 * inactivity prompt), but pressing distinguishes the two so the first press
 * lands on 'a' / '1' instead of advancing past it.
 */
data class MoveBuffer(
    val fromFileIdx: Int? = null,
    val fromRankIdx: Int? = null,
    val toFileIdx: Int? = null,
    val toRankIdx: Int? = null,
) {
    val fromFile: Char get() = 'a' + (fromFileIdx ?: 0)
    val fromRank: Int get() = (fromRankIdx ?: 0) + 1
    val toFile: Char get() = 'a' + (toFileIdx ?: 0)
    val toRank: Int get() = (toRankIdx ?: 0) + 1

    fun cycleFromFile(): MoveBuffer = copy(fromFileIdx = advance(fromFileIdx))
    fun cycleFromRank(): MoveBuffer = copy(fromRankIdx = advance(fromRankIdx))
    fun cycleToFile(): MoveBuffer = copy(toFileIdx = advance(toFileIdx))
    fun cycleToRank(): MoveBuffer = copy(toRankIdx = advance(toRankIdx))

    fun toUciString(): String = "$fromFile$fromRank$toFile$toRank"

    private fun advance(idx: Int?): Int = if (idx == null) 0 else (idx + 1) % 8

    companion object {
        val DEFAULT: MoveBuffer = MoveBuffer()
    }
}
