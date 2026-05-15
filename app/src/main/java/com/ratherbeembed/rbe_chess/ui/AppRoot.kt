package com.ratherbeembed.rbe_chess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.pocket.PocketModeScreen
import com.ratherbeembed.rbe_chess.pocket.PocketModeState

@Composable
fun AppRoot(
    phase: AppPhase,
    buffer: MoveBuffer,
    pocketMode: PocketModeState,
    history: MoveHistory,
    engineStatus: String,
    gameMode: GameMode,
    playerSide: ChessSide,
    batteryPct: Int?,
    onEnterPocketMode: () -> Unit,
    onExitPocketMode: () -> Unit,
) {
    when (phase) {
        is AppPhase.StartMenu -> StartMenuScreen(
            options = START_MENU_OPTIONS,
            selectedIndex = phase.selectedIndex,
        )
        AppPhase.InGame -> when (pocketMode) {
            PocketModeState.Pocket -> PocketModeScreen(onExit = onExitPocketMode)
            PocketModeState.Normal -> NormalScreen(
                buffer = buffer,
                history = history,
                engineStatus = engineStatus,
                gameMode = gameMode,
                playerSide = playerSide,
                batteryPct = batteryPct,
                onEnterPocketMode = onEnterPocketMode,
            )
        }
    }
}

@Composable
private fun NormalScreen(
    buffer: MoveBuffer,
    history: MoveHistory,
    engineStatus: String,
    gameMode: GameMode,
    playerSide: ChessSide,
    batteryPct: Int?,
    onEnterPocketMode: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RBE Chess",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Mode: ${if (gameMode == GameMode.Manual) "Manual" else "AutoAdvance"}    " +
                    "Keypad battery: ${batteryPct?.let { "$it%" } ?: "unknown"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            ChessBoard(
                history = history,
                bottomSide = playerSide,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = buffer.toUciString(),
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Pinky = from-file   Ring = from-rank",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Middle = to-file   Index = to-rank",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Thumb = commit + ask engine",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Hold Thumb:\n(+ Pinky = undo) (+ Ring = manual) (+ Index = new game)",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (history.size == 0) "History: (empty)"
                       else "History (${history.size}): ${history.moves.joinToString(" ")}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onEnterPocketMode) {
                Text("Enter Pocket Mode")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap anywhere on the black screen to exit.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = engineStatus,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview
@Composable
private fun AppRootInGamePreview() {
    MaterialTheme {
        AppRoot(
            phase = AppPhase.InGame,
            buffer = MoveBuffer.DEFAULT,
            pocketMode = PocketModeState.Normal,
            history = MoveHistory.EMPTY,
            engineStatus = "Engine: idle",
            gameMode = GameMode.AutoAdvance,
            playerSide = ChessSide.WHITE,
            batteryPct = 87,
            onEnterPocketMode = {},
            onExitPocketMode = {},
        )
    }
}

@Preview
@Composable
private fun AppRootStartMenuPreview() {
    MaterialTheme {
        AppRoot(
            phase = AppPhase.StartMenu(selectedIndex = 0),
            buffer = MoveBuffer.DEFAULT,
            pocketMode = PocketModeState.Normal,
            history = MoveHistory.EMPTY,
            engineStatus = "Engine: idle",
            gameMode = GameMode.AutoAdvance,
            playerSide = ChessSide.WHITE,
            batteryPct = 87,
            onEnterPocketMode = {},
            onExitPocketMode = {},
        )
    }
}
