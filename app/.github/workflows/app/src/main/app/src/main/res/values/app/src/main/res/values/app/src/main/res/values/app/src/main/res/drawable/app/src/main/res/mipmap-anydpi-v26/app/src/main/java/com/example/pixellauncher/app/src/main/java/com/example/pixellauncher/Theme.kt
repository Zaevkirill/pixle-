package com.example.pixellauncher

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

fun pixelColorScheme(ctx: Context, dark: Boolean): ColorScheme = when {
    Build.VERSION.SDK_INT >= 31 ->
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    dark -> darkColorScheme(
        primary = Color(0xFF8AB4F8),
        primaryContainer = Color(0xFF1B3A6B),
        onPrimaryContainer = Color(0xFFD3E3FD)
    )
    else -> lightColorScheme(
        primary = Color(0xFF1A73E8),
        primaryContainer = Color(0xFFD3E3FD),
        onPrimaryContainer = Color(0xFF041E49)
    )
}

@Composable
fun PixelTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = forceDark || isSystemInDarkTheme()
    MaterialTheme(colorScheme = pixelColorScheme(ctx, dark), content = content)
}
