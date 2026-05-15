package com.ratherbeembed.rbe_chess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ratherbeembed.rbe_chess.input.MoveBuffer

@Composable
fun AppRoot(buffer: MoveBuffer) {
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
                text = "M1 step 2b — cycler + TTS feedback",
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
                text = "Space = commit (logged, engine wiring is step 4)",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "TTS speaks each press; pause 2.5 s for the prompt.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview
@Composable
private fun AppRootPreview() {
    MaterialTheme {
        AppRoot(buffer = MoveBuffer.DEFAULT)
    }
}
