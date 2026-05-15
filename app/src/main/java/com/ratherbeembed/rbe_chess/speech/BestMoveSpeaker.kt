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

    fun speakBestMove(uci: String) {
        output.speak(SpokenMoveFormatter.spokenBestMove(uci))
    }
}
