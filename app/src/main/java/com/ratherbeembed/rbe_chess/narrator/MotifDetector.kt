package com.ratherbeembed.rbe_chess.narrator

import com.ratherbeembed.rbe_chess.chess.BoardSquare
import com.ratherbeembed.rbe_chess.chess.ChessMove
import com.ratherbeembed.rbe_chess.chess.ChessPiece
import com.ratherbeembed.rbe_chess.chess.PieceType
import com.ratherbeembed.rbe_chess.chess.Position
import com.ratherbeembed.rbe_chess.chess.opponent
import kotlin.math.abs

/**
 * v1 motif detectors (narrative-sidekick SPEC 13.1 cheap tier, plus an
 * even-trade check). Every detector is certain by construction or stays
 * silent: a fork is two or more attacked valuables, a pin is a piece
 * that may not leave the line to its king. Ranked most salient first.
 */
internal object MotifDetector {

    fun detect(before: Position, move: ChessMove): List<Motif> {
        val moved = before.pieceAt(move.from) ?: return emptyList()
        val after = before.play(move)
        val placed = after.pieceAt(move.to) ?: return emptyList()
        val captured = capturedPiece(before, move, moved)
        return listOfNotNull(
            fork(after, move.to, placed),
            pin(before, after, move.to, placed),
            trade(after, move.to, placed, captured),
            hanging(after, move.to, placed, captured),
        )
    }

    /** The moved piece attacks two or more valuable enemy pieces (SPEC 13.1). */
    private fun fork(after: Position, square: BoardSquare, piece: ChessPiece): Motif? {
        val enemy = piece.side.opponent()
        val targets = after.attacksFrom(square)
            .mapNotNull { sq -> after.pieceAt(sq)?.takeIf { it.side == enemy }?.let { sq to it } }
            .filter { (sq, target) -> isValuableTarget(after, sq, target, piece) }
            .map { it.second.type }
        if (targets.size < 2) return null
        return Motif(MotifKind.FORK, targets.sortedByDescending(::value))
    }

    /**
     * Valuable = the king (the fork gives check), or a non-pawn worth more
     * than the attacker or left undefended. Pawns never make a fork worth
     * announcing.
     */
    private fun isValuableTarget(
        after: Position,
        square: BoardSquare,
        target: ChessPiece,
        attacker: ChessPiece,
    ): Boolean = when (target.type) {
        PieceType.KING -> true
        PieceType.PAWN -> false
        else -> value(target.type) > value(attacker.type) ||
            after.attackers(square, target.side).isEmpty()
    }

    /**
     * The moved slider now pins an enemy piece to its king, and that pin
     * did not exist before the move. Pins the pinned piece can break by
     * capturing the pinner are not announced.
     */
    private fun pin(before: Position, after: Position, square: BoardSquare, piece: ChessPiece): Motif? {
        val directions = when (piece.type) {
            PieceType.BISHOP -> DIAGONALS
            PieceType.ROOK -> ORTHOGONALS
            PieceType.QUEEN -> DIAGONALS + ORTHOGONALS
            else -> return null
        }
        val enemy = piece.side.opponent()
        for ((df, dr) in directions) {
            val ray = walk(square, df, dr)
            val firstIndex = ray.indexOfFirst { after.pieceAt(it) != null }
            if (firstIndex < 0) continue
            val pinnedSquare = ray[firstIndex]
            val pinned = after.pieceAt(pinnedSquare) ?: continue
            if (pinned.side != enemy || pinned.type == PieceType.KING) continue
            val behind = ray.drop(firstIndex + 1).firstOrNull { after.pieceAt(it) != null } ?: continue
            if (after.pieceAt(behind) != ChessPiece(enemy, PieceType.KING)) continue
            if (canCapture(after, pinnedSquare, square)) continue
            if (wasPinned(before, pinnedSquare)) continue
            return Motif(MotifKind.PIN, listOf(pinned.type))
        }
        return null
    }

