package com.skeler.scanely

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.skeler.scanely.core.common.CompositionLocals
import com.skeler.scanely.navigation.ScanelyNavigation
import com.skeler.scanely.ui.theme.ScanelyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocals {
                ScanelyTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ScanelyNavigation()
                    }
                }
            }
        }
    }
}