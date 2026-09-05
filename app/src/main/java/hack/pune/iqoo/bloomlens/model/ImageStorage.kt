package hack.pune.iqoo.bloomlens.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/** Persists captured problem photos to app-private storage so history survives app restarts. */
class ImageStorage(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "session_images").apply { mkdirs() }

    fun save(bitmap: Bitmap, sessionId: String): String? = runCatching {
        val file = File(dir, "$sessionId.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        file.absolutePath
    }.getOrNull()

    fun load(path: String): Bitmap? = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
}
