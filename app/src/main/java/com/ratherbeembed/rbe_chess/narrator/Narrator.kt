package com.ratherbeembed.rbe_chess.narrator

import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.PieceType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The narrative-sidekick narrator: `narrate(event, level) -> string`
 * (SPEC section 10). Pure -- no I/O, clock, RNG or legality -- so the same
 * input and level always give byte-identical output, and the golden
 * catalog in narrative-sidekick/examples doubles as its test fixture.
 *
 * Levels: 0 = live entry, 1 = first pass after submit, 2 and 3 = each
 * further `repeat` (SPEC section 5). Levels above 3 read as 3.
 */
class Narrator(private val phonetic: Boolean = false) {

    fun narrate(event: NarrationEvent, level: Int): String {
        val lvl = level.coerceIn(0, 3)
        return when (event) {
            is EntryEvent -> entry(event)
            is MoveEvent -> move(event, maxOf(lvl, 1))
            is EngineEvent -> engine(event, maxOf(lvl, 1))
            is SystemEvent -> system(event)
        }
    }

    private fun entry(event: EntryEvent): String {
        val field = if (event.field == EntryField.FROM) "from" else "to"
        return "$field ${SpokenSan.square(event.square, phonetic)}"
    }

    private fun move(event: MoveEvent, level: Int): String {
        val spoken = SpokenSan.expand(event.san, pawnWord = level >= 2, phonetic = phonetic)
        val head = buildString {
            append(sideWord(event.side)).append(' ').append(spoken)
            headlineMotif(event)?.let { append(", ").append(motifPhrase(it)) }
        }
        val quality = event.quality?.let(MoveQuality::classify)
        if (level == 1) {
            val flag = quality?.takeIf {
                if (event.self) it.spokenAtL1ForSelf else it.spokenAtL1ForOpponent
            }
            return if (flag != null) "$head, ${flag.label}." else "$head."
        }
        val sentences = mutableListOf(head)
        val headline = headlineMotif(event)
        val extra = event.motifs.filter { it.confident && it != headline }
        val shown = if (level >= 3) extra else extra.take(1)
        shown.forEach { sentences += motifPhrase(it) }
        if (quality != null) sentences += qualityFull(quality, event.quality)
        return sentences.joinToString(". ") + "."
    }

    /** At most one cheap-tier, confident fork or pin rides along at L1 (SPEC 13.3). */
    private fun headlineMotif(event: MoveEvent): Motif? =
        event.motifs.firstOrNull {
            it.confident && it.kind.tier == MotifTier.CHEAP &&
                (it.kind == MotifKind.FORK || it.kind == MotifKind.PIN)
        }

    private fun motifPhrase(motif: Motif): String {
        val names = motif.targets.map { SpokenSan.pieceWord(letterOf(it)) }
        return when (motif.kind) {
            MotifKind.FORK -> "forking ${joinAnd(names)}"
            MotifKind.PIN -> "pinning the ${names.firstOrNull() ?: "piece"}"
            MotifKind.HANGING -> "the ${names.firstOrNull() ?: "piece"} is hanging"
            MotifKind.TRADE -> "trade, " + materialPhrase(motif.material ?: 0, even = "even")
            MotifKind.WINS_MATERIAL -> "wins ${pawnCount(motif.material ?: 1)}"
            MotifKind.SACRIFICE -> "sacrifice"
        }
    }

    private fun materialPhrase(pawns: Int, even: String): String =
        when {
            pawns == 0 -> even
            pawns > 0 -> "wins ${pawnCount(pawns)}"
            else -> "loses ${pawnCount(-pawns)}"
        }

    private fun pawnCount(n: Int): String = if (n == 1) "a pawn" else "$n pawns"

    private fun qualityFull(quality: MoveQuality, facts: QualityFacts): String =
        when {
            facts.missedMate -> "${quality.label}, missed mate"
            facts.allowedMate -> "${quality.label}, allowed mate"
            quality.needsMagnitude -> "${quality.label}, lost about ${lossMagnitude(facts.cpLoss)}"
            else -> quality.label
        }

