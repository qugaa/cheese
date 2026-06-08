package com.example.cheese.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay

/**
 * Welcome Splash Screen — temporary entry view.
 *
 * HCI rationale:
 * - The splash provides a cognitive "landing pad" that sets user expectations
 *   before the primary task UI loads (Norman: Gulf of Evaluation reduction).
 * - The spring-curve fade-in (800ms) leverages perceptual continuity — a smooth
 *   alpha transition is perceived as more polished than an abrupt appearance,
 *   increasing perceived quality (Aesthetic-Usability Effect).
 * - The 1.5s hold ensures the brand registers even at peripheral attention levels
 *   without blocking task completion (minimal frustration threshold ~2s).
 *
 * @param onTimeout Called when the splash duration has elapsed and the app
 *                  should navigate to the Organizer screen.
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // Animatable gives us fine-grained control over the spring curve
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Animate alpha from 0% to 100% over ~800ms using a smooth spring curve.
        // Spring with no bounce (dampingRatio = 1.0) and moderate stiffness
        // produces a natural ease-in that settles cleanly at 1.0.
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        // Hold for 1.5 seconds after the animation completes
        delay(1500L)
        onTimeout()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Cheese",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(alpha.value)
            )
        }
    }
}
