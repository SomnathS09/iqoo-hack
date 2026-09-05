package hack.pune.iqoo.bloomlens.model

/**
 * Instruct LLMs tried in order. The published GenieX SDK has no hub-catalog query API, so
 * [GenieModelRepository] just attempts each in turn and falls through to the next on failure.
 */
object ModelCandidates {

    data class HuggingFaceCandidate(val repo: String, val precision: String)

    /**
     * Small Hugging Face GGUF models (llama_cpp runtime), tried first: confirmed public/ungated
     * (HTTP 200 without a token, unlike the gated "unsloth" mirror originally tried) and
     * confirmed small (1.5B-2.7B params, Q4_0). The HYBRID compute unit still schedules
     * per-tensor across the Hexagon HTP + CPU on Snapdragon, so this remains hardware-
     * accelerated even without a qairt/NPU-only bundle.
     */
    val huggingFacePreferred = listOf(
        HuggingFaceCandidate("Qwen/Qwen2.5-1.5B-Instruct-GGUF", "Q4_0"),
        HuggingFaceCandidate("bartowski/Phi-3.5-mini-instruct-GGUF", "Q4_0"),
        HuggingFaceCandidate("bartowski/gemma-2-2b-it-GGUF", "Q4_0"),
    )

    /**
     * Qualcomm AI Hub bundles (qairt runtime) - true single-session NPU inference, tried after
     * the smaller Hugging Face options above. Phi-3.5-mini-instruct, Gemma-2-2b-it and
     * Qwen2.5-1.5B-Instruct were verified NOT published on-hub for Snapdragon 8 Elite Gen 5 as
     * of 2026-09-05 (kept in case that changes). Qwen3-4B-Instruct-2507 IS published but its
     * v0.52.0 release has a reproducible server-side failure around byte ~709MB (same failure
     * via GenieX's own downloader and a plain OkHttp range request from a different network) -
     * kept last since it's currently unreliable and, even when it works, a ~12-15GB multi-part
     * download.
     */
    val aiHubPreferred = listOf(
        "Qwen2.5-1.5B-Instruct",
        "Phi-3.5-mini-instruct",
        "Gemma-2-2b-it",
        "Qwen3-4B-Instruct-2507",
    )
}
