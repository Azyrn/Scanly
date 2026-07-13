package com.skeler.scanely.core.ocr.paddle

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PaddleModelStore"
private const val HF = "https://huggingface.co/PaddlePaddle"

sealed interface PackState {
    data object Bundled : PackState
    data object Installed : PackState
    data object Missing : PackState
    data class Downloading(val progress: Float) : PackState
    data class Failed(val message: String) : PackState
}

/** Bundled assets + on-demand script packs. Packs are usable offline once installed. */
@Singleton
class PaddleModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {

    // context.filesDir stats/creates the dir on every call; lazy keeps the
    // constructor free of disk I/O (eager Hilt injection lands on main).
    private val packsDir by lazy { File(context.filesDir, "ocr_packs") }
    private val uvdocFile by lazy { File(context.filesDir, "ocr_extras/uvdoc.onnx") }
    private val tableFile by lazy { File(context.filesDir, "ocr_extras/slanet.onnx") }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<ScriptPack, PackState>>(
        ScriptPack.entries.associateWith { pack ->
            if (pack.isBundled) PackState.Bundled else PackState.Missing
        }
    )
    val states: StateFlow<Map<ScriptPack, PackState>> = _states.asStateFlow()

    private val _uvdocState = MutableStateFlow<PackState>(PackState.Missing)
    val uvdocState: StateFlow<PackState> = _uvdocState.asStateFlow()

    private val _tableState = MutableStateFlow<PackState>(PackState.Missing)
    val tableState: StateFlow<PackState> = _tableState.asStateFlow()

    init {
        // Eager Hilt injection constructs this on the main thread; the exists() sweep
        // belongs on IO. Only Missing entries are upgraded, so states set by an
        // in-flight download are never clobbered.
        scope.launch {
            _states.update { map ->
                map.mapValues { (pack, state) ->
                    if (state is PackState.Missing && isInstalled(pack)) PackState.Installed else state
                }
            }
            if (hasUvdoc() && _uvdocState.value is PackState.Missing) {
                _uvdocState.value = PackState.Installed
            }
            if (hasTable() && _tableState.value is PackState.Missing) {
                _tableState.value = PackState.Installed
            }
        }
    }

    fun asset(name: String): ByteArray = context.assets.open(name).use { it.readBytes() }

    fun hasUvdoc(): Boolean = uvdocFile.exists()

    fun uvdocModel(): ByteArray = uvdocFile.readBytes()

    /**
     * Runs on the store's own scope, so leaving the settings screen doesn't cancel a download
     * half-way. [onInstalled] runs only once the model is verified and on disk.
     */
    fun downloadUvdocAsync(onInstalled: suspend () -> Unit = {}) {
        scope.launch { if (downloadUvdoc().isSuccess) onInstalled() }
    }

    fun downloadTableAsync() {
        scope.launch { downloadTable() }
    }

    fun downloadAsync(pack: ScriptPack) {
        scope.launch { download(pack) }
    }

    /** 30 MB dewarper, opt-in — downloaded rather than bundled. */
    suspend fun downloadUvdoc(): Result<Unit> = withContext(Dispatchers.IO) {
        if (hasUvdoc()) return@withContext Result.success(Unit)
        // A second tap must not start a second writer on the same file, nor report success
        // for a download it didn't make.
        if (_uvdocState.value is PackState.Downloading) {
            return@withContext Result.failure(IllegalStateException("Already downloading"))
        }
        _uvdocState.value = PackState.Downloading(0f)
        runCatching {
            downloadVerified("UVDoc_onnx", uvdocFile) {
                _uvdocState.value = PackState.Downloading(it)
            }
            _uvdocState.value = PackState.Installed
        }.onFailure { e ->
            Log.e(TAG, "UVDoc download failed", e)
            uvdocFile.delete()
            _uvdocState.value = PackState.Failed(e.message ?: "Download failed")
        }.map { }
    }

    fun deleteUvdoc() {
        uvdocFile.delete()
        _uvdocState.value = PackState.Missing
    }

    fun hasTable(): Boolean = tableFile.exists()

    fun tableModel(): ByteArray = tableFile.readBytes()

    /** 8 MB SLANet_plus table structure model, opt-in — downloaded rather than bundled. */
    suspend fun downloadTable(): Result<Unit> = withContext(Dispatchers.IO) {
        if (hasTable()) return@withContext Result.success(Unit)
        if (_tableState.value is PackState.Downloading) {
            return@withContext Result.failure(IllegalStateException("Already downloading"))
        }
        _tableState.value = PackState.Downloading(0f)
        runCatching {
            downloadVerified("SLANet_plus_onnx", tableFile) {
                _tableState.value = PackState.Downloading(it)
            }
            _tableState.value = PackState.Installed
        }.onFailure { e ->
            Log.e(TAG, "Table model download failed", e)
            tableFile.delete()
            _tableState.value = PackState.Failed(e.message ?: "Download failed")
        }.map { }
    }

    fun deleteTable() {
        tableFile.delete()
        _tableState.value = PackState.Missing
    }

    fun isInstalled(pack: ScriptPack): Boolean =
        modelFile(pack).exists() && dictFile(pack).exists()

