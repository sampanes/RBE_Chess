package com.ratherbeembed.rbe_chess.chess

/**
 * Draws the app ends the game on without anyone having to claim:
 * FIDE's automatic rules (fivefold repetition, seventy-five-move rule)
 * plus dead positions where no mate is possible by any legal sequence.
 */
enum class AutomaticDraw {
    FIVEFOLD_REPETITION,
    SEVENTY_FIVE_MOVE_RULE,
    INSUFFICIENT_MATERIAL,
}

/**
 * Draws a player could claim over the board but that don't end the
 * game by themselves. The app announces these and keeps playing —
 * the opponent at the physical board may well not claim, and ending a
 * winning game on the user's behalf would be wrong.
 */
enum class ClaimableDraw {
    THREEFOLD_REPETITION,
    FIFTY_MOVE_RULE,
}

data class DrawStatus(
    val automatic: AutomaticDraw?,
    val claimable: ClaimableDraw?,
    val repetitionCount: Int,
    val halfmoveClock: Int,
)

/**
 * Detects draw conditions for the position at the end of a
 * [MoveHistory]. Pure Kotlin, no engine round-trip: replays the history
 * through [PositionReplay], counting FIDE position identities for
 * repetition and the halfmove clock for the move rules.
 *
 * Checkmate/stalemate detection stays with Stockfish upstream; callers
 * should give those precedence (a mating move trumps the 75-move rule
 * per FIDE 9.6.2).
 */
object DrawDetector {
    private const val THREEFOLD_COUNT = 3
    private const val FIVEFOLD_COUNT = 5
    private const val FIFTY_MOVE_PLIES = 100
    private const val SEVENTY_FIVE_MOVE_PLIES = 150

    fun statusFor(history: MoveHistory): DrawStatus {
        val replay = PositionReplay()
        val seen = mutableMapOf(replay.repetitionKey() to 1)
        for (uci in history.moves) {
            replay.apply(uci)
            val key = replay.repetitionKey()
            seen[key] = (seen[key] ?: 0) + 1
        }
        return classify(
            repetitionCount = seen[replay.repetitionKey()] ?: 1,
            halfmoveClock = replay.halfmoveClock,
            deadPosition = isInsufficientMaterial(replay.board),
        )
    }

    internal fun classify(
        repetitionCount: Int,
        halfmoveClock: Int,
        deadPosition: Boolean,
    ): DrawStatus {
        val automatic = when {
            deadPosition -> AutomaticDraw.INSUFFICIENT_MATERIAL
            repetitionCount >= FIVEFOLD_COUNT -> AutomaticDraw.FIVEFOLD_REPETITION
            halfmoveClock >= SEVENTY_FIVE_MOVE_PLIES -> AutomaticDraw.SEVENTY_FIVE_MOVE_RULE
            else -> null
        }
        val claimable = when {
            automatic != null -> null
            repetitionCount >= THREEFOLD_COUNT -> ClaimableDraw.THREEFOLD_REPETITION
            halfmoveClock >= FIFTY_MOVE_PLIES -> ClaimableDraw.FIFTY_MOVE_RULE
            else -> null
        }
        return DrawStatus(
            automatic = automatic,
            claimable = claimable,
            repetitionCount = repetitionCount,
            halfmoveClock = halfmoveClock,
        )
    }

    /**
     * Dead-position subset that is safe to call automatically:
     * K vs K, K+minor vs K, and bishops-only where every bishop (either
     * side) stands on the same square color. Knight-vs-knight and other
     * helpmate-possible endings are deliberately not included.
     */
    internal fun isInsufficientMaterial(board: Map<BoardSquare, ChessPiece>): Boolean {
        var knights = 0
        val bishopSquareColors = mutableSetOf<Int>()
        for ((square, piece) in board) {
            when (piece.type) {
                PieceType.KING -> Unit
                PieceType.KNIGHT -> knights += 1
                PieceType.BISHOP -> bishopSquareColors.add((square.file + square.rank) % 2)
                PieceType.PAWN,
                PieceType.ROOK,
                PieceType.QUEEN,
                -> return false
            }
        }
        val bishops = board.values.count { it.type == PieceType.BISHOP }
        return when {
            knights == 0 && bishops == 0 -> true
            knights == 1 && bishops == 0 -> true
            knights == 0 && bishopSquareColors.size == 1 -> true
            else -> false
        }
    }
}
