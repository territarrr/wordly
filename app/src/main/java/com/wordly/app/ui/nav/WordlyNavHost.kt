package com.wordly.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wordly.app.ui.addword.AddWordScreen
import com.wordly.app.ui.home.HomeScreen
import com.wordly.app.ui.session.SessionScreen
import com.wordly.app.ui.words.WordListScreen

@Composable
fun WordlyNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpenWords = { navController.navigate(Routes.WORDS) },
                onStartSession = { config -> navController.navigate(Routes.session(config)) },
            )
        }

        composable(Routes.WORDS) {
            WordListScreen(
                onBack = { navController.popBackStack() },
                onAddWord = { navController.navigate(Routes.ADD) },
                onEditWord = { wordId -> navController.navigate(Routes.edit(wordId)) },
            )
        }

        composable(Routes.ADD) {
            AddWordScreen(onDone = { navController.popBackStack() })
        }

        composable(
            route = Routes.EDIT_PATTERN,
            arguments = listOf(navArgument("wordId") { type = NavType.LongType }),
        ) { backStackEntry ->
            AddWordScreen(
                wordId = backStackEntry.arguments?.getLong("wordId"),
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.SESSION_PATTERN,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "review" },
                navArgument("dir") { type = NavType.StringType; defaultValue = "EN_TO_RU" },
                navArgument("shuffle") { type = NavType.StringType; defaultValue = "false" },
                navArgument("scopeType") { type = NavType.StringType; defaultValue = "all" },
                navArgument("scopeCount") { type = NavType.StringType; defaultValue = "0" },
                navArgument("scopeFromStart") { type = NavType.StringType; defaultValue = "false" },
                navArgument("scopeFrom") { type = NavType.StringType; defaultValue = "0" },
                navArgument("scopeTo") { type = NavType.StringType; defaultValue = "0" },
                navArgument("batch") { type = NavType.StringType; defaultValue = "0" },
                navArgument("seed") { type = NavType.StringType; defaultValue = "0" },
            ),
        ) {
            SessionScreen(
                onExit = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
    }
}
