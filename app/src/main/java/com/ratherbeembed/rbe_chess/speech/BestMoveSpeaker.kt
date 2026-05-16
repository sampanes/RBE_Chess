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

    private var lastReplayable: String? = null

    private fun speak(text: String) {
        output.speak(text)
    }

    private fun speakReplayable(text: String) {
        lastReplayable = text
        output.speak(text)
    }

    private fun playedMoveText(mover: String, uci: String, waiting: String): String {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        return "$mover played $move. $waiting"
    }

    fun repeatLast() {
        output.speak(lastReplayable ?: "Nothing to repeat.")
    }

    fun speakIllegalMove(waiting: String) {
        speakReplayable("Illegal move. $waiting")
    }

    fun speakTerminal(state: TerminalState) {
        val phrase = when (state) {
            TerminalState.CHECKMATE -> "Checkmate."
            TerminalState.STALEMATE -> "Stalemate."
        }
        speakReplayable(phrase)
    }

    fun rememberPlayedMove(mover: String, uci: String, waiting: String) {
        lastReplayable = playedMoveText(mover, uci, waiting)
    }

    fun rememberBoardAtStart(waiting: String) {
        lastReplayable = "Board at start. $waiting"
    }

    fun speakFilePress(file: Char) {
        speak(SpokenMoveFormatter.spokenFile(file))
    }

    fun speakRankPress(rank: Int) {
        speak(SpokenMoveFormatter.spokenRank(rank))
    }

    fun speakInactivityPrompt(buffer: MoveBuffer) {
        speakReplayable(SpokenMoveFormatter.spokenInactivityPrompt(buffer))
    }

    fun speakMovePrompt(mover: String, buffer: MoveBuffer) {
        val move = SpokenMoveFormatter.spokenUciMove(buffer.toUciString())
        speakReplayable("$mover move: $move?")
    }

    fun speakCommit() {
        speakReplayable("Calculating")
    }

    fun speakBestMove(uci: String) {
        speakReplayable(SpokenMoveFormatter.spokenBestMove(uci))
    }

    fun speakPlayedMove(mover: String, uci: String, waiting: String) {
        speakReplayable(playedMoveText(mover, uci, waiting))
    }

    /** Spoken in manual mode where the engine's pick is advisory only. */
    fun speakSuggestion(uci: String) {
        // Reuse the bestmove formatter to keep pronunciation consistent
        // ("E two to E four"); only the leading label differs.
        val spoken = SpokenMoveFormatter.spokenBestMove(uci)
            .removePrefix("Best move:")
            .trim()
        speakReplayable("Suggestion: $spoken")
    }

    fun speakSuggestionFor(mover: String, uci: String) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        speakReplayable("Suggestion for $mover: $move.")
    }

    fun speakCalculatingFor(mover: String) {
        speakReplayable("Calculating $mover reply")
    }

    fun speakPlayedThenCalculating(mover: String, uci: String, nextMover: String) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        speakReplayable("$mover played $move. Calculating $nextMover reply")
    }

    fun speakPlayedAndSuggestion(
        mover: String,
        uci: String,
        suggestionMover: String,
        suggestionUci: String,
    ) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        val suggestion = SpokenMoveFormatter.spokenUciMove(suggestionUci)
        speakReplayable(
            "$mover played $move. Suggestion for $suggestionMover: $suggestion. " +
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
        speakReplayable(if (on) "Manual mode on" else "Manual mode off")
    }

    fun speakManualMode(on: Boolean, waiting: String) {
        speakReplayable("${if (on) "Manual mode on" else "Manual mode off"}. $waiting")
    }

    fun speakMenuOption(text: String) {
        speakReplayable(text)
    }

    fun speakGameStart(asWhite: Boolean) {
        speakReplayable(if (asWhite) "Playing as white" else "Playing as black")
    }

    fun speakBatteryWarning(critical: Boolean) {
        speak(
            if (critical) "Keypad battery critical" else "Keypad battery low",
        )
    }
}
