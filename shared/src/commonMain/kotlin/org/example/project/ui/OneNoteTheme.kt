package org.example.project.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.time.Instant

val OneNotePurple = Color(0xFF7719AA)
val OneNotePurpleLight = Color(0xFF9B4DCA)
val OneNotePurpleDark = Color(0xFF5A1083)

val OneNoteSectionColors = listOf(
    "#7719AA" to "Purple",
    "#2B579A" to "Blue",
    "#008272" to "Teal",
    "#217346" to "Green",
    "#D24726" to "Orange",
    "#B4009E" to "Magenta",
    "#C50F1F" to "Crimson",
    "#4F6BED" to "Indigo"
)

private val OneNoteLightColors = lightColorScheme(
    primary = OneNotePurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1E4FA),
    onPrimaryContainer = Color(0xFF2D0446),
    secondary = Color(0xFF5D536B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E2F2),
    onSecondaryContainer = Color(0xFF1F1929),
    background = Color(0xFFF6F4F9),
    onBackground = Color(0xFF1B1820),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1820),
    surfaceVariant = Color(0xFFEEEAF4),
    onSurfaceVariant = Color(0xFF4B4456),
    outline = Color(0xFFD0C7DC),
    error = Color(0xFFBA1A1A)
)

private val OneNoteDarkColors = darkColorScheme(
    primary = Color(0xFFC084FC),
    onPrimary = Color(0xFF2B0047),
    primaryContainer = Color(0xFF4A186E),
    onPrimaryContainer = Color(0xFFF5E6FF),
    secondary = Color(0xFFCBBFD9),
    onSecondary = Color(0xFF2D2638),
    secondaryContainer = Color(0xFF332D3F),
    onSecondaryContainer = Color(0xFFEBE2F7),
    background = Color(0xFF121016),
    onBackground = Color(0xFFF0ECF6),
    surface = Color(0xFF1B1822),
    onSurface = Color(0xFFF0ECF6),
    surfaceVariant = Color(0xFF252130),
    onSurfaceVariant = Color(0xFFC5BDD2),
    outline = Color(0xFF453E54),
    error = Color(0xFFFFB4AB)
)

@Composable
fun OneNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) OneNoteDarkColors else OneNoteLightColors,
        content = content
    )
}

fun parseHexColor(hex: String?, fallback: Color = OneNotePurple): Color {
    if (hex.isNullOrBlank()) return fallback
    val cleaned = hex.trim().removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return fallback
    val argb = when (cleaned.length) {
        6 -> value or 0xFF000000L
        8 -> value
        else -> return fallback
    }
    return Color(argb)
}

/**
 * Pure Kotlin timestamp formatter for KMP compatibility.
 */
fun formatNoteTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Just now"
    // Civil date from epoch days (Howard Hinnant algorithm)
    val totalSeconds = epochMillis / 1000L
    val days = floorDiv(totalSeconds, 86400L)
    val secOfDay = (totalSeconds - days * 86400L).toInt()
    val hour = secOfDay / 3600
    val minute = (secOfDay % 3600) / 60

    val z = days + 719468L
    val era = (if (z >= 0) z else z - 146096L) / 146097L
    val doe = (z - era * 146097L).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    val year = y + (if (m <= 2) 1 else 0)

    val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val monthStr = monthNames[(m - 1).coerceIn(0, 11)]
    val amPm = if (hour >= 12) "PM" else "AM"
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val minStr = minute.toString().padStart(2, '0')
    return "$monthStr $d, $year • $h12:$minStr $amPm"
}

private fun floorDiv(a: Long, b: Long): Long {
    var r = a / b
    if ((a xor b) < 0 && r * b != a) r--
    return r
}
