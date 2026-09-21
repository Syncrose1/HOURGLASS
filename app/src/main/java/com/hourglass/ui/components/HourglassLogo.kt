package com.hourglass.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourglass.ui.theme.*

@Composable
fun HourglassLogo(modifier: Modifier = Modifier, size: Int = 64) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_sand")

    val sandFall by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = androidx.compose.animation.core.EaseInOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "sand_fall"
    )

    Canvas(modifier = modifier.size(size.dp)) {
        val w = size.toFloat()
        val h = size.toFloat() * 1.2f
        val cx = w / 2
        val topY = 0f
        val bottomY = h
        val glassW = w * 0.7f
        val glassH = h * 0.8f
        val neckW = 5f
        val taper = 0.3f
        val neckY = glassH * taper

        val topPath = Path().apply {
            moveTo(cx - glassW / 2, topY)
            lineTo(cx + glassW / 2, topY)
            lineTo(cx + neckW / 2, neckY)
            lineTo(cx - neckW / 2, neckY)
            close()
        }
        drawPath(topPath, color = HourglassGold.copy(alpha = 0.1f), style = Stroke(width = 2f, cap = StrokeCap.Round))

        val bottomPath = Path().apply {
            moveTo(cx - neckW / 2, neckY)
            lineTo(cx + neckW / 2, neckY)
            lineTo(cx + glassW / 2, bottomY)
            lineTo(cx - glassW / 2, bottomY)
            close()
        }
        drawPath(bottomPath, color = HourglassGold.copy(alpha = 0.1f), style = Stroke(width = 2f, cap = StrokeCap.Round))

        val sandTopHeight = glassH * taper * (1f - sandFall)
        if (sandTopHeight > 1f) {
            val slope = (glassW / 2 - neckW / 2) / (glassH * taper)
            val sandPath = Path().apply {
                moveTo(cx - glassW / 2, topY)
                lineTo(cx + glassW / 2, topY)
                val leftAtSand = cx - glassW / 2 + slope * sandTopHeight
                val rightAtSand = cx + glassW / 2 - slope * sandTopHeight
                lineTo(rightAtSand, topY + sandTopHeight)
                lineTo(cx + neckW / 2, neckY)
                lineTo(cx - neckW / 2, neckY)
                lineTo(leftAtSand, topY + sandTopHeight)
                close()
            }
            drawPath(sandPath, color = HourglassGold.copy(alpha = 0.6f))
        }

        for (i in 0 until 5) {
            val particleY = neckY - 5 + sandFall * 30 + (i * 6)
            val particleAlpha = (1f - sandFall) * (0.6f - i * 0.1f)
            if (particleAlpha > 0f && particleY < bottomY - 5) {
                drawCircle(
                    color = HourglassGold,
                    radius = 1.5f,
                    center = Offset(cx + (i - 2) * 1.5f, particleY),
                    alpha = particleAlpha.coerceIn(0f, 1f)
                )
            }
        }

        val sandBottomHeight = glassH * taper * sandFall
        if (sandBottomHeight > 1f) {
            val expand = (glassW / 2 - neckW / 2) * sandBottomHeight * 0.5f / (glassH * taper)
            val bottomPath = Path().apply {
                moveTo(cx - neckW / 2, neckY)
                lineTo(cx + neckW / 2, neckY)
                lineTo(cx + neckW / 2 + expand, bottomY)
                lineTo(cx - neckW / 2 - expand, bottomY)
                close()
            }
            drawPath(bottomPath, color = HourglassGold.copy(alpha = 0.5f))
        }
    }
}
