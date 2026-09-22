package com.hourglass.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hourglass.ui.screens.AddTimerScreen
import com.hourglass.ui.screens.HomeScreen
import com.hourglass.ui.screens.SettingsScreen

/** The three destinations, named in one place instead of as scattered string literals. */
object Routes {
    const val HOME = "home"
    const val ADD_TIMER = "add_timer"
    const val SETTINGS = "settings"
}

@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddTimer = { navController.navigate(Routes.ADD_TIMER) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = Routes.ADD_TIMER,
            enterTransition = { slideInVertically(tween(280)) { it / 6 } + fadeIn(tween(280)) },
            popExitTransition = { slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(200)) }
        ) {
            AddTimerScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
