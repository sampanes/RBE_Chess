package com.ratherbeembed.rbe_chess.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

private const val TAG = "RBE_TTS"

/**
 * Thin wrapper around Android [TextToSpeech] for the Pocket Mode feedback
 * loop. Routes via USAGE_MEDIA + CONTENT_TYPE_SPEECH so a connected A2DP
 * Bluetooth speaker/headset receives the audio (Android's default media
 * routing is what we rely on; do not build custom BT routing per
 * AGENT_NOTES). Requests transient-may-duck audio focus on each utterance
 * and abandons it when the utterance completes.
 *
 * QUEUE_FLUSH is used intentionally so the latest press is what the user
 * hears — bursts of presses outpace TTS, and we'd rather speak the current
 * value than play back a stale queue.
 */
class SpeechOutput(context: Context) : SpeechSink {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest: AudioFocusRequest = AudioFocusRequest
        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener { /* no-op; bursts are short */ }
        .build()

    @Volatile private var ready = false
    @Volatile private var shutdownRequested = false
    @Volatile private var currentUtteranceId: String? = null
    private val pending = ArrayDeque<String>()
    private val pendingLock = Any()

    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(appContext) { status -> onTtsInit(status) }
    }

    private fun onTtsInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TextToSpeech init failed: status=$status")
            return
        }
        tts.setAudioAttributes(audioAttributes)
        tts.language = Locale.US
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = release(utteranceId)
            @Deprecated("kept for older Android paths")
            override fun onError(utteranceId: String?) = release(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) =
                release(utteranceId)
        })
        ready = true
        Log.d(TAG, "TextToSpeech ready")
        val drained: List<String> = synchronized(pendingLock) {
            val copy = pending.toList()
            pending.clear()
            copy
        }
        drained.forEach { speakInternal(it) }
    }

    override fun speak(text: String) {
        if (shutdownRequested || text.isBlank()) return
        if (!ready) {
            synchronized(pendingLock) { pending.addLast(text) }
            return
        }
        speakInternal(text)
    }

    private fun speakInternal(text: String) {
        val id = "rbe-${System.nanoTime()}"
        currentUtteranceId = id
        audioManager.requestAudioFocus(focusRequest)
        val rc = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (rc != TextToSpeech.SUCCESS) {
            Log.w(TAG, "tts.speak returned $rc for '$text'")
            release(id)
        }
    }

    private fun release(utteranceId: String?) {
        if (utteranceId != currentUtteranceId) return
        currentUtteranceId = null
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    fun shutdown() {
        shutdownRequested = true
        ready = false
        try {
            tts.stop()
            tts.shutdown()
        } catch (t: Throwable) {
            Log.w(TAG, "tts shutdown threw: ${t.message}")
        }
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
