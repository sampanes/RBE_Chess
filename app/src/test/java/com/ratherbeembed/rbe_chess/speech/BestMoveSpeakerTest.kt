package com.ratherbeembed.rbe_chess.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class BestMoveSpeakerTest {

    private class FakeSpeechSink : SpeechSink {
        val spoken = mutableListOf<String>()

        override fun speak(text: String) {
            spoken.add(text)
        }
    }

    @Test
    fun `repeatLast speaks fallback before replayable output exists`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.repeatLast()

        assertEquals(listOf("Nothing to repeat."), sink.spoken)
    }

    @Test
    fun `repeatLast replays latest replayable output`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakPlayedMove("Opponent Black", "e7e5", "Waiting for Your White.")
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Opponent Black played E seven to E five. Waiting for Your White.",
                "Opponent Black played E seven to E five. Waiting for Your White.",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `per-press speech does not replace replayable output`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakManualMode(on = true, waiting = "Waiting for Your White.")
        speaker.speakFilePress('c')
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Manual mode on. Waiting for Your White.",
                "C",
                "Manual mode on. Waiting for Your White.",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `battery warning does not replace replayable output`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakMenuOption("Play as black")
        speaker.speakBatteryWarning(critical = false)
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Play as black",
                "Keypad battery low",
                "Play as black",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `illegal move is replayable`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakIllegalMove("Waiting for Opponent Black.")
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Illegal move. Waiting for Opponent Black.",
                "Illegal move. Waiting for Opponent Black.",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `undo speech does not replace replayable output`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakPlayedMove("Opponent Black", "e7e5", "Waiting for Your White.")
        speaker.speakUndo("Waiting for Opponent Black.")
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Opponent Black played E seven to E five. Waiting for Your White.",
                "Undid last move. Waiting for Opponent Black.",
                "Opponent Black played E seven to E five. Waiting for Your White.",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `rememberPlayedMove updates replay without speaking`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakUndo("Waiting for Opponent Black.")
        speaker.rememberPlayedMove("Your White", "e2e4", "Waiting for Opponent Black.")
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Undid last move. Waiting for Opponent Black.",
                "Your White played E two to E four. Waiting for Opponent Black.",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `rememberBoardAtStart updates replay without speaking`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakUndo("Waiting for Your White.")
        speaker.rememberBoardAtStart("Waiting for Your White.")
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Undid last move. Waiting for Your White.",
                "Board at start. Waiting for Your White.",
            ),
            sink.spoken,
        )
    }
}
