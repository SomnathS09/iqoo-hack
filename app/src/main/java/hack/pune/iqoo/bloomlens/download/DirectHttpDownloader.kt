package hack.pune.iqoo.bloomlens.download

import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface DirectDownloadEvent {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : DirectDownloadEvent
    data class Completed(val file: File) : DirectDownloadEvent
    data class Failed(val message: String) : DirectDownloadEvent
}

/**
 * Plain resumable HTTP downloader, used when a hub's own downloader can't be trusted for a
 * given asset (see [hack.pune.iqoo.bloomlens.model.DirectDownloadOverrides]). Derives the true
 * file size from the HTTP response itself (Content-Length, or Content-Range on a 206) rather
 * than any hub manifest, and resumes from the existing partial file's length on retry.
 */
class DirectHttpDownloader(private val client: OkHttpClient = OkHttpClient()) {

    fun downloadFlow(url: String, destination: File): Flow<DirectDownloadEvent> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            runCatching { runDownloadLoop(url, destination) }
                .onFailure { trySend(DirectDownloadEvent.Failed(it.message ?: "Download failed")) }
            close()
        }
        awaitClose { job.cancel() }
    }

    private suspend fun ProducerScope<DirectDownloadEvent>.runDownloadLoop(
        url: String,
        destination: File,
    ) {
        val partFile = File(destination.parentFile, "${destination.name}.part")
        partFile.parentFile?.mkdirs()

        var attempts = 0
        while (isActive) {
            attempts++
            if (attempts > MAX_ATTEMPTS) error("Exceeded $MAX_ATTEMPTS download attempts for $url")

            val existingBytes = if (partFile.exists()) partFile.length() else 0L
            val requestBuilder = Request.Builder().url(url)
            if (existingBytes > 0) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} downloading $url")
                val body = response.body ?: error("Empty response body from $url")

                val totalBytes = if (response.code == 206) {
                    response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                        ?: error("Missing Content-Range on partial response from $url")
                } else {
                    body.contentLength().takeIf { it >= 0 } ?: error("Unknown content length from $url")
                }

                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.seek(existingBytes)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        var downloaded = existingBytes
                        var sinceLastEmit = 0L
                        while (isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            downloaded += read
                            sinceLastEmit += read
                            if (sinceLastEmit >= PROGRESS_EMIT_THRESHOLD_BYTES) {
                                trySend(DirectDownloadEvent.Progress(downloaded, totalBytes))
                                sinceLastEmit = 0
                            }
                        }
                    }
                }

                if (partFile.length() >= totalBytes) {
                    trySend(DirectDownloadEvent.Progress(partFile.length(), totalBytes))
                    partFile.renameTo(destination)
                    trySend(DirectDownloadEvent.Completed(destination))
                    return
                }
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val PROGRESS_EMIT_THRESHOLD_BYTES = 1_000_000L
    }
}
