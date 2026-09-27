package fr.thermostat6.app180.util

import androidx.compose.ui.graphics.Color
import fr.thermostat6.app180.ui.theme.Accent180

// ── String ────────────────────────────────────────────────────────────────────

private val HTML_TAG_REGEX = Regex("<[^>]+>")

/**
 * Supprime tous les tags HTML et décode les entités courantes.
 * Équivalent de String.stripHTML() (iOS / Extensions.swift).
 */
fun String.stripHtml(): String = HTML_TAG_REGEX.replace(this, "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&nbsp;", "\u00A0")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&rsquo;", "'")
    .replace("&laquo;", "«")
    .replace("&raquo;", "»")
    .replace("&eacute;", "é")
    .replace("&egrave;", "è")
    .replace("&ecirc;", "ê")
    .replace("&agrave;", "à")
    .replace("&ugrave;", "ù")
    .replace("&ccedil;", "ç")
    .trim()

// ── Color ─────────────────────────────────────────────────────────────────────

/**
 * Orange principal 180°C — alias de lecture du token de thème [Accent180]
 * (`ui/theme/Color.kt`), lui-même miroir de `Color.accent180`
 * (`apple/180/Extensions.swift:8`).
 *
 * Aucune valeur ici : un hexadécimal dupliqué finit toujours par diverger.
 *
 * Usage : Color.accent180
 */
val Color.Companion.accent180: Color
    get() = Accent180
