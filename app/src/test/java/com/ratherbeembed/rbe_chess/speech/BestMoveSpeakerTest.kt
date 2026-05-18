package com.ratherbeembed.rbe_chess.speech

import com.ratherbeembed.rbe_chess.engine.TerminalState
import org.junit.Assert.assertEquals
import org.junit.Test

class BestMoveSpeakerTest {

    private class FakeSpeechSink : SpeechSink {
        val spoken = mutableListOf<String>()
        val queued = mutableListOf<String>()

        override fun speak(text: String) {
            spoken.add(text)
        }

        override fun speakQueued(text: String) {
            queued.add(text)
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
    fun `played move can announce ordinary check`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakPlayedMove(
            "Your White",
            "d1h5",
            "Waiting for Opponent Black.",
            givesCheck = true,
        )
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Your White played D one to H five. Check. Waiting for Opponent Black.",
                "Your White played D one to H five. Check. Waiting for Opponent Black.",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `played move can queue behind current speech`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakPlayedMove(
            "Opponent Black",
            "e7e5",
            "Waiting for Your White.",
            queued = true,
        )

        assertEquals(
            listOf("Opponent Black played E seven to E five. Waiting for Your White."),
            sink.queued,
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
    fun `illegal move does not replace last board event`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakPlayedMove("Your White", "e2e4", "Waiting for Opponent Black.")
        speaker.speakIllegalMove("Waiting for Opponent Black.")
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Your White played E two to E four. Waiting for Opponent Black.",
                "Illegal move. Waiting for Opponent Black.",
                "Your White played E two to E four. Waiting for Opponent Black.",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `promotion prompt does not replace last board event`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakPlayedMove("Opponent Black", "a7a6", "Waiting for Your White.")
        speaker.speakPromotionPrompt("e7e8")
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Opponent Black played A seven to A six. Waiting for Your White.",
                "Promotion: E seven to E eight. Pinky knight. Ring bishop. Middle rook. Index or Thumb queen.",
                "Opponent Black played A seven to A six. Waiting for Your White.",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `terminal state is replayable`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakTerminal(TerminalState.CHECKMATE)
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Checkmate.",
                "Checkmate.",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `move that ends the game is replayable as one board event`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakPlayedTerminal("Opponent Black", "d8h4", TerminalState.CHECKMATE)
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Opponent Black played D eight to H four. Checkmate.",
                "Opponent Black played D eight to H four. Checkmate.",
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
    fun `mode status does not replace last board event`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakPlayedMove("Opponent Black", "e7e5", "Waiting for Your White.")
        speaker.speakManualMode(on = true, waiting = "Waiting for Your White.")
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Opponent Black played E seven to E five. Waiting for Your White.",
                "Manual mode on. Waiting for Your White.",
                "Opponent Black played E seven to E five. Waiting for Your White.",
            ),
            sink.spoken,
        )
    }

    @Test
    fun `autofill announcement does not replace last board event`() {
        val sink = FakeSpeechSink()
        val speaker = BestMoveSpeaker(sink)

        speaker.speakPlayedMove("Opponent Black", "e7e5", "Waiting for Your White.")
        speaker.speakAutofill("g1f3", "Only move from selected piece")
        speaker.repeatLast()

        assertEquals(
            listOf(
                "Opponent Black played E seven to E five. Waiting for Your White.",
                "Only move from selected piece: G one to F three.",
                "Opponent Black played E seven to E five. Waiting for Your White.",
            ),
            sink.spoken,
        )
        assertEquals(listOf("Only move from selected piece: G one to F three."), sink.queued)
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
