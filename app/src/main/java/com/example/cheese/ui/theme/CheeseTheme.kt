package com.example.cheese.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Static fallback color schemes for devices running API < 31 (Android 11 and below)
 * where Material You dynamic color is unavailable.
 *
 * Uses the project's existing Purple palette defined in Color.kt for visual
 * consistency with the original template. On API 31+ devices, these are
 * bypassed in favor of wallpaper-derived dynamic colors.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * Application theme wrapper.
 *
 * Provides Material 3 theming with two strategies:
 * - **API 31+ (Android 12+):** Dynamic color derived from the device wallpaper,
 *   providing personalised visual coherence at zero designer overhead.
 * - **API 26–30:** Falls back to static light/dark color schemes using the
 *   Purple palette, ensuring WCAG AA contrast levels on older fleet devices
 *   (Pocophone F1, Redmi Note 8 Pro).
 *
 * Typography uses the custom scale defined in Type.kt. Shape and other tokens
 * are left at Material 3 defaults to comply with the project constraint of
 * using standard M3 components.
 */
@Composable
fun CheeseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color requires API 31+ (Android 12 / Material You)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
