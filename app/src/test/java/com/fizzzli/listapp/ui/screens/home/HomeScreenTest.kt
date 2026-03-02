package com.fizzzli.listapp.ui.screens.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertExists
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_displaysEmptyState() {
        composeTestRule.setContent {
            HomeScreen(
                onNavigateToList = {},
                onNavigateToCreateList = {}
            )
        }
        
        composeTestRule.onNodeWithText("暂无列表").assertExists()
    }

    @Test
    fun homeScreen_displaysAppBarTitle() {
        composeTestRule.setContent {
            HomeScreen(
                onNavigateToList = {},
                onNavigateToCreateList = {}
            )
        }
        
        composeTestRule.onNodeWithText("我的列表").assertExists()
    }
}
