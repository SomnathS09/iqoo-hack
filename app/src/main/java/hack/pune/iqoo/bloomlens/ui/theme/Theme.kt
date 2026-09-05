package hack.pune.iqoo.bloomlens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BloomDarkColorScheme = darkColorScheme(
    primary = BloomGreen,
    onPrimary = BloomBackground,
    primaryContainer = BloomGreenContainer,
    onPrimaryContainer = BloomGreen,
    secondary = BloomPurple,
    onSecondary = BloomBackground,
    secondaryContainer = BloomPurpleContainer,
    onSecondaryContainer = BloomPurple,
    background = BloomBackground,
    onBackground = BloomOnSurface,
    surface = BloomSurface,
    onSurface = BloomOnSurface,
    error = BloomError,
)

private val BloomLightColorScheme = lightColorScheme(
    primary = BloomGreen,
    secondary = BloomPurple,
    background = androidx.compose.ui.graphics.Color(0xFFF5FBF8),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
)

@Composable
fun BloomLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) BloomDarkColorScheme else BloomLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BloomLensTypography,
        content = content,
    )
}