package com.ratherbeembed.rbe_chess.input

/**
 * Every physical/virtual key the app reacts to. The first five map 1:1
 * to the v1 cycler keys on the BT keypad (Pinky/Ring/Middle/Index/Thumb,
 * emitted as HID codes D/F/J/K/SPACE). The
 * next three are firmware-v2 chord emissions — the Bluefruit sends a
 * distinct HID letter when Thumb/Space is held and a cycler is tapped:
 *
 *   Thumb+Pinky  -> 'U' (UNDO)
 *   Thumb+Ring   -> 'M' (TOGGLE_MANUAL)
 *   Thumb+Middle -> 'R' (REPEAT_LAST)
 *   Thumb+Index  -> 'N' (end current game; starts a new game from done state)
 *
 * See firmware/RBE_32u4_chess/README.md §"Thumb/Space-as-modifier chords".
 */
enum class ChessKey {
    D,
    F,
    J,
    K,
    SPACE,
    UNDO,
    TOGGLE_MANUAL,
    REPEAT_LAST,
    NEW_GAME,
    IGNORED,
}

sealed interface GrammarAction {
    data object CycleFromFile : GrammarAction
    data object CycleFromRank : GrammarAction
    data object CycleToFile : GrammarAction
    data object CycleToRank : GrammarAction
    data object Commit : GrammarAction
    data object Undo : GrammarAction
    data object ToggleManual : GrammarAction
    data object RepeatLast : GrammarAction
    data object NewGame : GrammarAction
    data object Ignored : GrammarAction
}

object KeyboardGrammar {
    fun translate(key: ChessKey): GrammarAction = when (key) {
        ChessKey.D -> GrammarAction.CycleFromFile
        ChessKey.F -> GrammarAction.CycleFromRank
        ChessKey.J -> GrammarAction.CycleToFile
        ChessKey.K -> GrammarAction.CycleToRank
        ChessKey.SPACE -> GrammarAction.Commit
        ChessKey.UNDO -> GrammarAction.Undo
        ChessKey.TOGGLE_MANUAL -> GrammarAction.ToggleManual
        ChessKey.REPEAT_LAST -> GrammarAction.RepeatLast
        ChessKey.NEW_GAME -> GrammarAction.NewGame
        ChessKey.IGNORED -> GrammarAction.Ignored
    }

    fun apply(action: GrammarAction, buffer: MoveBuffer): MoveBuffer = when (action) {
        GrammarAction.CycleFromFile -> buffer.cycleFromFile()
        GrammarAction.CycleFromRank -> buffer.cycleFromRank()
        GrammarAction.CycleToFile -> buffer.cycleToFile()
        GrammarAction.CycleToRank -> buffer.cycleToRank()
        GrammarAction.Commit -> MoveBuffer.DEFAULT
        // Chord actions wipe any in-progress cycler input: after Undo or
        // NewGame/End the half-typed move is no longer meaningful. Manual
        // toggle and RepeatLast leave the buffer alone since they do not
        // change the move being typed.
        GrammarAction.Undo -> MoveBuffer.DEFAULT
        GrammarAction.ToggleManual -> buffer
        GrammarAction.RepeatLast -> buffer
        GrammarAction.NewGame -> MoveBuffer.DEFAULT
        GrammarAction.Ignored -> buffer
    }
}
