package hack.pune.iqoo.bloomlens.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Thin wrapper over Android's [SpeechRecognizer] for one-shot speech-to-text. */
class SpeechRecognizerManager(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun startListening(onRecognized: (String) -> Unit, onFailure: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFailure("Speech recognition isn't available on this device")
            return
        }
        stopListening()

        val instance = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = instance
        instance.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) onFailure("Didn't catch that - try again") else onRecognized(text)
                }

                override fun onError(error: Int) {
                    onFailure("Couldn't hear you clearly - try again")
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        instance.startListening(intent)
    }

    fun stopListening() {
        recognizer?.destroy()
        recognizer = null
    }
}
