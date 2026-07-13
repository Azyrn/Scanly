package com.skeler.scanely.history.data

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
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
        }
    }

    fun getHistory(): List<HistoryItem> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val jsonString = historyFile.readText()
            val jsonArray = JSONArray(jsonString)
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
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
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
        historyFile.writeText(jsonArray.toString())
    }
}
