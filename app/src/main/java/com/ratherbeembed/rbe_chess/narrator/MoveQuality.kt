package com.ratherbeembed.rbe_chess.narrator

import kotlin.math.exp

enum class MoveQuality(val label: String, val spokenAtL1ForSelf: Boolean, val spokenAtL1ForOpponent: Boolean) {
    BEST("best move", false, false),
    ACCURATE("accurate", false, false),
    FINE("fine", false, false),
    INACCURACY("inaccuracy", true, false),
    MISTAKE("mistake", true, false),
    BLUNDER("blunder", true, true),
    ;

    val needsMagnitude: Boolean get() = this >= INACCURACY

    companion object {
        /**
         * Centipawn-loss buckets (narrative-sidekick SPEC section 8). The
         * app can retune these without touching the grammar.
         */
        fun classify(facts: QualityFacts): MoveQuality =
            when {
                facts.missedMate || facts.allowedMate -> BLUNDER
                facts.isBest -> BEST
                facts.cpLoss < 20 -> ACCURATE
                facts.cpLoss < 50 -> FINE
                facts.cpLoss < 120 -> INACCURACY
                facts.cpLoss <= 250 -> MISTAKE
                else -> BLUNDER
            }
    }
}

/**
 * Lichess win-probability model (SPEC 8.2): turns a centipawn eval into
 * a 0..100 Win% so quality and saliency gate on practical impact rather
 * than raw centipawns.
 */
object WinPercent {
    private const val K = 0.00368208

    fun of(cp: Int): Double = 50.0 + 50.0 * (2.0 / (1.0 + exp(-K * cp)) - 1.0)

    /** Win% buckets from SPEC 8.2: >=20 blunder, 10-20 mistake, 5-10 inaccuracy. */
    fun classifyDrop(winBest: Double, winPlayed: Double, isBest: Boolean = false): MoveQuality {
        val drop = winBest - winPlayed
        return when {
            isBest -> MoveQuality.BEST
            drop >= 20.0 -> MoveQuality.BLUNDER
            drop >= 10.0 -> MoveQuality.MISTAKE
            drop >= 5.0 -> MoveQuality.INACCURACY
            drop >= 2.0 -> MoveQuality.FINE
            else -> MoveQuality.ACCURATE
        }
    }

    /** Default saliency gate for sequence narration (SPEC 8.2, 14.1). */
    const val SALIENCY_GATE = 7.5
}
