package app.khata.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0B6E4F), onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2CF), onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4C6358), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9DA), onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF7A5900), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA6), onTertiaryContainer = Color(0xFF261900),
    background = Color(0xFFF6FBF7), onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF6FBF7), onSurface = Color(0xFF171D1A),
    surfaceDim = Color(0xFFD6DBD8), surfaceBright = Color(0xFFF6FBF7),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F5F1),
    surfaceContainer = Color(0xFFEAEFEB), surfaceContainerHigh = Color(0xFFE5EAE5),
    surfaceContainerHighest = Color(0xFFDFE4E0),
    surfaceVariant = Color(0xFFDBE5DE), onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973), outlineVariant = Color(0xFFBFC9C2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AD6B4), onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF00513A), onPrimaryContainer = Color(0xFFA6F2CF),
    secondary = Color(0xFFB3CCBF), onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B41), onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFF5BE48), onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4300), onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDEE4DF),
    surfaceDim = Color(0xFF0F1512), surfaceBright = Color(0xFF353B37),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E), surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938C), outlineVariant = Color(0xFF404943),
)

@Composable
fun KhataTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(),
        content = content,
    )
}
