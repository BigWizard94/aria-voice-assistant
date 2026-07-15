package com.bigwizard.aria.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigwizard.aria.data.model.AssistantState
import com.bigwizard.aria.ui.theme.*
import kotlin.math.*

/**
 * AriaOrb — The animated visual core of the assistant UI.
 *
 * States:
 *  • Idle      → Slow gentle pulse, soft glow
 *  • Listening → Fast ripple waves, bright cyan
 *  • Processing → Rotating arcs, violet spin
 *  • Speaking  → Bouncing waveform bars
 *  • Error     → Red pulse
 */
@Composable
fun AriaOrb(
    state: AssistantState,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    // ── Animations ────────────────────────────────────────────────────────────

    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    // Pulse scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue  = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    is AssistantState.Listening   -> 600
                    is AssistantState.Processing  -> 800
                    is AssistantState.Speaking    -> 400
                    else                          -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Rotation for processing state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    // Ripple alpha
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    // Ripple radius
    val rippleRadius by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleRadius"
    )

    // Wave bars for speaking
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing)
        ),
        label = "wave"
    )

    // ── Colors by state ───────────────────────────────────────────────────────

    val primaryColor = when (state) {
        is AssistantState.Idle       -> AriaViolet
        is AssistantState.Listening  -> AriaCyan
        is AssistantState.Processing -> AriaVioletLight
        is AssistantState.Speaking   -> AriaCyan
        is AssistantState.Error      -> AriaError
    }

    val secondaryColor = when (state) {
        is AssistantState.Listening  -> AriaViolet
        is AssistantState.Speaking   -> AriaVioletLight
        else                         -> AriaCyanDark
    }

    // ── Canvas Drawing ────────────────────────────────────────────────────────

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f * 0.6f

        // Outer glow
        drawGlow(center, radius * 1.4f, primaryColor, alpha = 0.15f)
        drawGlow(center, radius * 1.2f, primaryColor, alpha = 0.25f)

        // Ripple waves (listening state)
        if (state is AssistantState.Listening) {
            drawCircle(
                color  = primaryColor.copy(alpha = rippleAlpha * 0.5f),
                radius = radius * (1f + rippleRadius * 0.8f),
                center = center
            )
            drawCircle(
                color  = primaryColor.copy(alpha = rippleAlpha * 0.3f),
                radius = radius * (1f + rippleRadius * 1.4f),
                center = center
            )
        }

        // Rotating arcs (processing state)
        if (state is AssistantState.Processing) {
            rotate(rotation, pivot = center) {
                drawArc(
                    color      = primaryColor.copy(alpha = 0.8f),
                    startAngle = 0f,
                    sweepAngle = 120f,
                    useCenter  = false,
                    topLeft    = Offset(center.x - radius * 1.1f, center.y - radius * 1.1f),
                    size       = androidx.compose.ui.geometry.Size(radius * 2.2f, radius * 2.2f),
                    style      = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                )
                drawArc(
                    color      = secondaryColor.copy(alpha = 0.6f),
                    startAngle = 180f,
                    sweepAngle = 100f,
                    useCenter  = false,
                    topLeft    = Offset(center.x - radius * 1.1f, center.y - radius * 1.1f),
                    size       = androidx.compose.ui.geometry.Size(radius * 2.2f, radius * 2.2f),
                    style      = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                )
            }
        }

        // Main orb body
        scale(pulseScale, pivot = center) {
            // Gradient fill
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.9f),
                        secondaryColor.copy(alpha = 0.6f),
                        primaryColor.copy(alpha = 0.3f)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Inner highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - radius * 0.2f, center.y - radius * 0.3f),
                    radius = radius * 0.5f
                ),
                radius = radius,
                center = center
            )
        }

        // Speaking waveform bars
        if (state is AssistantState.Speaking) {
            drawWaveform(center, radius, wavePhase, primaryColor)
        }
    }
}

// ── Drawing Helpers ───────────────────────────────────────────────────────────

private fun DrawScope.drawGlow(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawWaveform(
    center: Offset,
    radius: Float,
    phase: Float,
    color: Color
) {
    val barCount = 7
    val barWidth = radius * 0.08f
    val maxBarHeight = radius * 0.6f
    val spacing = radius * 0.18f
    val totalWidth = barCount * (barWidth + spacing) - spacing
    val startX = center.x - totalWidth / 2f

    for (i in 0 until barCount) {
        val x = startX + i * (barWidth + spacing)
        val heightFactor = sin(phase + i * 0.8f).absoluteValue * 0.7f + 0.3f
        val barHeight = maxBarHeight * heightFactor

        drawRoundRect(
            color     = color.copy(alpha = 0.9f),
            topLeft   = Offset(x, center.y - barHeight / 2f),
            size      = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
        )
    }
}