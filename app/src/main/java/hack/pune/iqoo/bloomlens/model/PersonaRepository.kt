package hack.pune.iqoo.bloomlens.model

import android.content.Context

/** One-time onboarding answers, stored locally so the tutor persists across sessions. */
class PersonaRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPersona(): UserPersona? {
        val skillLevel = prefs.getString(KEY_SKILL_LEVEL, null) ?: return null
        val focusArea = prefs.getString(KEY_FOCUS_AREA, null) ?: return null
        return UserPersona(skillLevel, focusArea)
    }

    fun savePersona(persona: UserPersona) {
        prefs.edit()
            .putString(KEY_SKILL_LEVEL, persona.skillLevel)
            .putString(KEY_FOCUS_AREA, persona.focusArea)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "bloomlens_persona"
        const val KEY_SKILL_LEVEL = "skill_level"
        const val KEY_FOCUS_AREA = "focus_area"
    }
}
