package hack.pune.iqoo.bloomlens.provider

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Cross-component status for Provider Mode: [ProviderServerService] owns the actual
 * [ProviderWebServer] and updates this; [hack.pune.iqoo.bloomlens.ui.provider.ProviderModeScreen]
 * only reads it. A plain singleton is enough here - both live in the same process.
 */
object ProviderStatus {
    val isRunning = MutableStateFlow(false)
    val sessionCount = MutableStateFlow(0)
    val port = MutableStateFlow(ProviderWebServer.DEFAULT_PORT)
}
