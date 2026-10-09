package com.ratherbeembed.rbe_chess.narrator

import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.PieceType

/**
 * Input events for [Narrator.narrate], per narrative-sidekick SPEC
 * section 10. Plain data: the app layer computes SAN, evals, quality and
 * motifs; the narrator only phrases them.
 */
sealed interface NarrationEvent

enum class EntryField { FROM, TO }

/** Live feedback while a coordinate cycles (L0). [square] is UCI, e.g. "e2". */
data class EntryEvent(val field: EntryField, val square: String) : NarrationEvent

/**
 * A submitted move. [self] gates L1 quality commentary (SPEC 8.1): self
 * moves flag inaccuracy and worse, opponent moves flag blunders only.
 */
data class MoveEvent(
    val side: ChessSide,
    val san: String,
    val self: Boolean = true,
    val quality: QualityFacts? = null,
    val motifs: List<Motif> = emptyList(),
) : NarrationEvent

/** Centipawn loss versus the engine's best move, from the mover's view. */
data class QualityFacts(
    val cpLoss: Int,
    val isBest: Boolean = false,
    val missedMate: Boolean = false,
    val allowedMate: Boolean = false,
)

/** Engine score from the side-to-move's view: [cp] or [mate] (negative = getting mated). */
data class Eval(val cp: Int? = null, val mate: Int? = null)

data class Alternative(val san: String, val eval: Eval)

/** Win / draw / loss per thousand, side to move. */
data class Wdl(val win: Int, val draw: Int, val loss: Int)

/** Stockfish's favored move for [side], with optional PV [line] (SAN plies after it). */
data class EngineEvent(
    val side: ChessSide,
    val san: String,
    val eval: Eval,
    val line: List<String> = emptyList(),
    val alt: Alternative? = null,
    val wdl: Wdl? = null,
) : NarrationEvent

enum class SystemAction { ENDGAME, RESTART, UNDO, STATUS }

enum class GameResult { WHITE, BLACK, DRAW }

data class RevertedMove(val side: ChessSide, val san: String)

data class SystemEvent(
    val action: SystemAction,
    val result: GameResult? = null,
    val reverted: RevertedMove? = null,
    val sideToMove: ChessSide? = null,
    val eval: Eval? = null,
    val moveNumber: Int? = null,
) : NarrationEvent

enum class MotifTier { CHEAP, MODERATE, HARD }

enum class MotifKind(val tier: MotifTier) {
    FORK(MotifTier.CHEAP),
    PIN(MotifTier.CHEAP),
    HANGING(MotifTier.CHEAP),
    TRADE(MotifTier.MODERATE),
    WINS_MATERIAL(MotifTier.MODERATE),
    SACRIFICE(MotifTier.HARD),
}

/**
 * A board-derived fact about a move (SPEC section 13). [targets] are the
 * pieces involved, for phrasing; [material] is net pawns for trades and
 * material wins. The narrator never speaks a motif that is not
 * [confident] (SPEC 13.4: silence beats a wrong "fork!").
 */
data class Motif(
    val kind: MotifKind,
    val targets: List<PieceType> = emptyList(),
    val material: Int? = null,
    val confident: Boolean = true,
)
