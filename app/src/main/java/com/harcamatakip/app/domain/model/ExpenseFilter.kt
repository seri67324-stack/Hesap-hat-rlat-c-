package com.harcamatakip.app.domain.model

import java.time.LocalDate

enum class TypeFilter(val label: String) { ALL("Hepsi"), EXPENSE("Gider"), INCOME("Gelir") }

enum class SortOrder(val label: String) {
    DATE_DESC("Tarih (yeni → eski)"),
    DATE_ASC("Tarih (eski → yeni)"),
    AMOUNT_DESC("Tutar (yüksek → düşük)"),
    AMOUNT_ASC("Tutar (düşük → yüksek)")
}

/** Hızlı tarih aralığı kısayolları (filtre sheet'indeki çipler). */
enum class DatePreset(val label: String) {
    THIS_MONTH("Bu ay"),
    LAST_MONTH("Geçen ay"),
    LAST_30_DAYS("Son 30 gün");

    fun range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        THIS_MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
        LAST_MONTH -> {
            val first = today.withDayOfMonth(1).minusMonths(1)
            first to first.withDayOfMonth(first.lengthOfMonth())
        }
        LAST_30_DAYS -> today.minusDays(29) to today
    }
}

data class ExpenseFilter(
    val query: String = "",
    val type: TypeFilter = TypeFilter.ALL,
    val categoryIds: Set<Long> = emptySet(),
    /** Boşsa tüm yöntemler; aksi halde yalnızca seçilen [PaymentMethod] kodları. */
    val paymentMethods: Set<String> = emptySet(),
    /** Epoch day, uçlar dahil. İkisi birlikte anlamlıdır. */
    val from: Long? = null,
    val to: Long? = null,
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    val sort: SortOrder = SortOrder.DATE_DESC
) {
    /** Filtre sheet'indeki aktif filtre sayısı (rozet için). */
    val activeCount: Int
        get() = listOf(
            categoryIds.isNotEmpty(),
            paymentMethods.isNotEmpty(),
            from != null && to != null,
            minAmount != null,
            maxAmount != null,
            sort != SortOrder.DATE_DESC
        ).count { it }

    /** Arama, tür veya sheet filtrelerinden herhangi biri açık mı? */
    val isActive: Boolean get() = query.isNotBlank() || type != TypeFilter.ALL || activeCount > 0
}
