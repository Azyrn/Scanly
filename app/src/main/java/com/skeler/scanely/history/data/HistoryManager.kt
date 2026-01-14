package com.skeler.scanely.history.data

import android.content.Context
import android.net.Uri
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

/**
 * Manages scan history with persistent image storage.
 *
 * ULTRATHINK Image Persistence:
 * - Content URIs become invalid after app restart
 * - Solution: Copy image to internal app storage
 * - File path stored in JSON instead of content URI
 * - Images auto-cleaned when history item deleted
 */
@Singleton
class HistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val historyFile = File(context.filesDir, "scan_history.json")
    private val imagesDir = File(context.filesDir, "history_images").also { it.mkdirs() }

    /**
     * Save a history item with persistent image copy.
     *
     * @param text The extracted/translated text
     * @param imageUri Original content URI of the image
     */
    fun saveItem(text: String, imageUri: String) {
        // Copy image to internal storage for persistence
        val persistentUri = copyImageToInternalStorage(imageUri)
        
        val currentList = getHistory().toMutableList()
        currentList.add(0, HistoryItem(text = text, imageUri = persistentUri))
        // Keep only last 50 items (delete old images too)
        if (currentList.size > 50) {
            val removed = currentList.drop(50)
            removed.forEach { item -> deleteImage(item.imageUri) }
        }
        val trimmedList = currentList.take(50)
        saveHistory(trimmedList)
    }

    /**
     * Copy image from content URI to internal storage.
     * Returns file:// URI that persists across app restarts.
     */
    private fun copyImageToInternalStorage(sourceUri: String): String {
        return try {
            val uri = Uri.parse(sourceUri)
            val fileName = "${UUID.randomUUID()}.jpg"
            val destFile = File(imagesDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Return file:// URI
            destFile.toUri().toString()
        } catch (e: Exception) {
            // If copy fails, return original URI (may not persist)
            Log.w(TAG, "Image copy failed, using original URI", e)
            sourceUri
        }
    }

    /**
     * Delete image file from internal storage.
     */
    private fun deleteImage(imageUri: String) {
        try {
            if (imageUri.startsWith("file://")) {
                val file = File(Uri.parse(imageUri).path ?: return)
                if (file.exists() && file.parentFile == imagesDir) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore deletion errors
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
        // Delete all images
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
