package hack.pune.iqoo.bloomlens.model

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local study log: every tutoring session, persisted as JSON in SharedPreferences. */
class HistoryRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getAllSessions(): List<SessionRecord> {
        val stored = readAll()
        return stored.sortedByDescending { it.timestamp }
    }

    /** Upserts by [SessionRecord.id] - safe to call on every new turn to keep history live-updated. */
    fun saveSession(record: SessionRecord) {
        val updated = readAll().filterNot { it.id == record.id } + record
        prefs.edit().putString(KEY_SESSIONS, json.encodeToString(updated)).apply()
    }

    private fun readAll(): List<SessionRecord> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SessionRecord>>(raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS_NAME = "bloomlens_history"
        const val KEY_SESSIONS = "sessions_json"
    }
}
