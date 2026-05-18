package com.ratherbeembed.rbe_chess.narrative

import com.ratherbeembed.rbe_chess.chess.BoardProjector
import com.ratherbeembed.rbe_chess.chess.BoardSnapshot
import com.ratherbeembed.rbe_chess.chess.BoardSquare
import com.ratherbeembed.rbe_chess.chess.ChessPiece
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.chess.PieceType
import com.ratherbeembed.rbe_chess.speech.SpokenMoveFormatter
import kotlin.math.abs

object MoveNarrative {

    fun latestFromHistory(history: MoveHistory): String? {
        val move = history.moves.lastOrNull() ?: return null
        return forMove(
            historyBefore = MoveHistory(history.moves.dropLast(1)),
            move = move,
        )
    }

    fun forMove(
        historyBefore: MoveHistory,
        move: String,
        wasForced: Boolean = false,
        onlyReply: String? = null,
        emotionalPrefix: String? = null,
    ): String? {
        val fact = moveFact(historyBefore, move) ?: return null
        val previousFact = previousMoveFact(historyBefore)
        val movePhrase = movePhrase(fact, previousFact)
        val parts = buildList {
            emotionalPrefix
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
            if (wasForced) add("Forced.")
            movePhrase?.let(::add)
            onlyReply?.let {
                add("Only reply: ${SpokenMoveFormatter.spokenUciMove(it)}.")
            }
        }
        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun previousMoveFact(historyBefore: MoveHistory): MoveFact? {
        val previousMove = historyBefore.moves.lastOrNull() ?: return null
        val previousHistory = MoveHistory(historyBefore.moves.dropLast(1))
        return moveFact(previousHistory, previousMove)
    }

    private fun movePhrase(fact: MoveFact, previousFact: MoveFact?): String? =
        when {
            fact.isCastle -> "Castles."
            fact.promotion != null -> promotionPhrase(fact.promotion)
            fact.captured != null &&
                previousFact?.captured != null &&
                previousFact.to == fact.to -> tradePhrase(fact, previousFact)
            fact.captured != null -> "Takes ${pieceName(fact.captured.type)} on ${squareName(fact.to)}."
            else -> null
        }

    private fun promotionPhrase(promotion: Char): String =
        when (promotion.lowercaseChar()) {
            'q' -> "Queens."
            'r' -> "Promotes to rook."
            'b' -> "Promotes to bishop."
            'n' -> "Promotes to knight."
            else -> "Promotes."
        }

    private fun tradePhrase(fact: MoveFact, previousFact: MoveFact): String {
        val tradedType =
            if (fact.captured?.type == previousFact.captured?.type) {
                fact.captured?.type
            } else {
                null
            }
        return if (tradedType != null) {
            "Trades ${piecePlural(tradedType)} on ${squareName(fact.to)}."
        } else {
            "Trades on ${squareName(fact.to)}."
        }
    }

    private fun moveFact(historyBefore: MoveHistory, move: String): MoveFact? {
        if (move.length !in 4..5) return null
        val from = BoardSquare.fromUci(move.substring(0, 2)) ?: return null
        val to = BoardSquare.fromUci(move.substring(2, 4)) ?: return null
        val boardBefore = BoardProjector.fromHistory(historyBefore)
        val moved = boardBefore.pieceAt(from) ?: return null
        val destinationCapture = boardBefore.pieceAt(to)
            ?.takeIf { it.side != moved.side }
        val enPassantCapture =
            if (
                moved.type == PieceType.PAWN &&
                from.file != to.file &&
                destinationCapture == null
            ) {
                boardBefore.pieceAt(BoardSquare(to.file, from.rank))
                    ?.takeIf { it.side != moved.side && it.type == PieceType.PAWN }
            } else {
                null
            }
        return MoveFact(
            move = move,
            moved = moved,
            captured = destinationCapture ?: enPassantCapture,
            from = from,
            to = to,
            promotion = move.getOrNull(4)?.lowercaseChar(),
            isCastle = moved.type == PieceType.KING && abs(to.file - from.file) == 2,
        )
    }

    private fun BoardSnapshot.pieceAt(square: BoardSquare): ChessPiece? =
        pieceAt(square.file, square.rank)

    private fun squareName(square: BoardSquare): String =
        SpokenMoveFormatter.spokenSquare('a' + square.file, square.rank + 1)

    private fun pieceName(type: PieceType): String =
        when (type) {
            PieceType.KING -> "king"
            PieceType.QUEEN -> "queen"
            PieceType.ROOK -> "rook"
            PieceType.BISHOP -> "bishop"
            PieceType.KNIGHT -> "knight"
            PieceType.PAWN -> "pawn"
        }

    private fun piecePlural(type: PieceType): String =
        when (type) {
            PieceType.KING -> "kings"
            PieceType.QUEEN -> "queens"
            PieceType.ROOK -> "rooks"
            PieceType.BISHOP -> "bishops"
            PieceType.KNIGHT -> "knights"
            PieceType.PAWN -> "pawns"
        }

    private data class MoveFact(
        val move: String,
        val moved: ChessPiece,
        val captured: ChessPiece?,
        val from: BoardSquare,
        val to: BoardSquare,
        val promotion: Char?,
        val isCastle: Boolean,
    )
}
