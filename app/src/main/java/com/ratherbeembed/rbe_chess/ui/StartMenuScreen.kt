package com.ratherbeembed.rbe_chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Verbal start menu. The TTS layer is the primary feedback channel — the
 * visible Compose surface is just so a sighted user can confirm which
 * option is highlighted. Ring = up, Middle = down, Thumb = select;
 * Pinky and Index do nothing in menu state. See
 * [com.ratherbeembed.rbe_chess.MainActivity.handleMenuKey].
 */
@Composable
fun StartMenuScreen(
    options: List<String>,
    selectedIndex: Int,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "RBE Chess",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Start menu",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            options.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                Text(
                    text = if (selected) "> $label" else "  $label",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Ring = up   Middle = down   Thumb = select",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Mid-game: hold Thumb + Pinky = undo, + Ring = manual, + Index = new game",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
