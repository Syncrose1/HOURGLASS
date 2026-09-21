package com.hourglass.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
fun AddTaskScreen(
    navController: NavController,
    viewModel: HourglassViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var taskName by remember { mutableStateOf("") }
    var durationHours by remember { mutableStateOf("1") }
    var durationMinutes by remember { mutableStateOf("0") }
    var selectedColour by remember { mutableStateOf(AmberGlass) }
    var isQuicksand by remember { mutableStateOf(false) }

    val colours = listOf(
        AmberGlass, TerracottaGlass, TealGlass, RoseGlass,
        IndigoGlass, OliveGlass, CoralGlass, HourglassGold
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(navController = navController, title = "New Timer")

        Spacer(modifier = Modifier.height(24.dp))

        HourglassLogo(size = 48)

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            FilterChip(
                selected = !isQuicksand,
                onClick = { isQuicksand = false },
                label = { Text("Sand Timer") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = HourglassGold.copy(alpha = 0.2f),
                    selectedLabelColor = HourglassGold
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = isQuicksand,
                onClick = { isQuicksand = true },
                label = { Text("Quicksand") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SandMedium.copy(alpha = 0.3f),
                    selectedLabelColor = SubtitleText
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = taskName,
            onValueChange = { taskName = it },
            label = { Text(if (isQuicksand) "Quick task name" else "Task name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HourglassGold,
                unfocusedBorderColor = GlassBorder
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Duration",
            style = MaterialTheme.typography.titleMedium.copy(color = SubtitleText),
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = durationHours,
                onValueChange = { durationHours = it.filter { c -> c.isDigit() } },
                label = { Text("Hours") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HourglassGold,
                    unfocusedBorderColor = GlassBorder
                )
            )
            OutlinedTextField(
                value = durationMinutes,
                onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                label = { Text("Min") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HourglassGold,
                    unfocusedBorderColor = GlassBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Colour",
            style = MaterialTheme.typography.titleMedium.copy(color = SubtitleText),
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colours.forEach { color ->
                val isSelected = selectedColour == color
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { selectedColour = color },
                    shape = RoundedCornerShape(10.dp),
                    color = color,
                    shadowElevation = if (isSelected) 6.dp else 0.dp
                ) {
                    if (isSelected) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.5f)
                            ) {
                                Modifier.size(24.dp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (taskName.isBlank()) {
                    Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val hours = durationHours.toIntOrNull() ?: 0
                val minutes = durationMinutes.toIntOrNull() ?: 0
                if (hours + minutes == 0) {
                    Toast.makeText(context, "Set a duration", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val colourHex = "#${Integer.toHexString(selectedColour.toArgb())}"

                if (isQuicksand) {
                    viewModel.addQuicksandTask(
                        name = taskName,
                        hours = hours,
                        minutes = minutes,
                        colourHex = colourHex
                    )
                } else {
                    viewModel.addTask(
                        name = taskName,
                        hours = hours,
                        minutes = minutes,
                        colourHex = colourHex
                    )
                }

                navController.popBackStack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HourglassGold)
        ) {
            Text("Create ${if (isQuicksand) "Quicksand" else "Sand Timer"}", fontSize = 16.sp)
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