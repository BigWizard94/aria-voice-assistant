package com.bigwizard.aria.stt

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import com.bigwizard.aria.data.remote.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Owns the on-disk Vosk model and a single, process-wide loaded [Model].
 *
 * The model is ~70 MB unpacked and ~50 MB in RAM, so on 1–2 GB Android Go
 * devices it must be loaded once and shared between the listener service and
 * the system RecognitionService instead of each keeping its own copy.
 * Callers [acquire] a reference and must [release] it when done.
 *
 * Downloads are atomic: the zip is unpacked into a temp directory, renamed
 * into place, and only then marked complete, so a download killed half way
 * can never leave a corrupt model that crashes the native loader.
 */
object VoskModelManager {

    private const val TAG = "VoskModelManager"
    private const val MODEL_BASE_URL = "https://alphacephei.com/vosk/models"
    private const val COMPLETE_MARKER = ".aria_complete"
    private const val MODELS_DIR = "vosk-models"

    private val installMutex = Mutex()
    private val lock = Any()

    private var model: Model? = null
    private var loadedName: String? = null
    private var refCount = 0

    fun modelDir(context: Context, modelName: String): File =
        File(File(context.filesDir, MODELS_DIR), modelName)

    /** Legacy location used by earlier builds (filesDir/<modelName>). */
    private fun legacyModelDir(context: Context, modelName: String): File =
        File(context.filesDir, modelName)

    fun isInstalled(context: Context, modelName: String): Boolean =
        File(modelDir(context, modelName), COMPLETE_MARKER).exists() ||
            migrateLegacyModel(context, modelName)

    /**
     * Ensures the model is on disk (downloading it if needed) and returns a
     * shared loaded [Model]. Must be balanced with [release].
     */
    suspend fun acquire(
        context: Context,
        modelName: String,
        onProgress: (Float?) -> Unit = {}
    ): Model = withContext(Dispatchers.IO) {
        if (!isInstalled(context, modelName)) {
            installMutex.withLock {
                if (!isInstalled(context, modelName)) download(context.applicationContext, modelName, onProgress)
            }
        }
        coroutineContext.ensureActive()
        synchronized(lock) {
            val current = model
            val loaded = if (current != null && loadedName == modelName) {
                current
            } else {
                current?.close()
                Model(modelDir(context, modelName).absolutePath).also {
                    model = it
                    loadedName = modelName
                }
            }
            refCount++
            loaded
        }
    }

    /** Drops one reference; the native model is freed when nobody holds it. */
    fun release() {
        synchronized(lock) {
            if (refCount > 0) refCount--
            if (refCount == 0) {
                model?.close()
                model = null
                loadedName = null
                Log.i(TAG, "Vosk model released")
            }
        }
    }

    // ── Download & unpack ──────────────────────────────────────────────────

    private suspend fun download(context: Context, modelName: String, onProgress: (Float?) -> Unit) {
        val zipFile = File(context.cacheDir, "$modelName.zip")
        val request = Request.Builder().url("$MODEL_BASE_URL/$modelName.zip").build()
        Log.i(TAG, "Downloading Vosk model $modelName")
        onProgress(0f)

        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Model download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Model download returned no data")
            val total = body.contentLength().takeIf { it > 0 }

            // Need room for the zip plus the unpacked model (~2x the zip).
            val needed = (total ?: 50L * 1024 * 1024) * 3
            if (freeBytes(context) < needed) {
                throw IOException("Not enough free storage for the speech model (needs about ${needed / (1024 * 1024)} MB)")
            }

            body.byteStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var lastReported = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        done += read
                        if (total != null) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress(done.toFloat() / total)
                            }
                        }
                    }
                }
            }
        }
        onProgress(null) // unpacking — indeterminate

        val root = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
        val tmpDir = File(root, ".tmp-$modelName")
        tmpDir.deleteRecursively()
        try {
            unzip(zipFile, tmpDir)
        } finally {
            zipFile.delete()
        }

        // Zips contain a single top-level "<modelName>/" folder.
        val unpacked = File(tmpDir, modelName).takeIf { it.isDirectory } ?: tmpDir
        if (!looksLikeModel(unpacked)) {
            tmpDir.deleteRecursively()
            throw IOException("Downloaded speech model is incomplete or corrupted")
        }
        val target = modelDir(context, modelName)
        target.deleteRecursively()
        if (!unpacked.renameTo(target)) {
            tmpDir.deleteRecursively()
            throw IOException("Could not install speech model")
        }
        tmpDir.deleteRecursively()
        File(target, COMPLETE_MARKER).createNewFile()
        Log.i(TAG, "Vosk model installed at $target")
    }

    /** Free space including cache the system would clear for us. */
    private fun freeBytes(context: Context): Long = try {
        val sm = context.getSystemService(StorageManager::class.java)
        sm.getAllocatableBytes(sm.getUuidForPath(context.filesDir))
    } catch (e: Exception) {
        context.filesDir.usableSpace
    }

    private suspend fun unzip(zipFile: File, targetDir: File) {
        val canonicalRoot = targetDir.canonicalPath + File.separator
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            val buffer = ByteArray(64 * 1024)
            var entry = zis.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                val outFile = File(targetDir, entry.name)
                // Zip-slip guard
                if (!outFile.canonicalPath.startsWith(canonicalRoot)) {
                    throw IOException("Invalid entry in model archive: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) fos.write(buffer, 0, len)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun looksLikeModel(dir: File): Boolean =
        File(dir, "am").isDirectory && File(dir, "conf").isDirectory && File(dir, "graph").isDirectory

    /** Adopts a model unpacked by an older build directly under filesDir. */
    private fun migrateLegacyModel(context: Context, modelName: String): Boolean {
        val legacy = legacyModelDir(context, modelName)
        if (!looksLikeModel(legacy) || !File(legacy, "am/final.mdl").exists()) return false
        val target = modelDir(context, modelName)
        target.parentFile?.mkdirs()
        target.deleteRecursively()
        return if (legacy.renameTo(target)) {
            File(target, COMPLETE_MARKER).createNewFile()
            true
        } else {
            false
        }
    }
}