    /** SPEC 8: speak fractions under a pawn, else whole or half pawns. */
    private fun lossMagnitude(cpLoss: Int): String =
        when {
            cpLoss < 20 -> "a tenth of a pawn"
            cpLoss < 45 -> "a third of a pawn"
            cpLoss < 100 -> "half a pawn"
            cpLoss < 150 -> "a pawn"
            else -> {
                val halves = (cpLoss + 25) / 50
                val text = if (halves % 2 == 0) "${halves / 2}" else "${halves / 2}.5"
                "$text pawns"
            }
        }

    private fun engine(event: EngineEvent, level: Int): String {
        val move = SpokenSan.expand(event.san, phonetic = phonetic)
        if (level == 1) return "engine: $move."
        val head = "engine: $move, ${evalPhrase(event.eval)}."
        if (level == 2) return head
        val parts = mutableListOf(head)
        if (event.line.isNotEmpty()) {
            parts += "then " + event.line.take(2).joinToString(", then ") {
                SpokenSan.expand(it, phonetic = phonetic)
            } + "."
        }
        event.alt?.let {
            parts += "or ${SpokenSan.expand(it.san, phonetic = phonetic)}, ${evalPhrase(it.eval)}."
        }
        event.wdl?.let(::wdlPhrase)?.let { parts += "$it." }
        return parts.joinToString(" ")
    }

    /** SPEC 9.1/9.2: signed pawns from the side to move's view, "level" near zero, mate words. */
    fun evalPhrase(eval: Eval): String {
        eval.mate?.let { mate ->
            return when {
                mate > 0 -> "mate in ${SpokenSan.number(mate)}"
                mate < 0 -> "getting mated in ${SpokenSan.number(-mate)}"
                else -> "checkmate"
            }
        }
        val cp = eval.cp ?: return "level"
        if (abs(cp) < LEVEL_CP) return "level"
        val tenths = (abs(cp) + 5) / 10
        val sign = if (cp > 0) "plus" else "minus"
        return "$sign ${tenths / 10}.${tenths % 10}"
    }

    /** SPEC 9.4: only when one outcome dominates (>= 800 per thousand). */
    private fun wdlPhrase(wdl: Wdl): String? {
        val (word, share) = listOf(
            "winning" to wdl.win,
            "drawn" to wdl.draw,
            "losing" to wdl.loss,
        ).maxBy { it.second }
        if (share < WDL_DECISIVE) return null
        val fifths = (share / 200.0).roundToInt()
        return if (fifths >= 5) {
            "$word nearly every time"
        } else {
            "$word ${SpokenSan.number(fifths)} times out of five"
        }
    }

    private fun system(event: SystemEvent): String =
        when (event.action) {
            SystemAction.ENDGAME -> when (event.result) {
                null -> "game over."
                GameResult.WHITE -> "game over, white wins."
                GameResult.BLACK -> "game over, black wins."
                GameResult.DRAW -> "game over, draw."
            }
            SystemAction.RESTART -> "new game."
            SystemAction.UNDO -> event.reverted?.let {
                "took back ${sideWord(it.side)} ${SpokenSan.expand(it.san, phonetic = phonetic)}."
            } ?: "undo."
            SystemAction.STATUS -> buildList {
                event.sideToMove?.let { add("${sideWord(it)} to move") }
                event.eval?.let { add(evalPhrase(it)) }
                event.moveNumber?.let { add("move $it") }
            }.joinToString(", ").ifEmpty { "no game" } + "."
        }

    private fun sideWord(side: ChessSide): String = if (side == ChessSide.WHITE) "white" else "black"

    private fun joinAnd(words: List<String>): String =
        when (words.size) {
            0 -> "pieces"
            1 -> words[0]
            else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
        }

    private fun letterOf(type: PieceType): Char =
        when (type) {
            PieceType.KING -> 'K'
            PieceType.QUEEN -> 'Q'
            PieceType.ROOK -> 'R'
            PieceType.BISHOP -> 'B'
            PieceType.KNIGHT -> 'N'
            PieceType.PAWN -> 'P'
        }

    private companion object {
        const val LEVEL_CP = 10
        const val WDL_DECISIVE = 800
    }
}
