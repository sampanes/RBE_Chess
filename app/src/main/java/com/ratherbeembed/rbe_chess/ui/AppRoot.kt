package com.ratherbeembed.rbe_chess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    batteryPct: Int?,
    onEnterPocketMode: () -> Unit,
    onExitPocketMode: () -> Unit,
    onTestStockfish: () -> Unit,
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
                batteryPct = batteryPct,
                onEnterPocketMode = onEnterPocketMode,
                onTestStockfish = onTestStockfish,
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
    batteryPct: Int?,
    onEnterPocketMode: () -> Unit,
    onTestStockfish: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
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
            Text(
                text = buffer.toUciString(),
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "D = from-file   F = from-rank",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "J = to-file     K = to-rank",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Space = commit + ask engine",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Hold Space + D = undo, + F = manual toggle, + K = new game",
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
            Spacer(Modifier.height(24.dp))
            Button(onClick = onTestStockfish) {
                Text("Test Stockfish (startpos)")
            }
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
            batteryPct = 87,
            onEnterPocketMode = {},
            onExitPocketMode = {},
            onTestStockfish = {},
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
            batteryPct = 87,
            onEnterPocketMode = {},
            onExitPocketMode = {},
            onTestStockfish = {},
        )
    }
}
