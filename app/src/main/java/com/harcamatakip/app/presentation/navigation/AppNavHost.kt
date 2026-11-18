package com.harcamatakip.app.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.harcamatakip.app.presentation.analytics.AnalyticsScreen
import com.harcamatakip.app.presentation.budget.BudgetScreen
import com.harcamatakip.app.presentation.categories.CategoriesScreen
import com.harcamatakip.app.presentation.expenses.AddEditScreen
import com.harcamatakip.app.presentation.expenses.ExpensesScreen
import com.harcamatakip.app.presentation.home.HomeScreen
import com.harcamatakip.app.presentation.recurring.RecurringScreen
import com.harcamatakip.app.presentation.settings.SettingsScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = bottomItems.any { it.screen.route == current }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = current == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(item.screen.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(if (selected) item.selectedIcon else item.icon, item.label)
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 12 } }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onAdd = { nav.navigate(Screen.AddEdit.create()) },
                    onOpen = { id -> nav.navigate(Screen.AddEdit.create(id)) },
                    onSeeAll = {
                        nav.navigate(Screen.Expenses.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onBudget = { nav.navigate(Screen.Budget.route) }
                )
            }
            composable(Screen.Expenses.route) {
                ExpensesScreen(
                    onAdd = { nav.navigate(Screen.AddEdit.create()) },
                    onOpen = { id -> nav.navigate(Screen.AddEdit.create(id)) }
                )
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen(onOpenTransaction = { id -> nav.navigate(Screen.AddEdit.create(id)) })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onCategories = { nav.navigate(Screen.Categories.route) },
                    onBudget = { nav.navigate(Screen.Budget.route) },
                    onRecurring = { nav.navigate(Screen.Recurring.route) }
                )
            }
            composable(
                route = Screen.AddEdit.route,
                arguments = listOf(navArgument("id") {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) {
                AddEditScreen(onBack = { nav.popBackStack() })
            }
            composable(Screen.Categories.route) { CategoriesScreen(onBack = { nav.popBackStack() }) }
            composable(Screen.Budget.route) { BudgetScreen(onBack = { nav.popBackStack() }) }
            composable(Screen.Recurring.route) { RecurringScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
