package com.example.cheese.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Application theme wrapper.
 *
 * Uses Material 3 dynamic color (Android 12+ / API 31+) to derive a
 * color scheme from the device wallpaper, providing personalised visual
 * coherence at zero designer overhead.  On API < 31 it falls back to the
 * default Material 3 baseline scheme — no bespoke color system needed.
 *
 * Typography and shape are left at Material 3 defaults to comply with the
 * project constraint of using standard M3 components without custom shapes.
 */
@Composable
fun CheeseTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Dynamic color is available on API 31+ (Android 12+); project minSdk = 34
    // so dynamic color is always available — the else branch is a safe fallback.
    val colorScheme = dynamicLightColorScheme(context)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
