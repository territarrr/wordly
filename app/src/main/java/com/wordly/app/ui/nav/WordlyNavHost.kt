package com.wordly.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wordly.app.ui.addword.AddWordScreen
import com.wordly.app.ui.home.HomeScreen
import com.wordly.app.ui.importwords.ImportScreen
import com.wordly.app.ui.recheck.RecheckScreen
import com.wordly.app.ui.stats.StatsScreen
import com.wordly.app.ui.session.SessionScreen
import com.wordly.app.ui.settings.SettingsScreen
import com.wordly.app.ui.words.WordListScreen

@Composable
fun WordlyNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpenWords = { profileId -> navController.navigate(Routes.words(profileId)) },
                onAddWord = { profileId -> navController.navigate(Routes.add(profileId)) },
                onStartSession = { config, profileId -> navController.navigate(Routes.session(config, profileId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenStats = { profileId -> navController.navigate(Routes.stats(profileId)) },
            )
        }

        composable(
            route = Routes.STATS_PATTERN,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { backStackEntry ->
            StatsScreen(
                profileId = backStackEntry.arguments?.getLong("profileId") ?: 1L,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onDone = { navController.popBackStack() })
        }

        composable(
            route = Routes.WORDS_PATTERN,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: 1L
            WordListScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                onAddWord = { navController.navigate(Routes.add(profileId)) },
                onEditWord = { wordId -> navController.navigate(Routes.edit(wordId, profileId)) },
                onImport = { navController.navigate(Routes.import(profileId)) },
                onRecheck = { navController.navigate(Routes.recheck(profileId)) },
            )
        }

        composable(
            route = Routes.RECHECK_PATTERN,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { backStackEntry ->
            RecheckScreen(
                profileId = backStackEntry.arguments?.getLong("profileId") ?: 2L,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.ADD_PATTERN,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: 1L
            AddWordScreen(
                profileId = profileId,
                onDone = { navController.popBackStack() },
                onOpenWord = { wordId -> navController.openWordInstead(wordId, profileId, backStackEntry) },
            )
        }

        composable(
            route = Routes.IMPORT_PATTERN,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { backStackEntry ->
            ImportScreen(
                profileId = backStackEntry.arguments?.getLong("profileId") ?: 1L,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EDIT_PATTERN,
            arguments = listOf(
                navArgument("wordId") { type = NavType.LongType },
                navArgument("profileId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: 1L
            AddWordScreen(
                wordId = backStackEntry.arguments?.getLong("wordId"),
                profileId = profileId,
                onDone = { navController.popBackStack() },
                onOpenWord = { wordId -> navController.openWordInstead(wordId, profileId, backStackEntry) },
            )
        }

        composable(
            route = Routes.SESSION_PATTERN,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "review" },
                navArgument("dir") { type = NavType.StringType; defaultValue = "EN_TO_RU" },
                navArgument("quiz") { type = NavType.StringType; defaultValue = "CARDS" },
                navArgument("shuffle") { type = NavType.StringType; defaultValue = "false" },
                navArgument("scopeType") { type = NavType.StringType; defaultValue = "all" },
                navArgument("scopeCount") { type = NavType.StringType; defaultValue = "0" },
                navArgument("scopeFromStart") { type = NavType.StringType; defaultValue = "false" },
                navArgument("scopeFrom") { type = NavType.StringType; defaultValue = "0" },
                navArgument("scopeTo") { type = NavType.StringType; defaultValue = "0" },
                navArgument("batch") { type = NavType.StringType; defaultValue = "0" },
                navArgument("seed") { type = NavType.StringType; defaultValue = "0" },
                navArgument("profileId") { type = NavType.StringType; defaultValue = "1" },
            ),
        ) {
            SessionScreen(
                onExit = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onEditWord = { wordId, profileId -> navController.navigate(Routes.edit(wordId, profileId)) },
            )
        }
    }
}

/**
 * Replaces the current add/edit screen with the edit screen for [wordId] (an existing duplicate
 * or translation match), so "back" returns to wherever the add/edit was opened from.
 */
private fun NavHostController.openWordInstead(wordId: Long, profileId: Long, current: NavBackStackEntry) {
    navigate(Routes.edit(wordId, profileId)) {
        popUpTo(current.destination.id) { inclusive = true }
    }
}
