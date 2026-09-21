package com.hourglass.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourglass.ui.theme.*
import com.hourglass.ui.util.toColorOrNull
import com.hourglass.viewmodel.TimerState
import kotlin.math.max

@Composable
fun SandTimerBlock(
    timerState: TimerState,
    taskName: String,
    taskColour: Color,
    onStartPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    val scale by animateFloatAsState(
        targetValue = if (timerState.isRunning) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "scale"
    )

    val glow by animateFloatAsState(
        targetValue = if (timerState.isRunning) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = androidx.compose.animation.core.EaseInOut),
        label = "glow"
    )

    val borderColor = if (timerState.isRunning) taskColour else GlassBorder

    val progress = if (timerState.totalDuration > 0) {
        timerState.timeRemaining.toFloat() / timerState.totalDuration.toFloat()
    } else 1f

    val isOverTime = timerState.isOverTime
    val isRunning = timerState.isRunning
    val isPaused = timerState.isPaused

    Column(
        modifier = modifier
            .scale(scale)
            .background(GlassBackground, RoundedCornerShape(20.dp))
            .border(
                width = 2.dp,
                brush = glowingBorderBrush(borderColor, glow),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SandTimerVisual(
            progress = progress,
            color = taskColour,
            isRunning = isRunning,
            isOverTime = isOverTime,
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = taskName,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TimerText
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = formatTime(timerState.timeRemaining),
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.Thin,
                color = if (isOverTime) OvertimeRed else TimerText
            )
        )

        if (isOverTime) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "OVERTIME",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    color = OvertimeRed,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        if (isPaused) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PAUSED",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = SubtitleText
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            when {
                !isRunning && !isPaused -> {
                    Button(
                        onClick = onStartPause,
                        colors = ButtonDefaults.buttonColors(containerColor = taskColour),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(120.dp).height(44.dp)
                    ) {
                        Text("Start", color = Color.White, fontSize = 14.sp)
                    }
                }
                isRunning -> {
                    Button(
                        onClick = onStartPause,
                        colors = ButtonDefaults.buttonColors(containerColor = SandDeep),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(120.dp).height(44.dp)
                    ) {
                        Text("Pause", color = Color.White, fontSize = 14.sp)
                    }
                }
                isPaused -> {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(containerColor = taskColour),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(120.dp).height(44.dp)
                    ) {
                        Text("Resume", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = SandLight),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(80.dp).height(44.dp)
            ) {
                Text("Stop", color = SandDeep, fontSize = 13.sp)
            }
        }

        onDelete?.let {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDelete) {
                Text("Remove", color = SubtitleText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun glowingBorderBrush(color: Color, glow: Float): Brush {
    return Brush.horizontalGradient(
        colors = listOf(
            color.copy(alpha = 0.8f + glow * 0.2f),
            color.copy(alpha = 0.6f + glow * 0.4f)
        )
    )
}

@Composable
fun SandTimerVisual(
    progress: Float,
    color: Color,
    isRunning: Boolean,
    isOverTime: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sand_animation")

    val sandShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isRunning) 1200 else 3000,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "sand_shift"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isRunning) 1f else 0.6f,
        animationSpec = tween(500),
        label = "sand_alpha"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val glassWidth = size.width * 0.4f
        val glassHeight = size.height * 0.85f
        val neckWidth = 8f
        val neckHeight = 12f
        val taper = 0.3f

        val topY = center.y - glassHeight / 2
        val bottomY = center.y + glassHeight / 2

        val glassPathTop = Path().apply {
            moveTo(center.x - glassWidth / 2, topY)
            lineTo(center.x + glassWidth / 2, topY)
            lineTo(center.x + neckWidth / 2, topY + glassHeight * taper)
            lineTo(center.x + neckWidth / 2, topY + glassHeight * taper + neckHeight / 2)
            lineTo(center.x - neckWidth / 2, topY + glassHeight * taper + neckHeight / 2)
            lineTo(center.x - neckWidth / 2, topY + glassHeight * taper)
            close()
        }
        drawPath(glassPathTop, color = color.copy(alpha = 0.15f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        val glassPathBottom = Path().apply {
            moveTo(center.x - neckWidth / 2, bottomY - glassHeight * taper - neckHeight / 2)
            lineTo(center.x + neckWidth / 2, bottomY - glassHeight * taper - neckHeight / 2)
            lineTo(center.x + neckWidth / 2, bottomY - glassHeight * taper)
            lineTo(center.x + glassWidth / 2, bottomY)
            lineTo(center.x - glassWidth / 2, bottomY)
            lineTo(center.x - neckWidth / 2, bottomY - glassHeight * taper)
            close()
        }
        drawPath(glassPathBottom, color = color.copy(alpha = 0.15f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        val topSandHeight = glassHeight * taper * (1f - progress.coerceIn(0f, 1f))
        if (topSandHeight > 1f) {
            val sandPathTop = Path().apply {
                moveTo(center.x - glassWidth / 2, topY)
                lineTo(center.x + glassWidth / 2, topY)
                lineTo(center.x + neckWidth / 2 + sandShift * 0.1f, topY + glassHeight * taper - topSandHeight)
                lineTo(center.x - neckWidth / 2 - sandShift * 0.1f, topY + glassHeight * taper - topSandHeight)
                close()
            }
            drawPath(sandPathTop, color = if (isOverTime) OvertimeRed else color, alpha = alpha)
        }

        val bottomSandHeight = glassHeight * taper * progress.coerceIn(0f, 1f)
        if (bottomSandHeight > 1f) {
            val sandPathBottom = Path().apply {
                val expand = (glassWidth / 2 - neckWidth / 2) * (bottomSandHeight / (glassHeight * taper))
                moveTo(center.x - neckWidth / 2, bottomY - glassHeight * taper)
                lineTo(center.x + neckWidth / 2, bottomY - glassHeight * taper)
                lineTo(center.x + neckWidth / 2 + expand * 0.3f, bottomY - glassHeight * taper + bottomSandHeight * 0.7f)
                lineTo(center.x + glassWidth / 2, bottomY)
                lineTo(center.x - glassWidth / 2, bottomY)
                lineTo(center.x - neckWidth / 2 - expand * 0.3f, bottomY - glassHeight * taper + bottomSandHeight * 0.7f)
                close()
            }
            drawPath(sandPathBottom, color = if (isOverTime) OvertimeRed.copy(alpha = 0.7f) else color.copy(alpha = 0.7f), alpha = alpha)
        }

        if (isRunning) {
            drawCircle(color = color.copy(alpha = 0.12f), radius = size.width * 0.55f, center = center)
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = max(0, millis) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
