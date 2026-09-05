package hack.pune.iqoo.bloomlens.model

import android.content.Context
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.ModelManagerWrapper
import com.geniex.sdk.bean.HubSource
import com.geniex.sdk.bean.ModelPaths
import com.geniex.sdk.bean.ModelPullInput
import com.geniex.sdk.bean.ModelType
import hack.pune.iqoo.bloomlens.download.DirectDownloadEvent
import hack.pune.iqoo.bloomlens.download.DirectHttpDownloader
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Resolves and pulls an on-device LLM using GenieX's own model manager (native resumable
 * downloads) rather than a hand-rolled downloader. The published SDK has no hub-catalog query
 * API, so candidates from [ModelCandidates] are tried in order: small Hugging Face GGUF models
 * first via [HubSource.HUGGINGFACE], then Qualcomm AI Hub bundles via [HubSource.AIHUB]. If an
 * AI Hub candidate has a known-broken GenieX download (see [DirectDownloadOverrides]) it's
 * fetched directly instead and imported via [HubSource.LOCALFS].
 */
class GenieModelRepository(private val context: Context) {

    private val directDownloader = DirectHttpDownloader()

    private suspend fun initGenieXSdk(): Result<Unit> = suspendCancellableCoroutine { cont ->
        GenieXSdk.getInstance().init(
            context,
            object : GenieXSdk.InitCallback {
                override fun onSuccess() = cont.resume(Result.success(Unit))
                override fun onFailure(reason: String) = cont.resume(Result.failure(IllegalStateException(reason)))
            },
        )
    }

    private suspend fun findCachedCandidate(): String? {
        val cached = runCatching { ModelManagerWrapper.list() }.getOrDefault(emptyList())
        val allCandidates = ModelCandidates.huggingFacePreferred.map { it.repo } + ModelCandidates.aiHubPreferred
        return allCandidates.firstOrNull { candidate ->
            cached.any { it.equals(candidate, ignoreCase = true) || it.endsWith(candidate, ignoreCase = true) }
        }
    }

    private suspend fun buildCandidateInputs(): List<ModelPullInput> {
        val huggingFaceInputs = ModelCandidates.huggingFacePreferred.map { candidate ->
            ModelPullInput(
                model_name = candidate.repo,
                precision = candidate.precision,
                hub = HubSource.HUGGINGFACE,
                model_type = ModelType.LLM,
            )
        }
        val chipset = runCatching { ModelManagerWrapper.detectChipset() }.getOrNull()
        val aiHubInputs = if (chipset != null) {
            ModelCandidates.aiHubPreferred.map { name ->
                ModelPullInput(
                    model_name = name,
                    hub = HubSource.AIHUB,
                    chipset = chipset,
                    display_name = name,
                    model_type = ModelType.LLM,
                )
            }
        } else {
            emptyList()
        }
        return huggingFaceInputs + aiHubInputs
    }

    fun prepareModel(): Flow<ModelPullEvent> = flow {
        val sdkInit = initGenieXSdk()
        if (sdkInit.isFailure) {
            emit(ModelPullEvent.Failed(sdkInit.exceptionOrNull()?.message ?: "GenieX SDK init failed"))
            return@flow
        }
        val dataDir = File(context.filesDir, "geniex").apply { mkdirs() }
        val mgrInit = ModelManagerWrapper.init(dataDir.absolutePath)
        if (mgrInit.isFailure) {
            emit(ModelPullEvent.Failed(mgrInit.exceptionOrNull()?.message ?: "Model manager init failed"))
            return@flow
        }

        findCachedCandidate()?.let { cachedName ->
            ModelManagerWrapper.getPaths(cachedName)?.let { paths ->
                emit(ModelPullEvent.AlreadyReady(paths))
                return@flow
            }
        }

        var lastError = "No model candidates available"
        for (input in buildCandidateInputs()) {
            var succeeded = false
            var candidateError: String? = null
            ModelManagerWrapper.pullFlow(input).collect { event ->
                when (event) {
                    is ModelManagerWrapper.PullEvent.Progress -> {
                        val downloaded = event.files.sumOf { it.downloaded_bytes }
                        val total = event.files.sumOf { maxOf(it.total_bytes, 0L) }
                        val activeFile = event.files.lastOrNull { it.downloaded_bytes < it.total_bytes }
                            ?: event.files.lastOrNull()
                        emit(ModelPullEvent.Progress(downloaded, total, activeFile?.file_name.orEmpty()))
                    }

                    ModelManagerWrapper.PullEvent.Completed -> succeeded = true

                    is ModelManagerWrapper.PullEvent.Error -> candidateError = event.message
                }
            }

            if (succeeded) {
                val paths = resolveReadyPaths(input.model_name)
                if (paths != null) {
                    emit(ModelPullEvent.Ready(paths))
                    return@flow
                }
                lastError = "Model pulled but no paths found for ${input.model_name}"
                continue
            }

            val overrideUrl = DirectDownloadOverrides.urls[input.model_name]
            if (overrideUrl == null) {
                lastError = candidateError ?: "Unknown error pulling ${input.model_name}"
                continue
            }

            val directResult = tryDirectDownloadAndImport(input.model_name, overrideUrl)
            if (directResult != null) {
                emit(ModelPullEvent.Ready(directResult))
                return@flow
            }
            lastError = "Direct download fallback also failed for ${input.model_name}"
        }
        emit(ModelPullEvent.Failed(lastError))
    }

    /** Downloads [url] directly and imports it into GenieX's model store via [HubSource.LOCALFS]. */
    private suspend fun FlowCollector<ModelPullEvent>.tryDirectDownloadAndImport(
        modelName: String,
        url: String,
    ): ModelPaths? {
        val destination = File(File(context.filesDir, "direct_downloads").apply { mkdirs() }, "$modelName.zip")

        var downloadedFile: File? = null
        directDownloader.downloadFlow(url, destination).collect { event ->
            when (event) {
                is DirectDownloadEvent.Progress ->
                    emit(ModelPullEvent.Progress(event.downloadedBytes, event.totalBytes, modelName))

                is DirectDownloadEvent.Completed -> downloadedFile = event.file
                is DirectDownloadEvent.Failed -> Unit
            }
        }
        val file = downloadedFile ?: return null

        val importInput = ModelPullInput(
            model_name = modelName,
            hub = HubSource.LOCALFS,
            local_path = file.absolutePath,
            model_type = ModelType.LLM,
        )
        var imported = false
        ModelManagerWrapper.pullFlow(importInput).collect { event ->
            if (event is ModelManagerWrapper.PullEvent.Completed) imported = true
        }
        return if (imported) resolveReadyPaths(modelName) else null
    }

    private suspend fun resolveReadyPaths(modelName: String): ModelPaths? {
        val resolvedName = runCatching { ModelManagerWrapper.resolveAlias(modelName) }.getOrNull() ?: modelName
        return ModelManagerWrapper.getPaths(resolvedName)
    }
}
