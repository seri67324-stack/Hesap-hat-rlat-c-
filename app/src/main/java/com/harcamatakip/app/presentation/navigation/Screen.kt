package com.harcamatakip.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Expenses : Screen("expenses")
    data object Analytics : Screen("analytics")
    data object Settings : Screen("settings")
    data object AddEdit : Screen("add_edit/{id}") {
        fun create(id: Long = -1L) = "add_edit/$id"
    }
    data object Categories : Screen("categories")
    data object Budget : Screen("budget")
    data object Recurring : Screen("recurring")
}

data class BottomItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

val bottomItems = listOf(
    BottomItem(Screen.Home, "Ana Sayfa", Icons.Outlined.Home, Icons.Rounded.Home),
    BottomItem(Screen.Expenses, "Harcamalar", Icons.Outlined.ReceiptLong, Icons.Rounded.ReceiptLong),
    BottomItem(Screen.Analytics, "Analiz", Icons.Outlined.PieChart, Icons.Rounded.PieChart),
    BottomItem(Screen.Settings, "Ayarlar", Icons.Outlined.Settings, Icons.Rounded.Settings)
)
