package com.ratherbeembed.rbe_chess.speech

import com.ratherbeembed.rbe_chess.input.MoveBuffer

/**
 * High-level speech facade for the Pocket Mode loop. Owns nothing — composes
 * a [SpeechOutput] (lifecycle managed by the Activity) with the pure
 * [SpokenMoveFormatter]. Lets the caller stay free of TTS detail.
 *
 * The name reflects the M1 acceptance target ("speak the bestmove"); it
 * also covers the per-press and inactivity-prompt phrases per the
 * AGENT_NOTES grammar.
 */
class BestMoveSpeaker(private val output: SpeechOutput) {

    fun speakFilePress(file: Char) {
        output.speak(SpokenMoveFormatter.spokenFile(file))
    }

    fun speakRankPress(rank: Int) {
        output.speak(SpokenMoveFormatter.spokenRank(rank))
    }

    fun speakInactivityPrompt(buffer: MoveBuffer) {
        output.speak(SpokenMoveFormatter.spokenInactivityPrompt(buffer))
    }

    fun speakMovePrompt(mover: String, buffer: MoveBuffer) {
        val move = SpokenMoveFormatter.spokenUciMove(buffer.toUciString())
        output.speak("$mover move: $move?")
    }

    fun speakCommit() {
        output.speak("Calculating")
    }

    fun speakBestMove(uci: String) {
        output.speak(SpokenMoveFormatter.spokenBestMove(uci))
    }

    fun speakPlayedMove(mover: String, uci: String, waiting: String) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        output.speak("$mover played $move. $waiting")
    }

    /** Spoken in manual mode where the engine's pick is advisory only. */
    fun speakSuggestion(uci: String) {
        // Reuse the bestmove formatter to keep pronunciation consistent
        // ("E two to E four"); only the leading label differs.
        val spoken = SpokenMoveFormatter.spokenBestMove(uci)
            .removePrefix("Best move:")
            .trim()
        output.speak("Suggestion: $spoken")
    }

    fun speakSuggestionFor(mover: String, uci: String) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        output.speak("Suggestion for $mover: $move.")
    }

    fun speakCalculatingFor(mover: String) {
        output.speak("Calculating $mover reply")
    }

    fun speakPlayedThenCalculating(mover: String, uci: String, nextMover: String) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        output.speak("$mover played $move. Calculating $nextMover reply")
    }

    fun speakPlayedAndSuggestion(
        mover: String,
        uci: String,
        suggestionMover: String,
        suggestionUci: String,
    ) {
        val move = SpokenMoveFormatter.spokenUciMove(uci)
        val suggestion = SpokenMoveFormatter.spokenUciMove(suggestionUci)
        output.speak(
            "$mover played $move. Suggestion for $suggestionMover: $suggestion. " +
                "Waiting for $suggestionMover.",
        )
    }

    fun speakUndo() {
        output.speak("Undid last move")
    }

    fun speakUndo(waiting: String) {
        output.speak("Undid last move. $waiting")
    }

    fun speakManualMode(on: Boolean) {
        output.speak(if (on) "Manual mode on" else "Manual mode off")
    }

    fun speakManualMode(on: Boolean, waiting: String) {
        output.speak("${if (on) "Manual mode on" else "Manual mode off"}. $waiting")
    }

    fun speakMenuOption(text: String) {
        output.speak(text)
    }

    fun speakGameStart(asWhite: Boolean) {
        output.speak(if (asWhite) "Playing as white" else "Playing as black")
    }

    fun speakBatteryWarning(critical: Boolean) {
        output.speak(
            if (critical) "Keypad battery critical" else "Keypad battery low",
        )
    }
}
