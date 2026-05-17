package com.ratherbeembed.rbe_chess.speech

interface SpeechSink {
    fun speak(text: String)

    fun speakQueued(text: String) {
        speak(text)
    }
}
