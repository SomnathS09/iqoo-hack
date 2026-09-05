package hack.pune.iqoo.bloomlens.model

import android.content.Context

/** Tracks stars earned by completing a full Bloom's Taxonomy journey, and spending them on rewards. */
class RewardsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rewards", Context.MODE_PRIVATE)

    fun getStars(): Int = prefs.getInt(KEY_STARS, 0)

    /** Awards a star for [sessionId] the first time it completes; returns false if already awarded. */
    fun awardStarForSession(sessionId: String): Boolean {
        val awarded = prefs.getStringSet(KEY_AWARDED_SESSIONS, emptySet()) ?: emptySet()
        if (sessionId in awarded) return false
        prefs.edit()
            .putInt(KEY_STARS, getStars() + 1)
            .putStringSet(KEY_AWARDED_SESSIONS, awarded + sessionId)
            .apply()
        return true
    }

    /** Spends stars on [reward]; returns false if the balance is insufficient. */
    fun redeem(reward: Reward): Boolean {
        val stars = getStars()
        if (stars < reward.cost) return false
        prefs.edit().putInt(KEY_STARS, stars - reward.cost).apply()
        return true
    }

    private companion object {
        const val KEY_STARS = "stars"
        const val KEY_AWARDED_SESSIONS = "awarded_sessions"
    }
}
