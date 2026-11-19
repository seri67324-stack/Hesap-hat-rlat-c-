package com.harcamatakip.app.domain.usecase

import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.domain.model.ExpenseFilter
import com.harcamatakip.app.domain.model.PaymentMethod
import com.harcamatakip.app.domain.model.SortOrder
import com.harcamatakip.app.domain.model.TypeFilter

/** Harcamalar ekranındaki filtre + sıralama mantığı (Android bağımlılığı yok, birim testli). */
object ExpenseFilterer {

    fun apply(
        all: List<ExpenseEntity>,
        f: ExpenseFilter,
        categoryNames: Map<Long, String>
    ): List<ExpenseEntity> {
        val q = f.query.trim()
        val methods = f.paymentMethods.map { PaymentMethod.normalize(it) }.toSet()
        val filtered = all.filter { e ->
            val typeOk = when (f.type) {
                TypeFilter.ALL -> true
                TypeFilter.EXPENSE -> !e.isIncome
                TypeFilter.INCOME -> e.isIncome
            }
            typeOk &&
                (f.categoryIds.isEmpty() || e.categoryId in f.categoryIds) &&
                (methods.isEmpty() || PaymentMethod.normalize(e.paymentMethod) in methods) &&
                (f.from == null || e.date >= f.from) &&
                (f.to == null || e.date <= f.to) &&
                (f.minAmount == null || e.amount >= f.minAmount) &&
                (f.maxAmount == null || e.amount <= f.maxAmount) &&
                (q.isEmpty() ||
                    e.note.contains(q, ignoreCase = true) ||
                    e.paymentLabel.contains(q, ignoreCase = true) ||
                    (categoryNames[e.categoryId] ?: "").contains(q, ignoreCase = true))
        }
        return sort(filtered, f.sort)
    }

    fun sort(list: List<ExpenseEntity>, order: SortOrder): List<ExpenseEntity> = when (order) {
        SortOrder.DATE_DESC -> list.sortedWith(compareByDescending<ExpenseEntity> { it.date }.thenByDescending { it.id })
        SortOrder.DATE_ASC -> list.sortedWith(compareBy<ExpenseEntity> { it.date }.thenBy { it.id })
        SortOrder.AMOUNT_DESC -> list.sortedWith(compareByDescending<ExpenseEntity> { it.amount }.thenByDescending { it.date }.thenByDescending { it.id })
        SortOrder.AMOUNT_ASC -> list.sortedWith(compareBy<ExpenseEntity> { it.amount }.thenByDescending { it.date }.thenByDescending { it.id })
    }
}
