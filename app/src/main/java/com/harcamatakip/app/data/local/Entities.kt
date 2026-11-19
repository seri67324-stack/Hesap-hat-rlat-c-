package com.harcamatakip.app.data.local

import androidx.room.ColumnInfo
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
    /** "CASH", "DEBIT_CARD" veya "CREDIT_CARD" (eski kayıtlardaki "CARD" migration ile dönüştürülür). */
    val paymentMethod: String = "CASH",
    /** İsteğe bağlı kısa kart/hesap etiketi (örn. "Garanti •1234"). Boş olabilir. */
    @ColumnInfo(defaultValue = "''") val paymentLabel: String = "",
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
    @ColumnInfo(defaultValue = "''") val paymentLabel: String = "",
    /** Frequency enum adı: WEEKLY, MONTHLY, YEARLY */
    val frequency: String,
    val startDay: Long,
    val nextDueDay: Long,
    val active: Boolean = true
)

/**
 * Hızlı işlem şablonu. Tutar kuruş (Long) cinsindendir; tek dokunuşla bugünün tarihiyle işlem üretir.
 * [title] listede görünen kısa addır (örn. "Sabah kahvesi").
 */
@Entity(tableName = "quick_templates", indices = [Index("categoryId")])
data class QuickTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val isIncome: Boolean,
    val categoryId: Long,
    val paymentMethod: String = "CASH",
    @ColumnInfo(defaultValue = "''") val paymentLabel: String = "",
    val note: String = "",
    val useCount: Int = 0,
    /** LocalDate.toEpochDay(); hiç kullanılmadıysa 0 */
    val lastUsedDay: Long = 0
)

/**
 * Bütçe uyarısı durumu: her (bütçe, dönem) için %80 ve %100 eşiklerinin en fazla bir kez bildirildiğini tutar.
 * [categoryId] = 0 genel bütçe; [periodKey] = "2026-11" gibi bütçe dönemi (ay).
 */
@Entity(tableName = "budget_alert_state", primaryKeys = ["categoryId", "periodKey"])
data class BudgetAlertStateEntity(
    val categoryId: Long,
    val periodKey: String,
    val sent80: Boolean = false,
    val sent100: Boolean = false
)
