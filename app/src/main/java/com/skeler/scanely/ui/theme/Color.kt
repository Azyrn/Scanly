@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color

@RequiresApi(Build.VERSION_CODES.S)
fun highContrastDynamicDarkColorScheme(context: Context): ColorScheme {
    return dynamicDarkColorScheme(context = context).copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = dynamicDarkColorScheme(context).surfaceContainerLowest,
        surfaceContainer = dynamicDarkColorScheme(context).surfaceContainerLow,
        surfaceContainerHigh = dynamicDarkColorScheme(context).surfaceContainer,
        surfaceContainerHighest = dynamicDarkColorScheme(context).surfaceContainerHigh,
    )
}