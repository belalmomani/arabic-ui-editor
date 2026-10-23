package com.arabicuieditor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arabicuieditor.ui.editor.EditorScreen
import com.arabicuieditor.ui.projects.ProjectsScreen
import com.arabicuieditor.ui.screens.AboutScreen
import com.arabicuieditor.ui.screens.HomeScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenProject = { id -> navController.navigate(Routes.editor(id)) },
                onOpenProjects = { navController.navigate(Routes.PROJECTS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }
        composable(Routes.PROJECTS) {
            ProjectsScreen(
                onBack = { navController.popBackStack() },
                onOpenProject = { id -> navController.navigate(Routes.editor(id)) }
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) {
            EditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
