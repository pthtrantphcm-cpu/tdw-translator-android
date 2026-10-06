package vn.thuducwater.translator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6BFF),
    onPrimary = Color.White,
    secondary = Color(0xFF4C5F86),
    background = Color(0xFFF3F4F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6EBF5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DB0FF),
    onPrimary = Color(0xFF0D1020),
    secondary = Color(0xFFB4C4E6),
    background = Color(0xFF16181D),
    surface = Color(0xFF1F2128),
    surfaceVariant = Color(0xFF2B2F3A),
)

/** Theme Sáng/Tối theo hệ thống; Android 12+ dùng màu động theo hình nền. */
@Composable
fun TdwTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
