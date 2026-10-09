package com.ratherbeembed.rbe_chess.chess

import kotlin.math.abs

/**
 * UCI -> Standard Algebraic Notation, with minimal disambiguation and
 * check/mate suffixes. The narration layer speaks SAN (SPEC section 7 of
 * narrative-sidekick), so it needs a real SAN source; Stockfish only
 * speaks UCI.
 */
internal object San {

    /** SAN for [uci] in [position], or null if the move is not legal there. */
    fun fromUci(position: Position, uci: String): String? {
        val move = ChessMove.fromUci(uci) ?: return null
        return if (position.isLegal(move)) of(position, move) else null
    }

    /** SAN for a move already known to be legal in [position]. */
    fun of(position: Position, move: ChessMove): String {
        val piece = requireNotNull(position.pieceAt(move.from)) { "no piece on ${move.from.name}" }
        val core = if (piece.type == PieceType.KING && abs(move.to.file - move.from.file) == 2) {
            if (move.to.file > move.from.file) "O-O" else "O-O-O"
        } else {
            pieceMove(position, move, piece)
        }
        val after = position.play(move)
        val suffix = when {
            after.isCheckmate() -> "#"
            after.inCheck() -> "+"
            else -> ""
        }
        return core + suffix
    }

    /** SAN for each move of [history], or null if any move is illegal. */
    fun forHistory(history: MoveHistory): List<String>? {
        var position = Position.START
        val out = mutableListOf<String>()
        for (uci in history.moves) {
            out += fromUci(position, uci) ?: return null
            position = position.play(requireNotNull(ChessMove.fromUci(uci)))
        }
        return out
    }

    private fun pieceMove(position: Position, move: ChessMove, piece: ChessPiece): String {
        val isCapture = position.pieceAt(move.to) != null ||
            (piece.type == PieceType.PAWN && move.from.file != move.to.file)
        return buildString {
            if (piece.type == PieceType.PAWN) {
                if (isCapture) append('a' + move.from.file)
            } else {
                append(letter(piece.type))
                append(disambiguation(position, move, piece))
            }
            if (isCapture) append('x')
            append(move.to.name)
            move.promotion?.let { append('=').append(letter(it)) }
        }
    }

    private fun disambiguation(position: Position, move: ChessMove, piece: ChessPiece): String {
        val rivals = position.legalMoves()
            .filter { it.to == move.to && it.from != move.from && position.pieceAt(it.from) == piece }
            .map { it.from }
        if (rivals.isEmpty()) return ""
        val from = move.from
        return when {
            rivals.none { it.file == from.file } -> "${'a' + from.file}"
            rivals.none { it.rank == from.rank } -> "${from.rank + 1}"
            else -> from.name
        }
    }

    fun letter(type: PieceType): Char =
        when (type) {
            PieceType.KING -> 'K'
            PieceType.QUEEN -> 'Q'
            PieceType.ROOK -> 'R'
            PieceType.BISHOP -> 'B'
            PieceType.KNIGHT -> 'N'
            PieceType.PAWN -> 'P'
        }
}
