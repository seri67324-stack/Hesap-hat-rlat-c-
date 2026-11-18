package com.harcamatakip.app.data.repository

import android.content.Context
import com.harcamatakip.app.core.util.Notifier
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Aylık bütçelerin %80 ve %100'üne ulaşılınca (ay başına bir kez) bildirim gönderir. */
@Singleton
class BudgetAlertManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val expenses: ExpenseRepository,
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository
) {
    suspend fun check() {
        val budgetList = budgets.getAll()
        if (budgetList.isEmpty()) return

        val ym = YearMonth.from(LocalDate.now())
        val ymKey = ym.toString()
        val from = ym.atDay(1).toEpochDay()
        val to = ym.atEndOfMonth().toEpochDay()
        val monthExpenses = expenses.getAll().filter { !it.isIncome && it.date in from..to }
        val cats = categories.getAll().associateBy { it.id }
        val sent = settings.notifiedKeys()
        val symbol = settings.settingsOnce().currency.symbol

        for (b in budgetList) {
            if (b.limitAmount <= 0) continue
            val spent = if (b.categoryId == 0L) {
                monthExpenses.sumOf { it.amount }
            } else {
                monthExpenses.filter { it.categoryId == b.categoryId }.sumOf { it.amount }
            }
            val ratio = spent.toDouble() / b.limitAmount
            val level = when {
                ratio >= 1.0 -> 100
                ratio >= 0.8 -> 80
                else -> 0
            }
            if (level == 0) continue

            val key = "$ymKey|budget|${b.categoryId}|$level"
            if (key in sent) continue

            val name = if (b.categoryId == 0L) "Aylık bütçen"
            else "${cats[b.categoryId]?.name ?: "Kategori"} bütçen"
            val title = if (level == 100) "Bütçe aşıldı" else "Bütçenin %80'ine ulaştın"
            val text = "$name: ${spent.formatMoney(symbol)} / ${b.limitAmount.formatMoney(symbol)}"
            Notifier.notify(ctx, Notifier.CH_BUDGET, key.hashCode(), title, text)

            settings.addNotified(key, ymKey)
            // %100 geçildiyse %80 uyarısı artık anlamsız
            if (level == 100) settings.addNotified("$ymKey|budget|${b.categoryId}|80", ymKey)
        }
    }
}
