package com.fizzzli.listapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fizzzli.listapp.ui.screens.create.CreateListScreen
import com.fizzzli.listapp.ui.screens.detail.ListDetailScreen
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
        
        composable(Screen.CreateList.route) {
            CreateListScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.ListDetail.route,
            arguments = Screen.ListDetail.arguments
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId") ?: return@composable
            ListDetailScreen(
                listId = listId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
