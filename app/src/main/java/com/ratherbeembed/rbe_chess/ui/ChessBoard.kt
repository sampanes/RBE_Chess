package com.ratherbeembed.rbe_chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ratherbeembed.rbe_chess.chess.BoardProjector
import com.ratherbeembed.rbe_chess.chess.BoardSnapshot
import com.ratherbeembed.rbe_chess.chess.BoardSquare
import com.ratherbeembed.rbe_chess.chess.ChessPiece
import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.chess.PieceType

private val LightSquare = Color(0xFFE8D7B9)
private val DarkSquare = Color(0xFF5E7F5B)
private val LastMoveFrom = Color(0xFFE5B94E)
private val LastMoveTo = Color(0xFF59A96A)
private val GridLine = Color(0x66000000)

@Composable
fun ChessBoard(
    history: MoveHistory,
    bottomSide: ChessSide,
    modifier: Modifier = Modifier,
) {
    val snapshot = remember(history) { BoardProjector.fromHistory(history) }
    val files = if (bottomSide == ChessSide.WHITE) 0..7 else 7 downTo 0
    val ranks = if (bottomSide == ChessSide.WHITE) 7 downTo 0 else 0..7

    Column(
        modifier = modifier.widthIn(max = 380.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (rank in ranks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoardLabel(
                    text = (rank + 1).toString(),
                    modifier = Modifier.width(24.dp),
                )
                for (file in files) {
                    BoardSquareView(
                        square = BoardSquare(file, rank),
                        snapshot = snapshot,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(24.dp))
            for (file in files) {
                BoardLabel(
                    text = ('a' + file).toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        snapshot.lastMove?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Last move: ${it.uci}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BoardSquareView(
    square: BoardSquare,
    snapshot: BoardSnapshot,
    modifier: Modifier = Modifier,
) {
    val isDark = (square.file + square.rank) % 2 == 0
    val baseColor = if (isDark) DarkSquare else LightSquare
    val color = when (square) {
        snapshot.lastMove?.from -> LastMoveFrom
        snapshot.lastMove?.to -> LastMoveTo
        else -> baseColor
    }
    val piece = snapshot.pieceAt(square.file, square.rank)
    Box(
        modifier = modifier
            .background(color)
            .border(0.5.dp, GridLine),
        contentAlignment = Alignment.Center,
    ) {
        if (piece != null) {
            Text(
                text = piece.boardSymbol(),
                style = MaterialTheme.typography.headlineSmall,
                color = pieceTextColor(piece.side),
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BoardLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ChessPiece.boardSymbol(): String {
    return when (type) {
        PieceType.KING -> "♚"
        PieceType.QUEEN -> "♛"
        PieceType.ROOK -> "♜"
        PieceType.BISHOP -> "♝"
        PieceType.KNIGHT -> "♞"
        PieceType.PAWN -> "♟"
    }
}

private fun pieceTextColor(side: ChessSide): Color =
    if (side == ChessSide.WHITE) {
        Color.White
    } else {
        Color.Black
    }
