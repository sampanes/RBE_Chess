package com.ratherbeembed.rbe_chess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratherbeembed.rbe_chess.chess.BoardMove
import com.ratherbeembed.rbe_chess.chess.BoardProjector
import com.ratherbeembed.rbe_chess.chess.BoardSnapshot
import com.ratherbeembed.rbe_chess.chess.BoardSquare
import com.ratherbeembed.rbe_chess.chess.ChessPiece
import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.chess.PieceType
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val LightSquare = Color(0xFFEBD9BA)
private val DarkSquare = Color(0xFF577A5B)
private val BoardFrame = Color(0xFF1E2A22)
private val LastMoveFrom = Color(0xFFEAC45C)
private val LastMoveTo = Color(0xFF7CCB7A)
private val CurrentFrom = Color(0xFF7AA7FF)
private val CurrentTo = Color(0xFF9BE7FF)
private val PendingFrom = Color(0xFFFFB45C)
private val PendingTo = Color(0xFFFF7A59)
private val GridLine = Color(0x66000000)
private val LastMoveArrow = Color(0x99222A22)
private val CurrentMoveArrow = Color(0xCC0D47A1)
private val PendingMoveArrow = Color(0xFFE65100)

@Composable
fun ChessBoard(
    history: MoveHistory,
    bottomSide: ChessSide,
    modifier: Modifier = Modifier,
    buffer: MoveBuffer = MoveBuffer.DEFAULT,
    pendingMove: String? = null,
) {
    val snapshot = remember(history) { BoardProjector.fromHistory(history) }
    val files = remember(bottomSide) {
        if (bottomSide == ChessSide.WHITE) (0..7).toList() else (7 downTo 0).toList()
    }
    val ranks = remember(bottomSide) {
        if (bottomSide == ChessSide.WHITE) (7 downTo 0).toList() else (0..7).toList()
    }
    val selection = remember(buffer) { buffer.selection() }
    val pending = remember(pendingMove) { boardMoveFromUci(pendingMove) }

    Column(
        modifier = modifier.widthIn(max = 380.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(2.dp, BoardFrame),
        ) {
            BoardGrid(
                files = files,
                ranks = ranks,
                snapshot = snapshot,
                selection = selection,
                pending = pending,
                bottomSide = bottomSide,
                modifier = Modifier.fillMaxSize(),
            )
            BoardArrowOverlay(
                files = files,
                ranks = ranks,
                lastMove = snapshot.lastMove,
                currentMove = selection.move.takeIf { pending == null },
                pendingMove = pending,
                modifier = Modifier.fillMaxSize(),
            )
        }
        MoveFocusLine(selection = selection, pending = pending)
        MoveHistoryLine(history)
    }
}

