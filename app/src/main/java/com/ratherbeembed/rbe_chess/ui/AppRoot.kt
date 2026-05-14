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

@Composable
fun AppRoot() {
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
                text = "M1 step 1 — Kotlin + Compose scaffold",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Engine, keyboard, and Pocket Mode arrive in later steps.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview
@Composable
private fun AppRootPreview() {
    MaterialTheme {
        AppRoot()
    }
}
