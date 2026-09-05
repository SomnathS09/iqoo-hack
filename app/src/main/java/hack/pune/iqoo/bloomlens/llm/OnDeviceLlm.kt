package hack.pune.iqoo.bloomlens.llm

import com.geniex.sdk.bean.ModelPaths

interface OnDeviceLlm {
    suspend fun initialize(paths: ModelPaths): Result<Unit>
    suspend fun generateTutorTurn(prompt: String): Result<TutorTurn>
    suspend fun generateFlashcards(prompt: String): Result<List<Flashcard>>
    fun isReady(): Boolean
    fun release()
}
