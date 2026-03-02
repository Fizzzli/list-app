package com.fizzzli.listapp.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object ListDetail : Screen("list/{listId}") {
        fun createRoute(listId: String) = "list/$listId"
        val arguments = listOf(
            navArgument("listId") { type = NavType.StringType }
        )
    }
    object CreateList : Screen("create_list")
}
