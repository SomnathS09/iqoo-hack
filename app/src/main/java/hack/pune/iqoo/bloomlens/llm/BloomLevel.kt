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
        fun fromLabel(label: String): BloomLevel? = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }
}
