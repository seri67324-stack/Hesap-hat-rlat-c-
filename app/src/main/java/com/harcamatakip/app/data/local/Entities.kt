package com.harcamatakip.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Kategori. Her tür (gelir/gider) için bir adet "Diğer" (isFallback) kategorisi bulunur. */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val isIncome: Boolean = false,
    val isFallback: Boolean = false
)

/** İşlem. Tutar kuruş cinsinden ve her zaman pozitiftir; yön isIncome ile belirlenir. */
@Entity(tableName = "expenses", indices = [Index("categoryId"), Index("date")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val isIncome: Boolean,
    val categoryId: Long,
    /** LocalDate.toEpochDay() */
    val date: Long,
    val note: String = "",
    /** "CASH" veya "CARD" */
    val paymentMethod: String = "CASH",
    val recurringId: Long? = null
)

/** Aylık bütçe. categoryId = 0 ise genel (tüm giderler) bütçedir. */
@Entity(tableName = "budgets", indices = [Index(value = ["categoryId"], unique = true)])
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long = 0,
    val limitAmount: Long
)

/** Tekrarlayan işlem (kira, abonelik, maaş...). */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val isIncome: Boolean,
    val categoryId: Long,
    val note: String = "",
    val paymentMethod: String = "CASH",
    /** Frequency enum adı: WEEKLY, MONTHLY, YEARLY */
    val frequency: String,
    val startDay: Long,
    val nextDueDay: Long,
    val active: Boolean = true
)
