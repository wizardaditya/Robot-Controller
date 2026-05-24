package com.example.ui.components

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class JoystickDirection {
    STOP, FORWARD, BACKWARD, LEFT, RIGHT, FORWARD_LEFT, FORWARD_RIGHT, BACKWARD_LEFT, BACKWARD_RIGHT
}

@Composable
fun RoboJoystick(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    isThrottle: Boolean = false,
    throttleValue: Float = 180f,
    onThrottleChanged: ((Float) -> Unit)? = null,
    onDirectionChanged: ((JoystickDirection) -> Unit)? = null
) {
    var activeDir by remember { mutableStateOf(JoystickDirection.STOP) }

    val padColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    val strokeColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.primary
    val activeGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val activeTrackColor = MaterialTheme.colorScheme.tertiary
    val trackpadShape = RoundedCornerShape(24.dp)

    BoxWithConstraints(
        modifier = modifier
            .size(size)
            .clip(trackpadShape)
            .background(padColor)
            .border(2.dp, strokeColor.copy(alpha = 0.45f), shape = trackpadShape)
            .testTag("joystick_pad"),
        contentAlignment = Alignment.Center
    ) {
        val maxRadiusPx = constraints.maxWidth / 2f
        val maxThumbRadius = maxRadiusPx * 0.75f

        // Reactive local representation of drag offset
        var dragOffset by remember { mutableStateOf(Offset.Zero) }

        // Sync with external throttleValue if in throttle mode and not dragging
        var isDragging by remember { mutableStateOf(false) }

        val targetOffset = if (isDragging) {
            dragOffset
        } else {
            if (isThrottle) {
                val normalized = (throttleValue / 255f).coerceIn(0f, 1f)
                Offset(0f, maxThumbRadius * (1f - 2f * normalized))
            } else {
                Offset.Zero
            }
        }

        val animatedOffset by animateOffsetAsState(
            targetValue = targetOffset,
            animationSpec = if (isDragging) {
                snap()
            } else {
                spring(
                    dampingRatio = 0.7f,
                    stiffness = Spring.StiffnessMedium
                )
            },
            label = "joystick_thumb"
        )

        // Gesture detector
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isThrottle) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffset = targetOffset
                        },
                        onDragEnd = {
                            isDragging = false
                            if (!isThrottle) {
                                if (activeDir != JoystickDirection.STOP) {
                                    activeDir = JoystickDirection.STOP
                                    onDirectionChanged?.invoke(JoystickDirection.STOP)
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            if (!isThrottle) {
                                if (activeDir != JoystickDirection.STOP) {
                                    activeDir = JoystickDirection.STOP
                                    onDirectionChanged?.invoke(JoystickDirection.STOP)
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val rawOffset = dragOffset + dragAmount
                            
                            val constrainedOffset = if (isThrottle) {
                                val constrainedY = rawOffset.y.coerceIn(-maxThumbRadius, maxThumbRadius)
                                Offset(0f, constrainedY)
                            } else {
                                val distance = sqrt(rawOffset.x * rawOffset.x + rawOffset.y * rawOffset.y)
                                if (distance > maxThumbRadius) {
                                    val ratio = maxThumbRadius / distance
                                    Offset(rawOffset.x * ratio, rawOffset.y * ratio)
                                } else {
                                    rawOffset
                                }
                            }
                            dragOffset = constrainedOffset

                            if (isThrottle) {
                                // Map Y-axis from bottom (maxThumbRadius -> 0f) to top (-maxThumbRadius -> 255f)
                                val ratio = (1f - (constrainedOffset.y / maxThumbRadius)) / 2f
                                val calculatedThrottle = (ratio * 255f).coerceIn(0f, 255f)
                                onThrottleChanged?.invoke(calculatedThrottle)
                            } else {
                                // Map coordinates to joystick direction
                                val dx = constrainedOffset.x
                                val dy = constrainedOffset.y
                                val deadZone = maxThumbRadius * 0.25f

                                val newDir = if (sqrt(dx * dx + dy * dy) < deadZone) {
                                    JoystickDirection.STOP
                                } else {
                                    val angleDegrees = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())) // Math Cartesian
                                    val normalizedAngle = if (angleDegrees < 0) angleDegrees + 360 else angleDegrees

                                    when {
                                        (normalizedAngle >= 67.5 && normalizedAngle < 112.5) -> JoystickDirection.FORWARD
                                        (normalizedAngle >= 22.5 && normalizedAngle < 67.5) -> JoystickDirection.FORWARD_RIGHT
                                        (normalizedAngle >= 112.5 && normalizedAngle < 157.5) -> JoystickDirection.FORWARD_LEFT
                                        (normalizedAngle >= 157.5 && normalizedAngle < 202.5) -> JoystickDirection.LEFT
                                        (normalizedAngle >= 202.5 && normalizedAngle < 247.5) -> JoystickDirection.BACKWARD_LEFT
                                        (normalizedAngle >= 247.5 && normalizedAngle < 292.5) -> JoystickDirection.BACKWARD
                                        (normalizedAngle >= 292.5 && normalizedAngle < 337.5) -> JoystickDirection.BACKWARD_RIGHT
                                        else -> JoystickDirection.RIGHT
                                    }
                                }

                                if (newDir != activeDir) {
                                    activeDir = newDir
                                    onDirectionChanged?.invoke(newDir)
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerPt = Offset(this.size.width / 2f, this.size.height / 2f)

                // Highlight active directional segment as a modern glowing background
                if (!isThrottle && activeDir != JoystickDirection.STOP) {
                    drawRoundRect(
                        color = activeGlow,
                        topLeft = Offset.Zero,
                        size = this.size
                    )
                }

                // For throttle-only, color the active region on the slide track
                if (isThrottle) {
                    // Draw a vertical slide track line
                    drawLine(
                        color = strokeColor.copy(alpha = 0.5f),
                        start = Offset(centerPt.x, centerPt.y - maxThumbRadius),
                        end = Offset(centerPt.x, centerPt.y + maxThumbRadius),
                        strokeWidth = 6.dp.toPx()
                    )
                    // Draw active portion starting from bottom
                    drawLine(
                        color = activeTrackColor,
                        start = Offset(centerPt.x, centerPt.y + maxThumbRadius),
                        end = Offset(centerPt.x, centerPt.y + animatedOffset.y),
                        strokeWidth = 6.dp.toPx()
                    )
                } else {
                    // Draw visual crosshairs for normal direction joystick
                    drawLine(
                        color = strokeColor.copy(alpha = 0.2f),
                        start = Offset(4.dp.toPx(), centerPt.y),
                        end = Offset(this.size.width - 4.dp.toPx(), centerPt.y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = strokeColor.copy(alpha = 0.2f),
                        start = Offset(centerPt.x, 4.dp.toPx()),
                        end = Offset(centerPt.x, this.size.height - 4.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Draw draggable thumb knob
                val thumbCenter = centerPt + animatedOffset
                drawCircle(
                    color = thumbColor,
                    radius = 24.dp.toPx(),
                    center = thumbCenter
                )
                
                // Outer chrome highlight on the thumb
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = 16.dp.toPx(),
                    center = thumbCenter,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
