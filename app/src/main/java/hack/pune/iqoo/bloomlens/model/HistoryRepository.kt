package hack.pune.iqoo.bloomlens.model

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local study log: every tutoring session, persisted as JSON in SharedPreferences.
 *
 * [saveSession] does a read-modify-write over the whole list, so calls are synchronized -
 * Provider Mode's web server can save sessions from a background thread at the same time the
 * native UI does from the main thread.
 */
class HistoryRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    fun getAllSessions(): List<SessionRecord> {
        val stored = synchronized(lock) { readAll() }
        return stored.sortedByDescending { it.timestamp }
    }

    /** Upserts by [SessionRecord.id] - safe to call on every new turn to keep history live-updated. */
    fun saveSession(record: SessionRecord) {
        synchronized(lock) {
            val updated = readAll().filterNot { it.id == record.id } + record
            prefs.edit().putString(KEY_SESSIONS, json.encodeToString(updated)).apply()
        }
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
