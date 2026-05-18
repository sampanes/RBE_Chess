package com.ratherbeembed.rbe_chess.session

import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.GameEndReason
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.TerminalState
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.input.PromotionPickState
import com.ratherbeembed.rbe_chess.input.PromotionPiece
import com.ratherbeembed.rbe_chess.ui.AppPhase
import com.ratherbeembed.rbe_chess.ui.FinishedGameUiState
import com.ratherbeembed.rbe_chess.ui.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSnapshotCodecTest {

    @Test
    fun `round trip preserves live game state`() {
        val snapshot = SessionSnapshot(
            phase = AppPhase.InGame,
            moveHistory = MoveHistory(listOf("e2e4", "e7e5", "g1f3")),
            moveBuffer = MoveBuffer.DEFAULT.copyFromEngine("b8c6"),
            gameMode = GameMode.Manual,
            playerSide = ChessSide.BLACK,
            terminalState = null,
            finishedGame = null,
            promotionPick = null,
            batteryPct = 73,
            miniKeyboardVisible = true,
        )

        assertEquals(snapshot, SessionSnapshotCodec.decode(SessionSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun `round trip preserves finished game and promotion state`() {
        val snapshot = SessionSnapshot(
            phase = AppPhase.InGame,
            moveHistory = MoveHistory(listOf("e7e8q")),
            moveBuffer = MoveBuffer.DEFAULT.copyFromEngine("e7e8"),
            gameMode = GameMode.AutoAdvance,
            playerSide = ChessSide.WHITE,
            terminalState = TerminalState.CHECKMATE,
            finishedGame = FinishedGameUiState(
                reason = GameEndReason.CHECKMATE,
                selectedIndex = 1,
                lastExportPath = "Downloads/RBE Chess/game.txt",
            ),
            promotionPick = PromotionPickState(
                baseMove = "e7e8",
                legalPieces = setOf(PromotionPiece.QUEEN, PromotionPiece.KNIGHT),
            ),
            batteryPct = null,
            miniKeyboardVisible = false,
        )

        assertEquals(snapshot, SessionSnapshotCodec.decode(SessionSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun `invalid snapshots are ignored`() {
        assertNull(SessionSnapshotCodec.decode("not a properties file"))
        assertNull(SessionSnapshotCodec.decode("version=999\nphase=inGame"))
    }
}
