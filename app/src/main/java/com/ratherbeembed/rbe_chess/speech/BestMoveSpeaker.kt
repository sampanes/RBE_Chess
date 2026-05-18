package com.ratherbeembed.rbe_chess.speech

import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.engine.TerminalState

/**
 * High-level speech facade for the Pocket Mode loop. Owns nothing — composes
 * a [SpeechOutput] (lifecycle managed by the Activity) with the pure
 * [SpokenMoveFormatter]. Lets the caller stay free of TTS detail.
 *
 * The name reflects the M1 acceptance target ("speak the bestmove"); it
 * also covers the per-press and inactivity-prompt phrases per the
 * AGENT_NOTES grammar.
 */
class BestMoveSpeaker(private val output: SpeechSink) {

    private var lastBoardEvent: String? = null
    private var lastStatusEvent: String? = null

    private fun speak(text: String) {
        output.speak(text)
    }

    private fun speakQueued(text: String) {
        output.speakQueued(text)
    }

    private fun speakBoardEvent(text: String, queued: Boolean = false) {
        lastBoardEvent = text
        if (queued) {
            output.speakQueued(text)
        } else {
            output.speak(text)
        }
    }

    private fun speakStatusEvent(text: String) {
        lastStatusEvent = text
        output.speak(text)
    }

    private fun playedMoveText(
        mover: String,
        uci: String,
        waiting: String,
        givesCheck: Boolean = false,
    ): String {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        val check = if (givesCheck) " Check." else ""
        return "$mover played $move.$check $waiting"
    }

    private fun playedTerminalText(mover: String, uci: String, state: TerminalState): String {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        return "$mover played $move. ${terminalText(state)}"
    }

    private fun terminalText(state: TerminalState): String =
        when (state) {
            TerminalState.CHECKMATE -> "Checkmate."
            TerminalState.STALEMATE -> "Stalemate."
        }

    fun repeatLast() {
        output.speak(lastBoardEvent ?: lastStatusEvent ?: "Nothing to repeat.")
    }

    fun speakIllegalMove(waiting: String) {
        speak("Illegal move. $waiting")
    }

    fun speakPromotionPrompt(baseMove: String) {
        val move = SpokenMoveFormatter.spokenUciMove(baseMove)
        speakStatusEvent(
            "Promotion: $move. Pinky knight. Ring bishop. Middle rook. " +
                "Index or Thumb queen.",
        )
    }

    fun speakTerminal(state: TerminalState) {
        speakBoardEvent(terminalText(state))
    }

    fun rememberPlayedMove(mover: String, uci: String, waiting: String) {
        lastBoardEvent = playedMoveText(mover, uci, waiting)
    }

    fun rememberBoardAtStart(waiting: String) {
        lastBoardEvent = "Board at start. $waiting"
    }

    fun speakFilePress(file: Char) {
        speak(SpokenMoveFormatter.spokenFile(file))
    }

    fun speakRankPress(rank: Int) {
        speak(SpokenMoveFormatter.spokenRank(rank))
    }

    fun speakInactivityPrompt(buffer: MoveBuffer) {
        speakStatusEvent(SpokenMoveFormatter.spokenInactivityPrompt(buffer))
    }

    fun speakMovePrompt(mover: String, buffer: MoveBuffer) {
        val move = SpokenMoveFormatter.spokenUciMove(buffer.toUciString())
        speakStatusEvent("$mover move: $move?")
    }

    fun speakCommit() {
        speakStatusEvent("Calculating")
    }

    fun speakBestMove(uci: String) {
        speakStatusEvent(SpokenMoveFormatter.spokenBestMove(uci))
    }

    fun speakPlayedMove(
        mover: String,
        uci: String,
        waiting: String,
        givesCheck: Boolean = false,
        queued: Boolean = false,
    ) {
        speakBoardEvent(playedMoveText(mover, uci, waiting, givesCheck), queued)
    }

    fun speakPlayedTerminal(
        mover: String,
        uci: String,
        state: TerminalState,
        queued: Boolean = false,
    ) {
        speakBoardEvent(playedTerminalText(mover, uci, state), queued)
    }

    /** Spoken in manual mode where the engine's pick is advisory only. */
    fun speakSuggestion(uci: String) {
        // Reuse the bestmove formatter to keep pronunciation consistent
        // ("E two to E four"); only the leading label differs.
        val spoken = SpokenMoveFormatter.spokenBestMove(uci)
            .removePrefix("Best move:")
            .trim()
        speakStatusEvent("Suggestion: $spoken")
    }

    fun speakSuggestionFor(mover: String, uci: String) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        speakStatusEvent("Suggestion for $mover: $move.")
    }

    fun speakCalculatingFor(mover: String) {
        speakStatusEvent("Calculating $mover reply")
    }

    fun speakPlayedThenCalculating(
        mover: String,
        uci: String,
        nextMover: String,
        givesCheck: Boolean = false,
    ) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        val check = if (givesCheck) " Check." else ""
        speakBoardEvent("$mover played $move.$check Calculating $nextMover reply")
    }

    fun speakPlayedAndSuggestion(
        mover: String,
        uci: String,
        suggestionMover: String,
        suggestionUci: String,
        givesCheck: Boolean = false,
    ) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        val suggestion = SpokenMoveFormatter.spokenUciMove(suggestionUci)
        val check = if (givesCheck) " Check." else ""
        speakBoardEvent(
            "$mover played $move.$check Suggestion for $suggestionMover: $suggestion. " +
                "Waiting for $suggestionMover.",
        )
    }

    fun speakUndo() {
        speak("Undid last move")
    }

    fun speakUndo(waiting: String) {
        speak("Undid last move. $waiting")
    }

    fun speakManualMode(on: Boolean) {
        speakStatusEvent(if (on) "Manual mode on" else "Manual mode off")
    }

    fun speakManualMode(on: Boolean, waiting: String) {
        speakStatusEvent("${if (on) "Manual mode on" else "Manual mode off"}. $waiting")
    }

    fun speakMenuOption(text: String) {
        speakStatusEvent(text)
    }

    fun speakFinishedGame(reason: String, option: String) {
        speakStatusEvent("$reason. $option.")
    }

    fun speakFinishedGameOption(option: String, queued: Boolean = false) {
        if (queued) {
            speakQueued(option)
        } else {
            speakStatusEvent(option)
        }
    }

    fun speakExportSaved(path: String) {
        speakStatusEvent("Saved game export to $path")
    }

    fun speakExportFailed() {
        speakStatusEvent("Could not save game export")
    }

    fun speakGameStart(asWhite: Boolean) {
        speakStatusEvent(if (asWhite) "Playing as white" else "Playing as black")
    }

    fun speakAutofill(uci: String, reason: String) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        speakQueued("$reason: $move.")
    }

    fun speakBatteryWarning(critical: Boolean) {
        speak(
            if (critical) "Keypad battery critical" else "Keypad battery low",
        )
    }
}
