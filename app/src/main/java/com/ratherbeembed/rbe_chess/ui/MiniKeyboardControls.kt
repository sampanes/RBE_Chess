package com.ratherbeembed.rbe_chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ratherbeembed.rbe_chess.input.ChessKey
import com.ratherbeembed.rbe_chess.input.MiniKeyboardButton
import com.ratherbeembed.rbe_chess.input.MiniKeyboardInput

@Composable
fun MiniKeyboardToggle(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiniControlButton(
        text = if (enabled) "Mini on" else "Mini off",
        active = enabled,
        width = 76.dp,
        modifier = modifier,
        onClick = onToggle,
    )
}

@Composable
fun MiniKeyboardPanel(
    onKey: (ChessKey) -> Unit,
    onMockBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var thumbHeld by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                MiniKeyboardButton.PINKY,
                MiniKeyboardButton.RING,
                MiniKeyboardButton.MIDDLE,
                MiniKeyboardButton.INDEX,
            ).forEach { button ->
                MiniControlButton(
                    text = buttonLabel(button, thumbHeld),
                    active = thumbHeld,
                    width = 48.dp,
                    height = 44.dp,
                    onClick = {
                        val result = MiniKeyboardInput.tap(button, thumbHeld)
                        thumbHeld = result.thumbHeld
                        onKey(result.key)
                    },
                )
            }
            MiniControlButton(
                text = "T",
                active = false,
                prominent = true,
                width = 48.dp,
                height = 44.dp,
                onClick = {
                    val result = MiniKeyboardInput.tap(MiniKeyboardButton.THUMB, thumbHeld)
                    thumbHeld = result.thumbHeld
                    onKey(result.key)
                },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniControlButton(
                text = "Hold",
                active = thumbHeld,
                width = 92.dp,
                height = 38.dp,
                onClick = { thumbHeld = !thumbHeld },
            )
            MiniControlButton(
                text = "B%",
                active = false,
                width = 70.dp,
                height = 38.dp,
                onClick = onMockBattery,
            )
        }
        if (thumbHeld) {
            Text(
                text = "U  M  R  N",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MiniControlButton(
    text: String,
    active: Boolean,
    prominent: Boolean = false,
    width: Dp = 44.dp,
    height: Dp = 38.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val container =
        if (active || prominent) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val border =
        if (active || prominent) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (prominent) 2.dp else 1.dp
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(container)
            .border(borderWidth, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active || prominent) FontWeight.Bold else FontWeight.Normal,
            color =
                if (active || prominent) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private fun buttonLabel(button: MiniKeyboardButton, thumbHeld: Boolean): String =
    if (thumbHeld) {
        when (button) {
            MiniKeyboardButton.PINKY -> "U"
            MiniKeyboardButton.RING -> "M"
            MiniKeyboardButton.MIDDLE -> "R"
            MiniKeyboardButton.INDEX -> "N"
            MiniKeyboardButton.THUMB -> "T"
        }
    } else {
        when (button) {
            MiniKeyboardButton.PINKY -> "P"
            MiniKeyboardButton.RING -> "R"
            MiniKeyboardButton.MIDDLE -> "M"
            MiniKeyboardButton.INDEX -> "I"
            MiniKeyboardButton.THUMB -> "T"
        }
    }
