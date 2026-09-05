package hack.pune.iqoo.bloomlens.state

import hack.pune.iqoo.bloomlens.model.SessionRecord

/**
 * A modal overlay layer, independent of [AppScreenState] - opening history doesn't disturb
 * whatever screen (Capturing, Tutoring, etc.) was active underneath.
 */
sealed interface HistoryViewState {
    data class ListView(val sessions: List<SessionRecord>) : HistoryViewState
    data class DetailView(val session: SessionRecord) : HistoryViewState
}
