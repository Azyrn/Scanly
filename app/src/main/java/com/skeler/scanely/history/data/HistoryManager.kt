package com.skeler.scanely.history.data

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HistoryManager"

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val imageUri: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** Null on malformed JSON — distinct from an empty-but-valid `[]`. */
internal fun parseHistoryJson(json: String): List<HistoryItem>? {
    return try {
        val jsonArray = JSONArray(json)
        val items = mutableListOf<HistoryItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            items.add(
                HistoryItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    text = obj.optString("text"),
                    imageUri = obj.optString("imageUri"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
        items
    } catch (_: JSONException) {
        null
    }
}

internal fun historyToJson(items: List<HistoryItem>): String {
    val jsonArray = JSONArray()
    items.forEach { item ->
        val obj = JSONObject().apply {
            put("id", item.id)
            put("text", item.text)
            put("imageUri", item.imageUri)
            put("timestamp", item.timestamp)
        }
        jsonArray.put(obj)
    }
    return jsonArray.toString()
}

@Singleton
class HistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val historyFile = File(context.filesDir, "scan_history.json")
    private val imagesDir = File(context.filesDir, "history_images").also { it.mkdirs() }

    fun saveItem(text: String, imageUri: String): HistoryItem {
        val persistentUri = copyImageToInternalStorage(imageUri)

        val item = HistoryItem(text = text, imageUri = persistentUri)
        val currentList = getHistory().toMutableList()
        currentList.add(0, item)
        if (currentList.size > 50) {
            val removed = currentList.drop(50)
            removed.forEach { old -> deleteImage(old.imageUri) }
        }
        val trimmedList = currentList.take(50)
        saveHistory(trimmedList)
        return item
    }

    fun updateItemText(id: String, text: String) {
        if (id.isBlank()) return
        val currentList = getHistory().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return
        currentList[index] = currentList[index].copy(text = text)
        saveHistory(currentList)
    }

    private fun copyImageToInternalStorage(sourceUri: String): String {
        return try {
            val uri = sourceUri.toUri()
            val fileName = "${UUID.randomUUID()}.jpg"
            val destFile = File(imagesDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            destFile.toUri().toString()
        } catch (e: Exception) {
            Log.w(TAG, "Image copy failed, using original URI", e)
            sourceUri
        }
    }

    private fun deleteImage(imageUri: String) {
        try {
            if (imageUri.startsWith("file://")) {
                val file = File(imageUri.toUri().path ?: return)
                if (file.exists() && file.parentFile == imagesDir) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Best-effort image cleanup failed for $imageUri", e)
        }
    }

    fun getHistory(): List<HistoryItem> {
        if (!historyFile.exists()) return emptyList()
        val jsonString = try {
            historyFile.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Could not read history file", e)
            return emptyList()
        }
        val items = parseHistoryJson(jsonString)
        if (items == null) {
            // Corrupt (e.g. process killed mid-write). Preserve the bytes so the
            // next saveItem() doesn't overwrite them with an empty list.
            Log.e(TAG, "History unreadable, preserving as scan_history.corrupt.json")
            historyFile.renameTo(File(context.filesDir, "scan_history.corrupt.json"))
            return emptyList()
        }
        return items
    }

    fun clearHistory() {
        imagesDir.listFiles()?.forEach { it.delete() }

        if (historyFile.exists()) {
            historyFile.delete()
        }
    }

    fun deleteItem(id: String) {
        val currentList = getHistory().toMutableList()
        val item = currentList.find { it.id == id }
        if (item != null) {
            deleteImage(item.imageUri)
            currentList.removeAll { it.id == id }
            saveHistory(currentList)
        }
    }

    private fun saveHistory(items: List<HistoryItem>) {
        // Write-then-rename keeps the old file intact if we're killed mid-write.
        val tmpFile = File(context.filesDir, "scan_history.json.tmp")
        tmpFile.writeText(historyToJson(items))
        if (!tmpFile.renameTo(historyFile)) {
            Log.e(TAG, "Could not move new history into place")
            tmpFile.delete()
        }
    }
}
