package com.hourglass.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hourglass.ui.components.*
import com.hourglass.ui.theme.*
import com.hourglass.ui.util.toColorOrNull
import com.hourglass.viewmodel.HourglassViewModel
import com.hourglass.viewmodel.SettingsViewModel
import com.hourglass.viewmodel.TimerState
import com.hourglass.viewmodel.UiTask

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HourglassViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiTasks by viewModel.tasksWithTimer.collectAsState()
    val bedtime by settingsViewModel.bedtime.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HourglassLogo(
                            modifier = Modifier.size(28.dp),
                            size = 28
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "HOURGLASS",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Thin,
                                letterSpacing = 3.sp,
                                color = SandDeep
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = SandDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GlassBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_task") },
                containerColor = HourglassGold,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item("bedtime") {
                BedtimeCountdown(bedtime = bedtime)
            }

            if (uiTasks.isEmpty()) {
                item("empty") {
                    EmptyStatePlaceholder(
                        "Your day awaits",
                        "Add sand timers or use quicksand for quick tasks"
                    )
                }
            } else {
                // Regular task timers
                item("tasks_header") {
                    Text(
                        "Today's Sand Timers",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = SubtitleText,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(
                    uiTasks.filter { !it.isQuicksand },
                    key = { "task_${it.id}" }
                ) { uiTask ->
                    TaskCard(
                        uiTask = uiTask,
                        viewModel = viewModel,
                        modifier = Modifier.animateItemPlacement(
                            animationSpec = tween(durationMillis = 300)
                        )
                    )
                }

                // Quicksand section
                item("quicksand_header") {
                    Text(
                        "Quicksand",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = SandDark.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }

                items(
                    uiTasks.filter { it.isQuicksand },
                    key = { "quick_${it.id}" }
                ) { uiTask ->
                    QuicksandCard(
                        uiTask = uiTask,
                        viewModel = viewModel,
                        modifier = Modifier.animateItemPlacement(
                            animationSpec = tween(durationMillis = 300)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    uiTask: UiTask,
    viewModel: HourglassViewModel,
    modifier: Modifier = Modifier
) {
    val colour = uiTask.colourHex.toColorOrNull() ?: AmberGlass

    val timerState = remember {
        TimerState(
            taskId = uiTask.id,
            timeRemaining = uiTask.durationMillis,
            totalDuration = uiTask.durationMillis,
            isRunning = false,
            isPaused = false,
            isOverTime = false
        )
    }

    // Observe timer changes
    val currentTimer = viewModel.activeTimer.value
    val effectiveTimer: TimerState =
        currentTimer?.takeIf { it.taskId == uiTask.id } ?: timerState

    SandTimerBlock(
        timerState = effectiveTimer,
        taskName = uiTask.name,
        taskColour = colour,
        onStartPause = { viewModel.startTimer(uiTask.id) },
        onResume = { viewModel.resumeTimer(uiTask.id) },
        onStop = { viewModel.stopTimer() },
        modifier = modifier,
        onDelete = { viewModel.deleteTask(uiTask.id) }
    )
}

@Composable
private fun QuicksandCard(
    uiTask: UiTask,
    viewModel: HourglassViewModel,
    modifier: Modifier = Modifier
) {
    val colour = uiTask.colourHex.toColorOrNull() ?: TerracottaGlass

    val timerState = remember {
        TimerState(
            taskId = uiTask.id,
            timeRemaining = uiTask.durationMillis,
            totalDuration = uiTask.durationMillis,
            isRunning = false,
            isPaused = false,
            isOverTime = false
        )
    }

    val currentTimer = viewModel.activeTimer.value
    val effectiveTimer: TimerState =
        currentTimer?.takeIf { it.taskId == uiTask.id } ?: timerState

    QuicksandBlock(
        task = com.hourglass.data.entity.QuicksandTaskEntity(
            id = uiTask.id,
            name = uiTask.name,
            durationMillis = uiTask.durationMillis,
            colour = uiTask.colourHex
        ),
        timerState = effectiveTimer,
        onStartPause = { viewModel.startTimer(uiTask.id) },
        onResume = { viewModel.resumeTimer(uiTask.id) },
        onStop = { viewModel.stopTimer() },
        modifier = modifier,
        onDelete = { viewModel.deleteQuicksand(uiTask.id) }
    )
}

@Composable
private fun EmptyStatePlaceholder(title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HourglassLogo(size = 48)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = SubtitleText
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = SandDark.copy(alpha = 0.6f)
                )
            )
        }
    }
}
