package com.harcamatakip.app.data.local

/** İlk açılışta eklenen hazır kategoriler. */
object DefaultCategories {

    val fallbackExpense = CategoryEntity(
        name = "Diğer", iconKey = "more", colorArgb = 0xFF757575,
        isIncome = false, isFallback = true
    )
    val fallbackIncome = CategoryEntity(
        name = "Diğer Gelir", iconKey = "more", colorArgb = 0xFF757575,
        isIncome = true, isFallback = true
    )

    val all: List<CategoryEntity> = listOf(
        CategoryEntity(name = "Market", iconKey = "cart", colorArgb = 0xFF43A047),
        CategoryEntity(name = "Fatura", iconKey = "receipt", colorArgb = 0xFFFB8C00),
        CategoryEntity(name = "Ulaşım", iconKey = "car", colorArgb = 0xFF1E88E5),
        CategoryEntity(name = "Eğlence", iconKey = "movie", colorArgb = 0xFF8E24AA),
        CategoryEntity(name = "Sağlık", iconKey = "health", colorArgb = 0xFFE53935),
        CategoryEntity(name = "Abonelik", iconKey = "subs", colorArgb = 0xFF00ACC1),
        CategoryEntity(name = "Kira", iconKey = "home", colorArgb = 0xFF6D4C41),
        CategoryEntity(name = "Yemek", iconKey = "food", colorArgb = 0xFFFF7043),
        CategoryEntity(name = "Giyim", iconKey = "cloth", colorArgb = 0xFFD81B60),
        CategoryEntity(name = "Eğitim", iconKey = "school", colorArgb = 0xFF3949AB),
        fallbackExpense,
        CategoryEntity(name = "Maaş", iconKey = "payments", colorArgb = 0xFF2E7D32, isIncome = true),
        CategoryEntity(name = "Ek Gelir", iconKey = "work", colorArgb = 0xFF00897B, isIncome = true),
        CategoryEntity(name = "Yatırım", iconKey = "trend", colorArgb = 0xFF5E35B1, isIncome = true),
        CategoryEntity(name = "Hediye", iconKey = "gift", colorArgb = 0xFFEC407A, isIncome = true),
        fallbackIncome
    )
}
