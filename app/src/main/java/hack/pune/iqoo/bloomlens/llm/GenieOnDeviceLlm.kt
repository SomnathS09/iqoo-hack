package hack.pune.iqoo.bloomlens.llm

import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.bean.ComputeUnitValue
import com.geniex.sdk.bean.GenerationConfig
import com.geniex.sdk.bean.LlmCreateInput
import com.geniex.sdk.bean.LlmStreamResult
import com.geniex.sdk.bean.ModelConfig
import com.geniex.sdk.bean.ModelPaths
import com.geniex.sdk.bean.RuntimeIdValue
import com.geniex.sdk.bean.SamplerConfig

/** Sole [OnDeviceLlm] implementation, wrapping Qualcomm GenieX's [LlmWrapper]. */
class GenieOnDeviceLlm : OnDeviceLlm {

    @Volatile
    private var llmWrapper: LlmWrapper? = null

    @Volatile
    private var isQairt: Boolean = false

    override suspend fun initialize(paths: ModelPaths): Result<Unit> {
        release()
        isQairt = paths.runtime_id == RuntimeIdValue.QAIRT.value
        val computeUnit = if (isQairt) {
            ComputeUnitValue.NPU.value
        } else {
            null // let llama_cpp default to its HYBRID Hexagon+CPU scheduler
        }
        val input = LlmCreateInput(
            model_name = paths.model_name,
            model_path = paths.model_path,
            tokenizer_path = paths.tokenizer_path,
            // 0 = use the model's own compiled default. The qairt/NPU plugin rejects any
            // explicit n_ctx override outright (it only supports the context lengths its
            // bundle was precompiled for); llama_cpp treats 0 the same way.
            config = ModelConfig(nCtx = 0),
            runtime_id = paths.runtime_id,
            compute_unit = computeUnit,
        )
        return LlmWrapper.builder()
            .llmCreateInput(input)
            .build()
            .onSuccess { llmWrapper = it }
            .map { }
    }

    override suspend fun generateTutorTurn(prompt: String): Result<TutorTurn> =
        generateRaw(prompt, useGrammar = true).mapCatching { TutorResponseParser.parse(it).getOrThrow() }

    override suspend fun generateFlashcards(prompt: String): Result<List<Flashcard>> =
        generateRaw(prompt, useGrammar = false).mapCatching { FlashcardResponseParser.parse(it).getOrThrow() }

    private suspend fun generateRaw(prompt: String, useGrammar: Boolean): Result<String> {
        val wrapper = llmWrapper ?: return Result.failure(IllegalStateException("LLM not initialized"))

        // Clear any session/KV-cache state left over from a previous turn - without this, a
        // second generation on the same handle behaves inconsistently (stale context bleeding
        // into the new prompt).
        wrapper.reset()

        // The qairt/NPU plugin rejects sampler features beyond basic temperature/top-p (it also
        // rejects a custom n_ctx and a real chat-message array - see initialize() and
        // PromptBuilder). Grammar-constrained decoding is no exception. TutorResponseParser's /
        // FlashcardResponseParser's regex fallback is the safety net for qairt; llama_cpp gets
        // the stricter grammar.
        val samplerConfig = if (isQairt || !useGrammar) {
            SamplerConfig(temperature = 0.2f, topP = 0.9f)
        } else {
            SamplerConfig(temperature = 0.2f, topP = 0.9f, grammarString = TutorGrammar.GBNF)
        }
        val config = GenerationConfig(maxTokens = 400, samplerConfig = samplerConfig)

        val builder = StringBuilder()
        var streamError: Throwable? = null
        wrapper.generateStreamFlow(prompt, config).collect { result ->
            when (result) {
                is LlmStreamResult.Token -> builder.append(result.text)
                is LlmStreamResult.Completed -> Unit
                is LlmStreamResult.Error -> streamError = result.throwable
            }
        }
        streamError?.let { return Result.failure(it) }

        return Result.success(builder.toString())
    }

    override fun isReady(): Boolean = llmWrapper != null

    override fun release() {
        llmWrapper?.close()
        llmWrapper = null
    }
}
