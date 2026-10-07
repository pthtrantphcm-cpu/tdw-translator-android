package vn.thuducwater.translator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF1FF),
    onPrimaryContainer = Color(0xFF1D3F91),
    secondary = Color(0xFF0E9F6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECFDF3),
    onSecondaryContainer = Color(0xFF05603A),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF6F7F9),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE3E7EE),
    error = Color(0xFFDC2626),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5B8DFF),
    onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFF1C2741),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF16301F),
    onSecondaryContainer = Color(0xFFCFF7DD),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE8EAEF),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE8EAEF),
    surfaceVariant = Color(0xFF1D212A),
    onSurfaceVariant = Color(0xFF9097A6),
    outline = Color(0xFF272C37),
    error = Color(0xFFF87171),
)

/**
 * Theme Sáng/Tối theo hệ thống, cùng bộ màu với bản máy tính (xanh dương #2563EB).
 * Không dùng màu động theo hình nền (Android 12+) để giao diện nhất quán, chuyên nghiệp trên mọi máy.
 */
@Composable
fun TdwTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
