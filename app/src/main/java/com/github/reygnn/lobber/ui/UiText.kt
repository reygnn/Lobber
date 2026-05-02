package com.github.reygnn.lobber.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * UI-Text, der entweder als String-Ressource oder als bereits aufgelöster
 * String getragen wird. ViewModels emittieren `UiText` statt direkter
 * `String`s, sodass Composables die Ressource erst beim Rendern (mit dem
 * aktuellen Locale des Configurations) auflösen.
 *
 * - [Resource] für statische, übersetzbare Texte aus `strings.xml`.
 * - [Literal] für bereits zur Laufzeit zusammengebaute Strings (z. B. eine
 *   Cause-Chain oder eine Server-Fehlermeldung) — werden nicht übersetzt.
 */
sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Literal(val value: String) : UiText
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Resource -> if (args.isEmpty()) {
        context.getString(id)
    } else {
        context.getString(id, *args.toTypedArray())
    }
    is UiText.Literal -> value
}

@Composable
fun UiText.resolve(): String = resolve(LocalContext.current)
