package com.ratherbeembed.rbe_chess.input

enum class ChessKey { D, F, J, K, SPACE, IGNORED }

sealed interface GrammarAction {
    data object CycleFromFile : GrammarAction
    data object CycleFromRank : GrammarAction
    data object CycleToFile : GrammarAction
    data object CycleToRank : GrammarAction
    data object Commit : GrammarAction
    data object Ignored : GrammarAction
}

object KeyboardGrammar {
    fun translate(key: ChessKey): GrammarAction = when (key) {
        ChessKey.D -> GrammarAction.CycleFromFile
        ChessKey.F -> GrammarAction.CycleFromRank
        ChessKey.J -> GrammarAction.CycleToFile
        ChessKey.K -> GrammarAction.CycleToRank
        ChessKey.SPACE -> GrammarAction.Commit
        ChessKey.IGNORED -> GrammarAction.Ignored
    }

    fun apply(action: GrammarAction, buffer: MoveBuffer): MoveBuffer = when (action) {
        GrammarAction.CycleFromFile -> buffer.cycleFromFile()
        GrammarAction.CycleFromRank -> buffer.cycleFromRank()
        GrammarAction.CycleToFile -> buffer.cycleToFile()
        GrammarAction.CycleToRank -> buffer.cycleToRank()
        GrammarAction.Commit -> MoveBuffer.DEFAULT
        GrammarAction.Ignored -> buffer
    }
}
