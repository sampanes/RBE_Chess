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
    buffer: MoveBuffer,
    pocketMode: PocketModeState,
    history: MoveHistory,
    engineStatus: String,
    onEnterPocketMode: () -> Unit,
    onExitPocketMode: () -> Unit,
    onTestStockfish: () -> Unit,
) {
    when (pocketMode) {
        PocketModeState.Pocket -> PocketModeScreen(onExit = onExitPocketMode)
        PocketModeState.Normal -> NormalScreen(
            buffer = buffer,
            history = history,
            engineStatus = engineStatus,
            onEnterPocketMode = onEnterPocketMode,
            onTestStockfish = onTestStockfish,
        )
    }
}

@Composable
private fun NormalScreen(
    buffer: MoveBuffer,
    history: MoveHistory,
    engineStatus: String,
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
                text = "M1 step 4 — Space → engine → bestmove",
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
                text = "Space = commit + ask engine (auto-advances both plies)",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "TTS speaks each press; pause 2.5 s for the prompt.",
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
private fun AppRootPreview() {
    MaterialTheme {
        AppRoot(
            buffer = MoveBuffer.DEFAULT,
            pocketMode = PocketModeState.Normal,
            history = MoveHistory.EMPTY,
            engineStatus = "Engine: idle",
            onEnterPocketMode = {},
            onExitPocketMode = {},
            onTestStockfish = {},
        )
    }
}
