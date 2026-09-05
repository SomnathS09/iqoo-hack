package hack.pune.iqoo.bloomlens.model

import com.geniex.sdk.bean.ModelPaths

sealed interface ModelPullEvent {
    data class AlreadyReady(val paths: ModelPaths) : ModelPullEvent
    data class Progress(val downloadedBytes: Long, val totalBytes: Long, val fileName: String) : ModelPullEvent
    data class Ready(val paths: ModelPaths) : ModelPullEvent
    data class Failed(val message: String) : ModelPullEvent
}