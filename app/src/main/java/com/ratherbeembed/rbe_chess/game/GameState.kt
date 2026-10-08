package com.ratherbeembed.rbe_chess.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.TerminalState
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.input.PromotionPickState
import com.ratherbeembed.rbe_chess.session.SessionSnapshot
import com.ratherbeembed.rbe_chess.ui.AppPhase
import com.ratherbeembed.rbe_chess.ui.FINISHED_GAME_OPTIONS
import com.ratherbeembed.rbe_chess.ui.FinishedGameUiState
import com.ratherbeembed.rbe_chess.ui.GameMode
import com.ratherbeembed.rbe_chess.ui.START_MENU_OPTIONS

/**
 * Observable game/UI state. Every field is Compose state so the UI and the
 * session-persistence flow both react to changes. Only [GameController]
 * mutates it; the setters are public so tests can arrange scenarios.
 */
class GameState {
    var phase by mutableStateOf<AppPhase>(AppPhase.StartMenu(0))
    var moveBuffer by mutableStateOf(MoveBuffer.DEFAULT)
    var moveHistory by mutableStateOf(MoveHistory.EMPTY)
    var pendingMove by mutableStateOf<String?>(null)
    var promotionPick by mutableStateOf<PromotionPickState?>(null)
    var finishedGame by mutableStateOf<FinishedGameUiState?>(null)
    var engineStatus by mutableStateOf(ENGINE_IDLE)
    var gameMode by mutableStateOf(GameMode.AutoAdvance)
    var playerSide by mutableStateOf(ChessSide.WHITE)
    var terminalState by mutableStateOf<TerminalState?>(null)
    var batteryPct by mutableStateOf<Int?>(null)
    var miniKeyboardVisible by mutableStateOf(false)

    val sideToMove: ChessSide get() = sideToMove(moveHistory)

    fun moverLabel(side: ChessSide): String = moverLabel(side, playerSide)

    fun waitingPhrase(): String = "Waiting for ${moverLabel(sideToMove)}."

    /**
     * True while the board and input buffer are exactly as they were when a
     * background request captured them. Async results check this before
     * touching the buffer so a late answer never clobbers newer input.
     */
    fun isUnchangedSince(history: MoveHistory, buffer: MoveBuffer): Boolean =
        phase == AppPhase.InGame &&
            terminalState == null &&
            moveHistory == history &&
            moveBuffer == buffer

    /** Drop everything tied to the current position except the move list. */
    fun clearPositionState() {
        pendingMove = null
        promotionPick = null
        finishedGame = null
        moveBuffer = MoveBuffer.DEFAULT
        terminalState = null
    }

    fun clearBoard() {
        moveHistory = MoveHistory.EMPTY
        clearPositionState()
    }

    fun finishWith(terminal: TerminalState) {
        terminalState = terminal
        finishedGame = FinishedGameUiState(terminal.toEndReason())
    }

    fun toSnapshot(): SessionSnapshot =
        SessionSnapshot(
            phase = phase,
            moveHistory = moveHistory,
            moveBuffer = moveBuffer,
            gameMode = gameMode,
            playerSide = playerSide,
            terminalState = terminalState,
            finishedGame = finishedGame,
            promotionPick = promotionPick,
            batteryPct = batteryPct,
            miniKeyboardVisible = miniKeyboardVisible,
        )

    fun applySnapshot(snapshot: SessionSnapshot) {
        phase = when (val restored = snapshot.phase) {
            is AppPhase.StartMenu ->
                AppPhase.StartMenu(restored.selectedIndex.coerceIn(0, START_MENU_OPTIONS.lastIndex))
            AppPhase.InGame -> AppPhase.InGame
        }
        moveHistory = snapshot.moveHistory
        moveBuffer = snapshot.moveBuffer
        gameMode = snapshot.gameMode
        playerSide = snapshot.playerSide
        terminalState = snapshot.terminalState
        finishedGame = snapshot.finishedGame
            ?.let { it.copy(selectedIndex = it.selectedIndex.coerceIn(0, FINISHED_GAME_OPTIONS.lastIndex)) }
            ?: snapshot.terminalState?.let { FinishedGameUiState(it.toEndReason()) }
        promotionPick = snapshot.promotionPick
        batteryPct = snapshot.batteryPct
        miniKeyboardVisible = snapshot.miniKeyboardVisible
        pendingMove = null
    }

    companion object {
        const val ENGINE_IDLE = "Engine: idle"
    }
}
