package com.harcamatakip.app.core.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Kategori ikonları: veritabanında anahtar (String) olarak saklanır. */
object CategoryIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "cart" to Icons.Rounded.ShoppingCart,
        "receipt" to Icons.Rounded.Receipt,
        "car" to Icons.Rounded.DirectionsCar,
        "movie" to Icons.Rounded.Movie,
        "health" to Icons.Rounded.LocalHospital,
        "subs" to Icons.Rounded.Subscriptions,
        "home" to Icons.Rounded.Home,
        "food" to Icons.Rounded.Restaurant,
        "cloth" to Icons.Rounded.Checkroom,
        "school" to Icons.Rounded.School,
        "payments" to Icons.Rounded.Payments,
        "work" to Icons.Rounded.Work,
        "trend" to Icons.Rounded.TrendingUp,
        "gift" to Icons.Rounded.CardGiftcard,
        "flight" to Icons.Rounded.Flight,
        "pets" to Icons.Rounded.Pets,
        "fitness" to Icons.Rounded.FitnessCenter,
        "savings" to Icons.Rounded.Savings,
        "wifi" to Icons.Rounded.Wifi,
        "fuel" to Icons.Rounded.LocalGasStation,
        "coffee" to Icons.Rounded.Coffee,
        "bag" to Icons.Rounded.ShoppingBag,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "bolt" to Icons.Rounded.Bolt,
        "game" to Icons.Rounded.SportsEsports,
        "music" to Icons.Rounded.MusicNote,
        "book" to Icons.Rounded.MenuBook,
        "build" to Icons.Rounded.Build,
        "child" to Icons.Rounded.ChildCare,
        "category" to Icons.Rounded.Category,
        "more" to Icons.Rounded.MoreHoriz
    )

    fun get(key: String?): ImageVector = all[key] ?: Icons.Rounded.Category
}

/** Kategori renk paleti (ARGB). */
val CategoryPalette: List<Long> = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
    0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1, 0xFF00897B,
    0xFF43A047, 0xFF7CB342, 0xFFFDD835, 0xFFFB8C00,
    0xFFFF7043, 0xFF6D4C41, 0xFF757575, 0xFF546E7A
)
