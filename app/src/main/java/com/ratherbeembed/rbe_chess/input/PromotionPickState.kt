package com.ratherbeembed.rbe_chess.input

enum class PromotionPiece(
    val suffix: Char,
    val pieceName: String,
) {
    QUEEN('q', "queen"),
    ROOK('r', "rook"),
    BISHOP('b', "bishop"),
    KNIGHT('n', "knight"),
}

data class PromotionPickState(
    val baseMove: String,
    val legalPieces: Set<PromotionPiece>,
) {
    init {
        require(baseMove.length == 4) { "expected 4-char UCI base move, got '$baseMove'" }
        require(legalPieces.isNotEmpty()) { "promotion pick state needs at least one legal piece" }
    }

    fun choose(key: ChessKey): String? {
        val piece = pieceForKey(key) ?: return null
        return if (piece in legalPieces) "$baseMove${piece.suffix}" else null
    }

    fun legalPieceNames(): String =
        PIECE_ORDER
            .filter { it in legalPieces }
            .joinToString("/") { it.pieceName }

    companion object {
        private val PIECE_ORDER = listOf(
            PromotionPiece.QUEEN,
            PromotionPiece.ROOK,
            PromotionPiece.BISHOP,
            PromotionPiece.KNIGHT,
        )

        fun fromLegalMoves(baseMove: String, legalMoves: Set<String>): PromotionPickState? {
            if (baseMove.length != 4) return null
            val legalPieces = legalMoves
                .mapNotNull { legalMove ->
                    val suffix = legalMove.getOrNull(4)?.lowercaseChar()
                    if (legalMove.length == 5 && legalMove.startsWith(baseMove)) {
                        pieceForSuffix(suffix)
                    } else {
                        null
                    }
                }
                .toSet()
            return legalPieces
                .takeIf { it.isNotEmpty() }
                ?.let { PromotionPickState(baseMove, it) }
        }

        fun pieceForKey(key: ChessKey): PromotionPiece? =
            when (key) {
                ChessKey.D -> PromotionPiece.KNIGHT
                ChessKey.F -> PromotionPiece.BISHOP
                ChessKey.J -> PromotionPiece.ROOK
                ChessKey.K,
                ChessKey.SPACE -> PromotionPiece.QUEEN
                ChessKey.UNDO,
                ChessKey.TOGGLE_MANUAL,
                ChessKey.REPEAT_LAST,
                ChessKey.NEW_GAME,
                ChessKey.IGNORED -> null
            }

        private fun pieceForSuffix(suffix: Char?): PromotionPiece? =
            when (suffix) {
                PromotionPiece.QUEEN.suffix -> PromotionPiece.QUEEN
                PromotionPiece.ROOK.suffix -> PromotionPiece.ROOK
                PromotionPiece.BISHOP.suffix -> PromotionPiece.BISHOP
                PromotionPiece.KNIGHT.suffix -> PromotionPiece.KNIGHT
                else -> null
            }
    }
}
