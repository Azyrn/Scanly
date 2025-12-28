package com.skeler.scanely

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.skeler.scanely.navigation.ScanelyNavigation
import com.skeler.scanely.ui.theme.ScanelyTheme
import com.skeler.scanely.ui.theme.ThemeMode
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Load preferences directly (no remember needed for the reference itself, but needed for the state)
            val prefs = getPreferences(MODE_PRIVATE)
            
            // Load initial state immediately
            // We use mutableStateOf with the loaded value directly to ensure it initializes correctly.
            var themeMode by remember { 
                val name = prefs.getString("theme_mode", ThemeMode.System.name) ?: ThemeMode.System.name
                val mode = try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.System }
                mutableStateOf(mode) 
            }
            
            val initialLangs = remember {
                prefs.getStringSet("ocr_langs", setOf("eng", "ara")) ?: setOf("eng", "ara")
            }
            var ocrLanguages by remember { mutableStateOf(initialLangs) }
            
            ScanelyTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScanelyNavigation(
                        currentTheme = themeMode,
                        onThemeChanged = { newMode ->
                            themeMode = newMode
                            // Commit immediately to ensure persistence
                            prefs.edit(commit = true) { putString("theme_mode", newMode.name) }
                        },
                        ocrLanguages = ocrLanguages,
                        onOcrLanguagesChanged = { newLangs ->
                            ocrLanguages = newLangs
                             // Commit immediately
                            prefs.edit(commit = true) { putStringSet("ocr_langs", newLangs) }
                        }
                    )
                }
            }
        }
    }
}