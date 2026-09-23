package com.hourglass.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hourglass.core.TimerKind
import com.hourglass.core.TimerRef
import com.hourglass.core.world.DaySky
import com.hourglass.ui.world.LocalDaySpent
import com.hourglass.viewmodel.HourglassViewModel
import com.hourglass.ui.screens.DesertScreen
import com.hourglass.ui.screens.HomeScreen
import com.hourglass.ui.screens.SettingsScreen
import com.hourglass.ui.screens.SoulsScreen
import com.hourglass.ui.screens.TimerFormScreen

/**
 * Four destinations.
 *
 * Focus is not one of them: a running timer is a state of the home screen, not a place
 * you navigate to, which is what lets tapping out of it pause the timer rather than
 * leave it running behind a back stack.
 */
object Routes {
    const val HOME = "home"
    const val NEW_TIMER = "timer/new"
    const val SETTINGS = "settings"
    const val DESERT = "desert"
    const val SOULS = "souls"

    const val ARG_ID = "id"
    const val ARG_KIND = "kind"

    const val EDIT_TIMER = "timer/edit/{$ARG_KIND}/{$ARG_ID}"

    fun editTimer(ref: TimerRef): String = "timer/edit/${ref.kind.name}/${ref.id}"

    /** Rebuilds the ref from the back stack, returning null on anything malformed. */
    fun refFrom(kind: String?, id: Long?): TimerRef? {
        if (id == null || id <= 0L) return null
        val parsed = TimerKind.entries.firstOrNull { it.name == kind } ?: return null
        return TimerRef(id, parsed)
    }

    val editArguments = listOf(
        navArgument(ARG_KIND) { type = NavType.StringType },
        navArgument(ARG_ID) { type = NavType.LongType }
    )
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    viewModel: HourglassViewModel = hiltViewModel()
) {
    val home by viewModel.homeState.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalDaySpent provides DaySky.spentFor(home.minutesUntilBedtime)) {
        Destinations(navController)
    }
}

@Composable
private fun Destinations(navController: NavHostController) {
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
                onAddTimer = { navController.navigate(Routes.NEW_TIMER) },
                onEditTimer = { ref -> navController.navigate(Routes.editTimer(ref)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDesert = { navController.navigate(Routes.DESERT) },
                onOpenSouls = { navController.navigate(Routes.SOULS) }
            )
        }

        composable(
            route = Routes.NEW_TIMER,
            enterTransition = { slideInVertically(tween(280)) { it / 6 } + fadeIn(tween(280)) },
            popExitTransition = { slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(200)) }
        ) {
            TimerFormScreen(onDone = { navController.popBackStack() })
        }

        composable(
            route = Routes.EDIT_TIMER,
            arguments = Routes.editArguments,
            enterTransition = { slideInVertically(tween(280)) { it / 6 } + fadeIn(tween(280)) },
            popExitTransition = { slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(200)) }
        ) { entry ->
            val ref = Routes.refFrom(
                kind = entry.arguments?.getString(Routes.ARG_KIND),
                id = entry.arguments?.getLong(Routes.ARG_ID)
            )
            if (ref == null) {
                // A malformed link is not worth an empty form; go back rather than guess.
                navController.popBackStack()
            } else {
                TimerFormScreen(onDone = { navController.popBackStack() }, editing = ref)
            }
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SOULS) {
            SoulsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DESERT) {
            DesertScreen(onBack = { navController.popBackStack() })
        }
    }
}
