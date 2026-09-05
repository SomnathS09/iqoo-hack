package hack.pune.iqoo.bloomlens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import hack.pune.iqoo.bloomlens.llm.GenieOnDeviceLlm
import hack.pune.iqoo.bloomlens.model.GenieModelRepository
import hack.pune.iqoo.bloomlens.model.HistoryRepository
import hack.pune.iqoo.bloomlens.model.ImageStorage
import hack.pune.iqoo.bloomlens.model.PersonaRepository
import hack.pune.iqoo.bloomlens.model.RewardsRepository
import hack.pune.iqoo.bloomlens.ocr.TextRecognizer
import hack.pune.iqoo.bloomlens.state.MainViewModel

/** Plain constructor-injected object graph - no DI framework needed for a single-screen-flow app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val personaRepository = PersonaRepository(appContext)
    private val modelRepository = GenieModelRepository(appContext)
    private val rewardsRepository = RewardsRepository(appContext)

    // Not private: Provider Mode's foreground service (ProviderServerService) reaches these
    // through BloomLensApp.container to share the exact same NPU model handle, OCR client, and
    // history log the native UI uses - a separate instance would double-load the model.
    val historyRepository = HistoryRepository(appContext)
    val imageStorage = ImageStorage(appContext)
    val textRecognizer = TextRecognizer()
    val onDeviceLlm = GenieOnDeviceLlm()

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return MainViewModel(
                personaRepository,
                modelRepository,
                historyRepository,
                imageStorage,
                textRecognizer,
                onDeviceLlm,
                rewardsRepository,
            ) as T
        }
    }
}
