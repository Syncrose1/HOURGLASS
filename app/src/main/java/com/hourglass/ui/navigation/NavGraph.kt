package com.hourglass.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hourglass.ui.screens.AddTaskScreen
import com.hourglass.ui.screens.HomeScreen
import com.hourglass.ui.screens.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(navController = navController)
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        composable("add_task") {
            AddTaskScreen(navController = navController)
        }
    }
}