@Composable
private fun BoardGrid(
    files: List<Int>,
    ranks: List<Int>,
    snapshot: BoardSnapshot,
    selection: BoardSelection,
    pending: BoardMove?,
    bottomSide: ChessSide,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        for (rank in ranks) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (file in files) {
                    val square = BoardSquare(file, rank)
                    BoardSquareView(
                        square = square,
                        snapshot = snapshot,
                        highlight = square.highlight(snapshot.lastMove, selection, pending),
                        showFileLabel = square.rank == bottomRank(bottomSide),
                        showRankLabel = square.file == leftFile(bottomSide),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardArrowOverlay(
    files: List<Int>,
    ranks: List<Int>,
    lastMove: BoardMove?,
    currentMove: BoardMove?,
    pendingMove: BoardMove?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        lastMove?.let {
            drawMoveArrow(it, files, ranks, LastMoveArrow, strokeWidth = 5f)
        }
        currentMove?.let {
            drawMoveArrow(it, files, ranks, CurrentMoveArrow, strokeWidth = 7f)
        }
        pendingMove?.let {
            drawMoveArrow(it, files, ranks, PendingMoveArrow, strokeWidth = 9f)
        }
    }
}

@Composable
private fun BoardSquareView(
    square: BoardSquare,
    snapshot: BoardSnapshot,
    highlight: SquareHighlight,
    showFileLabel: Boolean,
    showRankLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = (square.file + square.rank) % 2 == 0
    val baseColor = if (isDark) DarkSquare else LightSquare
    val color = when (highlight) {
        SquareHighlight.LAST_FROM -> LastMoveFrom
        SquareHighlight.LAST_TO -> LastMoveTo
        SquareHighlight.CURRENT_FROM -> CurrentFrom
        SquareHighlight.CURRENT_TO -> CurrentTo
        SquareHighlight.PENDING_FROM -> PendingFrom
        SquareHighlight.PENDING_TO -> PendingTo
        SquareHighlight.NONE -> baseColor
    }
    val borderColor = when (highlight) {
        SquareHighlight.NONE -> GridLine
        SquareHighlight.LAST_FROM,
        SquareHighlight.LAST_TO -> Color(0xCC1B5E20)
        SquareHighlight.CURRENT_FROM,
        SquareHighlight.CURRENT_TO -> Color(0xFF0D47A1)
        SquareHighlight.PENDING_FROM,
        SquareHighlight.PENDING_TO -> Color(0xFFBF360C)
    }
    val borderWidth = if (highlight == SquareHighlight.NONE) 0.5.dp else 2.dp
    val piece = snapshot.pieceAt(square.file, square.rank)

    Box(
        modifier = modifier
            .background(color)
            .border(borderWidth, borderColor),
        contentAlignment = Alignment.Center,
    ) {
        if (showRankLabel) {
            BoardCornerLabel(
                text = (square.rank + 1).toString(),
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        if (showFileLabel) {
            BoardCornerLabel(
                text = ('a' + square.file).toString(),
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        if (piece != null) {
            Text(
                text = piece.boardSymbol(),
                style = MaterialTheme.typography.headlineSmall,
                color = pieceTextColor(piece.side),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BoardCornerLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = Color(0xAA101010),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun MoveFocusLine(selection: BoardSelection, pending: BoardMove?) {
    val colors = MaterialTheme.colorScheme
    val text = when {
        pending != null -> "Pending: ${pending.uci}"
        selection.move != null -> "Input: ${selection.move.uci}"
        selection.from != null -> "Selected: ${selection.from.name}"
        else -> "Selected: none"
    }
    val color = when {
        pending != null -> PendingMoveArrow
        selection.move != null || selection.from != null -> CurrentMoveArrow
        else -> colors.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = if (pending != null) FontWeight.Bold else FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun MoveHistoryLine(history: MoveHistory) {
    val colors = MaterialTheme.colorScheme
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.onSurfaceVariant)) {
            append("History: ")
        }
        if (history.moves.isEmpty()) {
            withStyle(SpanStyle(color = colors.onSurfaceVariant.copy(alpha = 0.7f))) {
                append("empty")
            }
            return@buildAnnotatedString
        }

        history.moves.forEachIndexed { idx, move ->
            if (idx > 0) append("  ")
            if (idx % 2 == 0) {
                withStyle(
                    SpanStyle(color = colors.onSurfaceVariant.copy(alpha = 0.52f)),
                ) {
                    append("${idx / 2 + 1}. ")
                }
            }
            val isLast = idx == history.moves.lastIndex
            val moveColor =
                if (isLast) colors.onSurface
                else if (idx % 2 == 0) colors.onSurfaceVariant.copy(alpha = 0.68f)
                else colors.onSurfaceVariant.copy(alpha = 0.95f)
            withStyle(
                SpanStyle(
                    color = moveColor,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                ),
            ) {
                append(move)
            }
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        textAlign = TextAlign.Start,
    )
}

private enum class SquareHighlight {
    NONE,
    LAST_FROM,
    LAST_TO,
    CURRENT_FROM,
    CURRENT_TO,
    PENDING_FROM,
    PENDING_TO,
}

private data class BoardSelection(
    val from: BoardSquare?,
    val to: BoardSquare?,
    val move: BoardMove?,
)

private fun MoveBuffer.selection(): BoardSelection {
    // Treat null as 0 for visual feedback (Always-On highlight)
    val from = BoardSquare(fromFileIdx ?: 0, fromRankIdx ?: 0)
    val to = BoardSquare(toFileIdx ?: 0, toRankIdx ?: 0)
    val move =
        if (fromFileIdx != null && fromRankIdx != null && toFileIdx != null && toRankIdx != null) {
            BoardMove(uci = "${from.name}${to.name}", from = from, to = to)
        } else {
            null
        }
    return BoardSelection(from = from, to = to, move = move)
}

private fun BoardSquare.highlight(
    lastMove: BoardMove?,
    selection: BoardSelection,
    pending: BoardMove?,
): SquareHighlight =
    when (this) {
        pending?.to -> SquareHighlight.PENDING_TO
        pending?.from -> SquareHighlight.PENDING_FROM
        selection.to -> SquareHighlight.CURRENT_TO
        selection.from -> SquareHighlight.CURRENT_FROM
        lastMove?.to -> SquareHighlight.LAST_TO
        lastMove?.from -> SquareHighlight.LAST_FROM
        else -> SquareHighlight.NONE
    }

private fun boardMoveFromUci(uci: String?): BoardMove? {
    if (uci == null || uci.length < 4) return null
    val from = BoardSquare.fromUci(uci.substring(0, 2)) ?: return null
    val to = BoardSquare.fromUci(uci.substring(2, 4)) ?: return null
    return BoardMove(uci = uci.take(4), from = from, to = to)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMoveArrow(
    move: BoardMove,
    files: List<Int>,
    ranks: List<Int>,
    color: Color,
    strokeWidth: Float,
) {
    val start = move.from.center(files, ranks, size.width, size.height)
    val end = move.to.center(files, ranks, size.width, size.height)
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0f && dy == 0f) {
        drawCircle(color = color, radius = strokeWidth * 1.8f, center = end)
        return
    }

    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )

    val angle = atan2(dy, dx)
    val arrowLength = strokeWidth * 3.0f
    val arrowAngle = PI.toFloat() / 7f
    val left = Offset(
        x = end.x - arrowLength * cos(angle - arrowAngle),
        y = end.y - arrowLength * sin(angle - arrowAngle),
    )
    val right = Offset(
        x = end.x - arrowLength * cos(angle + arrowAngle),
        y = end.y - arrowLength * sin(angle + arrowAngle),
    )
    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(left.x, left.y)
        moveTo(end.x, end.y)
        lineTo(right.x, right.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

private fun BoardSquare.center(
    files: List<Int>,
    ranks: List<Int>,
    width: Float,
    height: Float,
): Offset {
    val fileIndex = files.indexOf(file).coerceAtLeast(0)
    val rankIndex = ranks.indexOf(rank).coerceAtLeast(0)
    val cellWidth = width / 8f
    val cellHeight = height / 8f
    return Offset(
        x = (fileIndex + 0.5f) * cellWidth,
        y = (rankIndex + 0.5f) * cellHeight,
    )
}

private fun bottomRank(side: ChessSide): Int =
    if (side == ChessSide.WHITE) 0 else 7

private fun leftFile(side: ChessSide): Int =
    if (side == ChessSide.WHITE) 0 else 7

private fun ChessPiece.boardSymbol(): String =
    when (side) {
        ChessSide.WHITE -> when (type) {
            PieceType.KING -> "♔"
            PieceType.QUEEN -> "♕"
            PieceType.ROOK -> "♖"
            PieceType.BISHOP -> "♗"
            PieceType.KNIGHT -> "♘"
            PieceType.PAWN -> "♙"
        }
        ChessSide.BLACK -> when (type) {
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
