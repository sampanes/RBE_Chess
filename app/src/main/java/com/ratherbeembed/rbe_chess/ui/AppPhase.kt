package com.ratherbeembed.rbe_chess.ui

import androidx.compose.runtime.Immutable

/**
 * Top-level screen state for the app. Sits above [com.ratherbeembed.rbe_chess.pocket.PocketModeState]
 * so the start menu can pre-empt both the normal and pocket game screens.
 *
 * @Immutable on the data class so Compose strong-skipping recomposes the
 * start menu when [StartMenu.selectedIndex] changes (the index drives the
 * highlighted option label).
 */
sealed interface AppPhase {
    @Immutable
    data class StartMenu(val selectedIndex: Int = 0) : AppPhase
    data object InGame : AppPhase
}

/**
 * Two-option side-select menu. F cycles up, J cycles down, Space selects.
 * Index 0 = play as white (engine bootstraps with white's opening move);
 * index 1 = play as black (engine waits for the user to type white's
 * first move). Kept as `val` so the index constants stay stable.
 */
val START_MENU_OPTIONS: List<String> = listOf(
    "Play as white",
    "Play as black",
)

const val START_MENU_PLAY_WHITE: Int = 0
const val START_MENU_PLAY_BLACK: Int = 1

/**
 * Toggleable input flow. AutoAdvance is the default M1 loop: each typed
 * move is the opponent's, and the engine's reply is auto-appended so the
 * user only ever enters opponent moves. Manual mode keeps the engine's
 * reply advisory — the user types every ply themselves and Stockfish just
 * whispers what it would have played.
 */
enum class GameMode { AutoAdvance, Manual }
