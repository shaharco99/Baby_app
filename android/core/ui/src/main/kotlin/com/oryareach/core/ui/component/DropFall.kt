package com.oryareach.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * One burst of falling drops.
 *
 * [id] is what makes a second burst a second burst: the animation is keyed to it, so firing
 * twice restarts the drops rather than leaving the first run to finish alone. [count] is how
 * many fall.
 */
@Immutable
data class DropBurst(val id: Long, val count: Int)

/** Long enough to read as falling, short enough not to sit in front of the log. */
private const val FALL_MILLIS = 1_700

/** Where a drop starts fading, as a fraction of its own fall. */
private const val FADE_FROM = 0.65f

/**
 * The drops: a short fall of milk down the screen when a session is put away.
 *
 * Drawn rather than animated with composables — a dozen moving `Box`es would each be a layout
 * pass, while this is one canvas redrawn against a single clock. It draws on top of everything and
 * takes no touches, so pressing on through it while it falls works normally.
 *
 * Every drop's lane, size, drift and head start come from [DropBurst.id], so a burst looks
 * scattered but never reshuffles mid-fall, and the next burst is scattered differently.
 */
@Composable
fun DropFall(burst: DropBurst, onFinished: () -> Unit) {
    val fall = remember(burst.id) { Animatable(0f) }
    val seeds = remember(burst.id) {
        val random = Random(burst.id)
        List(burst.count) {
            DropSeed(
                lane = random.nextFloat(),
                scale = 0.7f + random.nextFloat() * 0.8f,
                delay = random.nextFloat() * 0.45f,
                drift = (random.nextFloat() - 0.5f) * 0.08f,
            )
        }
    }

    LaunchedEffect(burst.id) {
        fall.animateTo(1f, tween(durationMillis = FALL_MILLIS, easing = LinearEasing))
        onFinished()
    }

    val milk = MaterialTheme.colorScheme.surfaceBright
    val rim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (seed in seeds) {
            // Each drop's own progress through its own window, so they do not fall in step.
            val span = 1f - seed.delay
            val t = ((fall.value - seed.delay) / span).coerceIn(0f, 1f)
            if (t <= 0f) continue

            val radius = size.width * 0.011f * seed.scale
            // Accelerating, like something actually falling, and fading over the last third.
            val y = -radius * 3f + (size.height * 0.78f + radius * 3f) * t * t
            val x = size.width * (0.08f + seed.lane * 0.84f) +
                size.width * seed.drift * sin(t * PI.toFloat())
            val alpha = if (t < FADE_FROM) 1f else 1f - (t - FADE_FROM) / (1f - FADE_FROM)

            val drop = teardrop(x, y, radius)
            drawPath(drop, color = milk, alpha = alpha)
            drawPath(drop, color = rim, alpha = alpha, style = Stroke(width = radius * 0.18f))
        }
    }
}

/** A drop: round at the bottom, drawn out to a point at the top, falling point-first. */
private fun teardrop(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy - r * 2.2f)
    cubicTo(cx + r * 0.9f, cy - r * 1.1f, cx + r, cy + r * 0.35f, cx, cy + r)
    cubicTo(cx - r, cy + r * 0.35f, cx - r * 0.9f, cy - r * 1.1f, cx, cy - r * 2.2f)
    close()
}

private data class DropSeed(
    val lane: Float,
    val scale: Float,
    val delay: Float,
    val drift: Float,
)
