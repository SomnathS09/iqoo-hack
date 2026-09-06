package hack.pune.iqoo.bloomlens.llm

/** Bloom's Taxonomy levels, lowest to highest depth of understanding. */
enum class BloomLevel(val label: String) {
    REMEMBER("Remember"),
    UNDERSTAND("Understand"),
    APPLY("Apply"),
    ANALYZE("Analyze"),
    EVALUATE("Evaluate"),
    CREATE("Create"),
    ;

    companion object {
        /**
         * Matches leniently, not just exact equality: a small on-device model doesn't always
         * echo the level name verbatim (extra words, wrong case, a trailing "-ing"). Exact-only
         * matching here silently falls back to the *previous* level on any mismatch, which reads
         * as the tutor being stuck even when the model actually intended to advance.
         */
        fun fromLabel(label: String): BloomLevel? {
            val normalized = label.trim().lowercase()
            if (normalized.isEmpty()) return null
            return entries.firstOrNull { normalized.startsWith(it.label.lowercase()) }
                ?: entries.firstOrNull { normalized.contains(it.label.lowercase()) }
        }
    }
}