    /** Captured an equal-value piece and can be recaptured: an even trade. */
    private fun trade(after: Position, square: BoardSquare, piece: ChessPiece, captured: ChessPiece?): Motif? {
        if (captured == null || piece.type == PieceType.KING) return null
        if (value(captured.type) != value(piece.type)) return null
        if (legalCapturersOf(after, square).isEmpty()) return null
        return Motif(MotifKind.TRADE, listOf(captured.type), material = 0)
    }

    /**
     * The moved piece can be taken for free or by something cheaper.
     * Captures that are already a fair trade or better are excluded --
     * recapture there is the expected trade, not a hung piece.
     */
    private fun hanging(after: Position, square: BoardSquare, piece: ChessPiece, captured: ChessPiece?): Motif? {
        if (piece.type == PieceType.KING) return null
        if (captured != null && value(captured.type) >= value(piece.type)) return null
        val capturers = legalCapturersOf(after, square)
        if (capturers.isEmpty()) return null
        val defended = after.attackers(square, piece.side).isNotEmpty()
        val cheapest = capturers.minOf { value(it.type) }
        if (defended && cheapest >= value(piece.type)) return null
        return Motif(MotifKind.HANGING, listOf(piece.type))
    }

    private fun legalCapturersOf(after: Position, square: BoardSquare): List<ChessPiece> =
        after.legalMoves().filter { it.to == square }.mapNotNull { after.pieceAt(it.from) }.distinct()

    private fun canCapture(after: Position, from: BoardSquare, target: BoardSquare): Boolean =
        after.attacksFrom(from).contains(target)

    /** True if the piece on [square] was already absolutely pinned in [position]. */
    private fun wasPinned(position: Position, square: BoardSquare): Boolean {
        val piece = position.pieceAt(square) ?: return false
        val king = position.kingSquare(piece.side) ?: return false
        val df = Integer.signum(square.file - king.file)
        val dr = Integer.signum(square.rank - king.rank)
        val aligned = (square.file - king.file == 0) || (square.rank - king.rank == 0) ||
            abs(square.file - king.file) == abs(square.rank - king.rank)
        if (!aligned) return false
        val ray = walk(king, df, dr)
        val first = ray.firstOrNull { position.pieceAt(it) != null } ?: return false
        if (first != square) return false
        val pinner = ray.dropWhile { it != square }.drop(1)
            .firstOrNull { position.pieceAt(it) != null }
            ?.let(position::pieceAt) ?: return false
        if (pinner.side == piece.side) return false
        val diagonal = df != 0 && dr != 0
        return pinner.type == PieceType.QUEEN ||
            (diagonal && pinner.type == PieceType.BISHOP) ||
            (!diagonal && pinner.type == PieceType.ROOK)
    }

    private fun capturedPiece(before: Position, move: ChessMove, moved: ChessPiece): ChessPiece? {
        before.pieceAt(move.to)?.let { return it }
        val enPassant = moved.type == PieceType.PAWN && move.from.file != move.to.file
        return if (enPassant) before.pieceAt(BoardSquare(move.to.file, move.from.rank)) else null
    }

    private fun walk(from: BoardSquare, df: Int, dr: Int): List<BoardSquare> {
        val out = mutableListOf<BoardSquare>()
        var f = from.file + df
        var r = from.rank + dr
        while (f in 0..7 && r in 0..7) {
            out += BoardSquare(f, r)
            f += df
            r += dr
        }
        return out
    }

    /** Conventional piece values in pawns; the king outranks everything. */
    fun value(type: PieceType): Int =
        when (type) {
            PieceType.PAWN -> 1
            PieceType.KNIGHT, PieceType.BISHOP -> 3
            PieceType.ROOK -> 5
            PieceType.QUEEN -> 9
            PieceType.KING -> 100
        }

    private val ORTHOGONALS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val DIAGONALS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
}
