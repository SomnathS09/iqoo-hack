package hack.pune.iqoo.bloomlens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import hack.pune.iqoo.bloomlens.llm.GenieOnDeviceLlm
import hack.pune.iqoo.bloomlens.model.GenieModelRepository
import hack.pune.iqoo.bloomlens.model.HistoryRepository
import hack.pune.iqoo.bloomlens.model.PersonaRepository
import hack.pune.iqoo.bloomlens.ocr.TextRecognizer
import hack.pune.iqoo.bloomlens.state.MainViewModel

/** Plain constructor-injected object graph - no DI framework needed for a single-screen-flow app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val personaRepository = PersonaRepository(appContext)
    private val modelRepository = GenieModelRepository(appContext)
    private val historyRepository = HistoryRepository(appContext)
    private val textRecognizer = TextRecognizer()
    private val onDeviceLlm = GenieOnDeviceLlm()

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return MainViewModel(personaRepository, modelRepository, historyRepository, textRecognizer, onDeviceLlm) as T
        }
    }
}