    fun isAvailable(pack: ScriptPack): Boolean = pack.isBundled || isInstalled(pack)

    /** Rec model bytes + charset for the pack, falling back to the bundled universal model. */
    fun recModel(pack: ScriptPack): Pair<ByteArray, List<String>> {
        if (pack.isBundled) {
            return asset(pack.bundledModel!!) to charset(asset(pack.bundledDict!!).decodeToString())
        }
        if (!isInstalled(pack)) return recModel(ScriptPack.DEFAULT)
        return modelFile(pack).readBytes() to charset(dictFile(pack).readText())
    }

    suspend fun download(pack: ScriptPack): Result<Unit> = withContext(Dispatchers.IO) {
        val repo = pack.repo ?: return@withContext Result.success(Unit)
        val dir = File(packsDir, pack.id).apply { mkdirs() }
        setState(pack, PackState.Downloading(0f))

        runCatching {
            val yml = fetch("$HF/$repo/resolve/main/inference.yml").decodeToString()
            val chars = parseYamlDict(yml)
            check(chars.size > 10) { "Empty charset" }
            setState(pack, PackState.Downloading(0.1f))

            downloadVerified(repo, modelFile(pack)) { p ->
                setState(pack, PackState.Downloading(0.1f + p * 0.9f))
            }
            dictFile(pack).writeText(chars.joinToString("\n"))
            setState(pack, PackState.Installed)
        }.onFailure { e ->
            Log.e(TAG, "Pack download failed: ${pack.id}", e)
            dir.deleteRecursively()
            setState(pack, PackState.Failed(e.message ?: "Download failed"))
        }.map { }
    }

    fun delete(pack: ScriptPack) {
        if (pack.isBundled) return
        File(packsDir, pack.id).deleteRecursively()
        setState(pack, PackState.Missing)
    }

    private fun fetch(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return response.body.bytes()
        }
    }

    /**
     * Streams the repo's inference.onnx to [dest], hashing on the fly and verifying
     * size + SHA-256 against the repo's own LFS pointer, so truncated or corrupted
     * transfers can never install. Nothing is buffered fully in memory.
     */
    private fun downloadVerified(repo: String, dest: File, onProgress: (Float) -> Unit) {
        val pointer = parseLfsPointer(fetch("$HF/$repo/raw/main/inference.onnx").decodeToString())
            ?: error("No LFS metadata for $repo")
        check(pointer.size > 1_000_000) { "Model too small" }

        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        var read = 0L
        try {
            val request = Request.Builder().url("$HF/$repo/resolve/main/inference.onnx").build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = response.body
                tmp.outputStream().use { out ->
                    body.byteStream().use { stream ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = stream.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            read += n
                            onProgress((read.toFloat() / pointer.size).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            check(read == pointer.size) { "Size mismatch: got $read, expected ${pointer.size}" }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            check(sha == pointer.sha256) { "SHA-256 mismatch" }
            dest.delete()
            check(tmp.renameTo(dest)) { "Could not move verified model into place" }
        } finally {
            tmp.delete()
        }
    }

    private fun setState(pack: ScriptPack, state: PackState) =
        _states.update { it + (pack to state) }

    private fun modelFile(pack: ScriptPack) = File(File(packsDir, pack.id), "rec.onnx")
    private fun dictFile(pack: ScriptPack) = File(File(packsDir, pack.id), "dict.txt")

    data class LfsPointer(val sha256: String, val size: Long)

    companion object {

        /** Git LFS pointer text: "oid sha256:<hex>" + "size <bytes>" lines. */
        @VisibleForTesting
        fun parseLfsPointer(text: String): LfsPointer? {
            val sha = Regex("oid sha256:([0-9a-f]{64})").find(text)?.groupValues?.get(1)
                ?: return null
            val size = Regex("size (\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull()
                ?: return null
            return LfsPointer(sha, size)
        }

        /** CTC classes: blank + dict + space, matching PaddleOCR's CTCLabelDecode. */
        fun charset(dict: String): List<String> =
            buildList {
                add("")
                dict.lineSequence().forEach { if (it.isNotEmpty()) add(it) }
                add(" ")
            }

        /** character_dict entries out of PaddleX inference.yml. */
        fun parseYamlDict(yml: String): List<String> {
            val body = yml.substringAfter("character_dict:", "")
            if (body.isEmpty()) return emptyList()
            val out = ArrayList<String>(8000)
            for (raw in body.lineSequence()) {
                if (raw.isBlank()) continue
                val trimmed = raw.trimStart()
                if (!trimmed.startsWith("- ")) {
                    if (!raw.startsWith(" ")) break else continue
                }
                var v = trimmed.removePrefix("- ").trimEnd()
                if (v.length >= 2 && v.first() == '\'' && v.last() == '\'') {
                    v = v.substring(1, v.length - 1).replace("''", "'")
                } else if (v.length >= 2 && v.first() == '"' && v.last() == '"') {
                    v = v.substring(1, v.length - 1)
                }
                out.add(v.ifEmpty { " " })
            }
            return out
        }
    }
}
