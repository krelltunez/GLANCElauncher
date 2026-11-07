package app.glance.launcher.system

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Speech to text for the search bar, on the device: Android 12's on-device recognizer where the
 * phone has one, else the system recognizer asked to stay offline. No audio leaves the phone on
 * the first path; on the second it depends on the phone's recognizer, which is why the first is
 * preferred. Partial words stream to [onPartial] as they are heard; [onFinal] gets the last
 * transcript, [onError] a reason to show (nothing heard, no recognizer).
 *
 * Main thread only, as SpeechRecognizer requires. One listen at a time; [destroy] when done.
 */
class VoiceRecognizer(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = false

    /** Whether this phone can recognise speech at all. */
    fun available(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

    fun start() {
        destroy()
        onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        listen()
    }

    private fun listen() {
        val r = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = r
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        onListening(true)
        r.startListening(intent)
    }

    /** Stop listening; what was heard so far still arrives as the final result. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        onListening(false)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            first(partialResults)?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            onListening(false)
            val text = first(results)
            if (text.isNullOrBlank()) onError("Didn't catch that") else onFinal(text)
        }

        override fun onError(error: Int) {
            // The on-device model can be missing for the language or not yet downloaded: try the
            // system recognizer (still asked to stay offline) once before giving up.
            if (onDevice && error in RETRY_OFF_DEVICE) {
                onDevice = false
                recognizer?.destroy()
                listen()
                return
            }
            onListening(false)
            onError(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Voice search needs the microphone permission"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition isn't available offline on this phone"
                    else -> "Voice search stopped (error $error)"
                },
            )
        }
    }

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        /** ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE, ERROR_SERVER (12, 13, 4): no usable on-device model. */
        val RETRY_OFF_DEVICE = setOf(12, 13, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_CLIENT)
    }
}
