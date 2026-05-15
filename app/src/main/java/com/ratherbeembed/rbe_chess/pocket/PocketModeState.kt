package com.ratherbeembed.rbe_chess.pocket

/**
 * UI mode for the M1 shell. Will likely grow into a data class once
 * step 4 wires the engine and Pocket Mode needs to surface "Last move"
 * and "Best move" values, but a binary toggle is enough for 2c.
 */
enum class PocketModeState { Normal, Pocket }
