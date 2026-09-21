package com.hourglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourglass.ui.theme.*
import java.time.Duration
import kotlin.math.max

@Composable
fun BedtimeCountdown(
    bedtime: String = "22:00",
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000L)
            now = System.currentTimeMillis()
        }
    }

    val timeUntilBedtime = remember(now, bedtime) {
        calculateTimeUntilBedtime(now, bedtime)
    }

    val hours = timeUntilBedtime.toHours()
    val minutes = timeUntilBedtime.toMinutes() % 60
    val totalMinutes = hours * 60 + minutes

    val progress = (hours + minutes / 60f) / 12f
    val isSoon = hours <= 2 && hours >= 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = if (isSoon) HourglassGold else SandDark,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Bedtime",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isSoon) HourglassGold else SandDark,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (isSoon) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = HourglassGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        Text(
                            "SOON",
                            fontSize = 10.sp,
                            color = HourglassGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    bedtime,
                    style = MaterialTheme.typography.titleLarge.copy(color = TimerText)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = formatBedtimeCountdown(hours, minutes),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Thin,
                    color = if (isSoon) HourglassGold else TimerText
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hours <= 0 && minutes <= 0) "Time for bed! 💤" else "until bedtime",
                style = MaterialTheme.typography.bodyMedium.copy(color = SubtitleText),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = GlassBorder,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 4f, cap = StrokeCap.Round),
                        topLeft = Offset(4f, 4f),
                        size = Size(size.width - 8f, size.height - 8f)
                    )
                    drawArc(
                        color = HourglassGold,
                        startAngle = -90f,
                        sweepAngle = (progress.coerceIn(0f, 1f) * 360f),
                        useCenter = false,
                        style = Stroke(width = 4f, cap = StrokeCap.Round),
                        topLeft = Offset(4f, 4f),
                        size = Size(size.width - 8f, size.height - 8f)
                    )
                }
                Text(
                    text = "${max(0, totalMinutes)}m",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SandDark,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

private fun formatBedtimeCountdown(hours: Long, minutes: Long): String {
    if (hours <= 0 && minutes <= 0) return "Time for bed!"
    val hourText = if (hours == 1L) "1 hour" else "$hours hours"
    val minuteText = if (minutes == 1L) "1 minute" else "$minutes minutes"
    return when {
        hours == 0L -> minuteText
        minutes == 0L -> hourText
        else -> "$hourText $minuteText"
    }
}

private fun calculateTimeUntilBedtime(currentTime: Long, bedtime: String): Duration {
    val parts = bedtime.split(":")
    val bedHour = parts.getOrNull(0)?.toIntOrNull() ?: 22
    val bedMin = parts.getOrNull(1)?.toIntOrNull() ?: 0

    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = currentTime
    val nowHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val nowMin = cal.get(java.util.Calendar.MINUTE)

    var targetTotalMin = bedHour * 60 + bedMin
    val currentTotalMin = nowHour * 60 + nowMin

    if (targetTotalMin <= currentTotalMin) {
        targetTotalMin += 24 * 60
    }

    val diffMin = targetTotalMin - currentTotalMin
    return Duration.ofMinutes(diffMin.toLong())
}
