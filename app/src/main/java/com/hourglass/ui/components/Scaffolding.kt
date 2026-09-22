package com.hourglass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.hourglass.R
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.ui.theme.WordmarkStyle

/**
 * The ambient background every screen sits on: a soft vertical wash from the lighter
 * sand at the top to the deeper tone below. Flat fills were what made the old screens
 * read as grey boxes rather than as one continuous surface.
 */
@Composable
fun HourglassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = HourglassTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(colors.backdropTop, colors.backdrop))
            ),
        content = content
    )
}

/** Top bar shared by every screen: transparent, so the backdrop runs under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourglassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showMark: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val colors = HourglassTheme.colors
    TopAppBar(
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showMark) {
                    HourglassMark(size = Spacing.xl, animated = true)
                    Spacer(Modifier.width(Spacing.md))
                }
                Text(
                    text = title,
                    style = WordmarkStyle,
                    color = colors.textPrimary
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = colors.textSecondary
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = colors.textPrimary,
            actionIconContentColor = colors.textSecondary
        )
    )
}

/** Small uppercase caption introducing a group of cards. */
@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = HourglassTheme.colors.textMuted,
        modifier = modifier
    )
}
