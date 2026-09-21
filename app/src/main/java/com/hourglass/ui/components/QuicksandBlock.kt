package com.hourglass.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourglass.data.entity.QuicksandTaskEntity
import com.hourglass.ui.theme.*
import com.hourglass.ui.util.toColorOrNull
import com.hourglass.viewmodel.TimerState as VMTimerState
import kotlin.math.max

@Composable
fun QuicksandBlock(
    task: QuicksandTaskEntity,
    timerState: VMTimerState,
    onStartPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    val scale by animateFloatAsState(
        targetValue = if (timerState.isRunning) 1.01f else 0.98f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "quicksand_scale"
    )

    val isRunning = timerState.isRunning
    val isPaused = timerState.isPaused
    val isOverTime = timerState.isOverTime
    val progress = if (timerState.totalDuration > 0) {
        timerState.timeRemaining.toFloat() / timerState.totalDuration.toFloat()
    } else 1f

    Column(
        modifier = modifier
            .scale(scale)
            .background(
                Color(0xFFE8E0D4),
                RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(listOf(SubtleBrown, SubtleBrownLight)),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = SandMedium.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    "QUICKSAND",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = SubtitleText,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        MiniSandVisual(
            progress = progress,
            color = task.colour.toColorOrNull() ?: SandMedium,
            isRunning = isRunning,
            isOverTime = isOverTime
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = task.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = SubtitleText,
                fontWeight = FontWeight.Normal
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = formatMiniTime(timerState.timeRemaining),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Thin,
                color = if (isOverTime) OvertimeRed else TimerText
            )
        )

        if (isOverTime) {
            Text(
                text = "OVERTIME",
                style = MaterialTheme.typography.labelSmall.copy(color = OvertimeRed, fontSize = 10.sp)
            )
        }

        if (isPaused) {
            Text(
                text = "PAUSED",
                style = MaterialTheme.typography.labelSmall.copy(color = SubtitleText, fontSize = 10.sp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            when {
                !isRunning && !isPaused -> {
                    OutlinedButton(
                        onClick = onStartPause,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.width(100.dp).height(38.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = task.colour.toColorOrNull() ?: SandMedium)
                    ) {
                        Text("Start", fontSize = 12.sp)
                    }
                }
                isRunning -> {
                    OutlinedButton(
                        onClick = onStartPause,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.width(100.dp).height(38.dp)
                    ) {
                        Text("Pause", fontSize = 12.sp, color = SandDeep)
                    }
                }
                isPaused -> {
                    OutlinedButton(
                        onClick = onResume,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.width(100.dp).height(38.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = task.colour.toColorOrNull() ?: SandMedium)
                    ) {
                        Text("Resume", fontSize = 12.sp)
                    }
                }
            }

            OutlinedButton(
                onClick = onStop,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.width(70.dp).height(38.dp)
            ) {
                Text("Stop", fontSize = 11.sp, color = SandDark)
            }
        }

        onDelete?.let {
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onDelete) {
                Text("Remove", color = SubtitleText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MiniSandVisual(
    progress: Float,
    color: Color,
    isRunning: Boolean,
    isOverTime: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(60.dp)) {
        val centerX = size.width / 2
        val topY = size.height * 0.1f
        val bottomY = size.height * 0.9f
        val neckW = 4f
        val neckH = 4f
        val taper = 0.35f
        val glassW = size.width * 0.45f

        val sandPath = Path().apply {
            moveTo(centerX - glassW / 2, topY)
            lineTo(centerX + glassW / 2, topY)
            lineTo(centerX + neckW / 2, topY + (bottomY - topY) * taper)
            lineTo(centerX + neckW / 2, topY + (bottomY - topY) * taper + neckH)
            lineTo(centerX + glassW / 2, bottomY)
            lineTo(centerX - glassW / 2, bottomY)
            lineTo(centerX - neckW / 2, topY + (bottomY - topY) * taper + neckH)
            lineTo(centerX - neckW / 2, topY + (bottomY - topY) * taper)
            close()
        }
        drawPath(sandPath, color = color.copy(alpha = if (isRunning) 0.18f else 0.10f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))

        val p = progress.coerceIn(0f, 1f)
        if (p < 1f) {
            drawLine(
                color = if (isOverTime) OvertimeRed else color,
                start = Offset(centerX - glassW * 0.3f, topY + 2f),
                end = Offset(centerX + glassW * 0.3f, topY + 2f),
                strokeWidth = 3f,
                alpha = 0.6f
            )
        }
    }
}

private fun formatMiniTime(millis: Long): String {
    val totalSeconds = max(0, millis) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private val SubtleBrown = Color(0xFFA1887F)
private val SubtleBrownLight = Color(0xFFBCAAA4)
