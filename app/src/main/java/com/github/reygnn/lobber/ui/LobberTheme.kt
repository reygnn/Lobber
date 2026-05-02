package com.github.reygnn.lobber.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material 3 wrapper, der je nach System-Setting Light- oder Dark-Color-Scheme
 * benutzt. Default-Schemes — wenn Lobber später ein eigenes Branding-Color-
 * Scheme bekommt, hier `lightColorScheme(primary = …)` / `darkColorScheme(…)`
 * austauschen.
 */
@Composable
fun LobberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}
