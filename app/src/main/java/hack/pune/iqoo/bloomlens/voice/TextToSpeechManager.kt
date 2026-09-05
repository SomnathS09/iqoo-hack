package hack.pune.iqoo.bloomlens.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Thin wrapper over Android's on-device [TextToSpeech] engine. */
class TextToSpeechManager(context: Context) {
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ready = true
            }
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bloomlens_tts")
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
