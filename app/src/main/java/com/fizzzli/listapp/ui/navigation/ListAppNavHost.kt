package com.fizzzli.listapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fizzzli.listapp.ui.screens.home.HomeScreen

@Composable
fun ListAppNavHost() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToList = { listId ->
                    navController.navigate(Screen.ListDetail.createRoute(listId))
                },
                onNavigateToCreateList = {
                    navController.navigate(Screen.CreateList.route)
                }
            )
        }
        
        composable(
            route = Screen.ListDetail.route,
            arguments = Screen.ListDetail.arguments
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId") ?: return@composable
            // TODO: Implement ListDetailScreen
        }
        
        composable(Screen.CreateList.route) {
            // TODO: Implement CreateListScreen
        }
    }
}
