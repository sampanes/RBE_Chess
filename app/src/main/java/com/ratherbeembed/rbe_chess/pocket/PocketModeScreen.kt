package com.ratherbeembed.rbe_chess.pocket

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview

/**
 * Black/minimal screen for Pocket Mode. The Activity stays foregrounded
 * (so it keeps BT keyboard focus) but the visual surface is essentially
 * off; a tap anywhere exits Pocket Mode per the AGENT_NOTES grammar.
 */
@Composable
fun PocketModeScreen(onExit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onExit() })
            },
        contentAlignment = Alignment.Center,
    ) {
        // Faint marker so the user can confirm Pocket Mode is active if
        // they peek at the screen. Anything brighter would defeat the
        // point of the dimmed window.
        Text(
            text = "•",
            color = Color(0xFF202020),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview
@Composable
private fun PocketModeScreenPreview() {
    PocketModeScreen(onExit = {})
}
