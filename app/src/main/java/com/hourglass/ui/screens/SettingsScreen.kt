package com.hourglass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hourglass.ui.components.HourglassLogo
import com.hourglass.ui.theme.*
import com.hourglass.viewmodel.HourglassViewModel
import com.hourglass.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val bedtime by settingsViewModel.bedtime.collectAsState()
    val wakeTime by settingsViewModel.wakeTime.collectAsState()
    val sleepMinutes by remember(bedtime, wakeTime) {
        derivedStateOf { settingsViewModel.getSleepDurationMinutes() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(navController = navController, title = "Settings")

        Spacer(modifier = Modifier.height(32.dp))

        HourglassLogo(size = 56)

        Spacer(modifier = Modifier.height(40.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = GlassBackground),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Bedtime",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = SubtitleText,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = bedtime,
                    onValueChange = {
                        if (it.length <= 5) settingsViewModel.setBedtime(it)
                    },
                    label = { Text("HH:MM") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = GlassBackground),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Wake Time",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = SubtitleText,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = wakeTime,
                    onValueChange = {
                        if (it.length <= 5) settingsViewModel.setWakeTime(it)
                    },
                    label = { Text("HH:MM") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HourglassGold.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Projected Sleep",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = HourglassGold,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "$sleepMinutes minutes",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Thin,
                        color = TimerText
                    )
                )
                Text(
                    "${sleepMinutes / 60}h ${sleepMinutes % 60}m",
                    style = MaterialTheme.typography.bodyMedium.copy(color = SubtitleText)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = GlassBackground)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "About HOURGLASS",
                    style = MaterialTheme.typography.titleMedium.copy(color = SandDeep)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "A lightweight productivity timer. Bank your time, respect your rest.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = SubtitleText),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(navController: NavController, title: String) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Thin,
                    color = SandDeep
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = SandDark
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = GlassBackground
        )
    )
}
