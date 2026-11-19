package com.harcamatakip.app.domain.usecase

import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.local.QuickTemplateEntity
import com.harcamatakip.app.domain.model.PaymentMethod

/** Sık tekrarlanan bir işlem kombinasyonu (şablon önerisi). Tutar kuruştur. */
data class ComboSuggestion(
    val isIncome: Boolean,
    val categoryId: Long,
    val amount: Long,
    val paymentMethod: String,
    val paymentLabel: String,
    val note: String,
    val count: Int,
    val lastDay: Long
)

object FrequentCombos {

    private data class Key(
        val isIncome: Boolean,
        val categoryId: Long,
        val amount: Long,
        val method: String,
        val note: String
    )

    private fun key(isIncome: Boolean, categoryId: Long, amount: Long, method: String, note: String) =
        Key(isIncome, categoryId, amount, PaymentMethod.normalize(method), note.trim().lowercase())

    /**
     * Son [windowDays] günde en az [minCount] kez aynı (tür, kategori, tutar, ödeme yöntemi, not)
     * ile girilen işlemleri öneri olarak döndürür. Zaten şablonu olanlar ve tekrarlayan işlemlerden
     * otomatik üretilenler hariçtir.
     */
    fun suggest(
        expenses: List<ExpenseEntity>,
        templates: List<QuickTemplateEntity>,
        today: Long,
        windowDays: Int = 120,
        minCount: Int = 2,
        limit: Int = 4
    ): List<ComboSuggestion> {
        val existing = templates.map { key(it.isIncome, it.categoryId, it.amount, it.paymentMethod, it.note) }.toSet()
        val since = today - windowDays
        return expenses.asSequence()
            .filter { it.recurringId == null && it.date in since..today }
            .groupBy { key(it.isIncome, it.categoryId, it.amount, it.paymentMethod, it.note) }
            .filter { (k, list) -> list.size >= minCount && k !in existing }
            .map { (_, list) ->
                val latest = list.maxWith(compareBy<ExpenseEntity> { it.date }.thenBy { it.id })
                ComboSuggestion(
                    isIncome = latest.isIncome,
                    categoryId = latest.categoryId,
                    amount = latest.amount,
                    paymentMethod = PaymentMethod.normalize(latest.paymentMethod),
                    paymentLabel = latest.paymentLabel,
                    note = latest.note.trim(),
                    count = list.size,
                    lastDay = latest.date
                )
            }
            .sortedWith(compareByDescending<ComboSuggestion> { it.count }.thenByDescending { it.lastDay })
            .take(limit)
            .toList()
    }
}
