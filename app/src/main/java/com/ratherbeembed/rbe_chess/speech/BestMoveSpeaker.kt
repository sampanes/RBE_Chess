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

    fun speakCommit() {
        output.speak("Calculating")
    }

    fun speakBestMove(uci: String) {
        output.speak(SpokenMoveFormatter.spokenBestMove(uci))
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

    fun speakUndo() {
        output.speak("Undid last move")
    }

    fun speakManualMode(on: Boolean) {
        output.speak(if (on) "Manual mode on" else "Manual mode off")
    }

    fun speakMenuOption(text: String) {
        output.speak(text)
    }

    fun speakGameStart(asWhite: Boolean) {
        output.speak(if (asWhite) "Playing as white" else "Playing as black")
    }
}
