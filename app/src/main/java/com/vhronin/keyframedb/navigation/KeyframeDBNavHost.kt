package com.vhronin.keyframedb.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vhronin.keyframedb.diagrameditor.ui.DiagramEditorScreen
import com.vhronin.keyframedb.projectlist.ui.ProjectListScreen

@Composable
fun KeyframeDBNavHost(navController: NavHostController){
    NavHost(startDestination = Destination.ProjectList.name, navController = navController){
        composable(Destination.ProjectList.name){
            ProjectListScreen()
        }

        composable(
            route = Destination.DiagramEditor.name,
            arguments = listOf(navArgument("projectId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ){ backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId")

            DiagramEditorScreen(projectId = projectId ?: "1")
        }
    }
}