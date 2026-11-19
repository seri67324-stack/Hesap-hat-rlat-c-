package com.harcamatakip.app.domain

import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.domain.model.DatePreset
import com.harcamatakip.app.domain.model.ExpenseFilter
import com.harcamatakip.app.domain.model.PaymentMethod
import com.harcamatakip.app.domain.model.SortOrder
import com.harcamatakip.app.domain.model.TypeFilter
import com.harcamatakip.app.domain.usecase.ExpenseFilterer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpenseFiltererTest {

    private fun day(d: Int) = LocalDate.of(2026, 11, d).toEpochDay()

    private fun e(
        id: Long, amount: Long, d: Int, cat: Long = 1, income: Boolean = false,
        pay: String = PaymentMethod.CASH, note: String = "", label: String = ""
    ) = ExpenseEntity(
        id = id, amount = amount, isIncome = income, categoryId = cat, date = day(d),
        note = note, paymentMethod = pay, paymentLabel = label
    )

    private val names = mapOf(1L to "Market", 2L to "Ulaşım", 3L to "Maaş")
    private val data = listOf(
        e(1, 1000, 1, cat = 1, pay = PaymentMethod.CASH, note = "ekmek"),
        e(2, 5000, 5, cat = 1, pay = PaymentMethod.DEBIT_CARD, label = "Garanti •1234"),
        e(3, 2500, 10, cat = 2, pay = PaymentMethod.CREDIT_CARD, label = "Yapı Kredi"),
        e(4, 900000, 15, cat = 3, income = true, pay = PaymentMethod.DEBIT_CARD),
        e(5, 700, 20, cat = 2, pay = "CARD") // eski kayıt
    )

    private fun ids(f: ExpenseFilter) = ExpenseFilterer.apply(data, f, names).map { it.id }

    @Test fun noFilter_returnsAllNewestFirst() = assertEquals(listOf(5L, 4L, 3L, 2L, 1L), ids(ExpenseFilter()))

    @Test
    fun paymentFilter_singleMethod() {
        assertEquals(listOf(4L, 2L), ids(ExpenseFilter(paymentMethods = setOf(PaymentMethod.DEBIT_CARD))))
    }

    @Test
    fun paymentFilter_creditIncludesLegacyCard() {
        assertEquals(listOf(5L, 3L), ids(ExpenseFilter(paymentMethods = setOf(PaymentMethod.CREDIT_CARD))))
    }

    @Test
    fun paymentFilter_multipleMethods() {
        val f = ExpenseFilter(paymentMethods = setOf(PaymentMethod.CASH, PaymentMethod.CREDIT_CARD))
        assertEquals(listOf(5L, 3L, 1L), ids(f))
    }

    @Test
    fun categoryFilter_andTypeFilter() {
        assertEquals(listOf(5L, 3L), ids(ExpenseFilter(categoryIds = setOf(2L))))
        assertEquals(listOf(4L), ids(ExpenseFilter(type = TypeFilter.INCOME)))
        assertEquals(listOf(5L, 3L, 2L, 1L), ids(ExpenseFilter(type = TypeFilter.EXPENSE)))
    }

    @Test
    fun dateRange_isInclusiveOnBothEnds() {
        assertEquals(listOf(3L, 2L), ids(ExpenseFilter(from = day(5), to = day(10))))
    }

    @Test
    fun sort_byAmountAndDate() {
        assertEquals(listOf(4L, 2L, 3L, 1L, 5L), ids(ExpenseFilter(sort = SortOrder.AMOUNT_DESC)))
        assertEquals(listOf(5L, 1L, 3L, 2L, 4L), ids(ExpenseFilter(sort = SortOrder.AMOUNT_ASC)))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(ExpenseFilter(sort = SortOrder.DATE_ASC)))
    }

    @Test
    fun search_matchesNoteCategoryAndCardLabel() {
        assertEquals(listOf(1L), ids(ExpenseFilter(query = "ekmek")))
        assertEquals(listOf(5L, 3L), ids(ExpenseFilter(query = "ulaş")))
        assertEquals(listOf(2L), ids(ExpenseFilter(query = "garanti")))
    }

    @Test
    fun combinedFilters_narrowResults() {
        val f = ExpenseFilter(
            type = TypeFilter.EXPENSE,
            categoryIds = setOf(1L),
            paymentMethods = setOf(PaymentMethod.DEBIT_CARD),
            from = day(1), to = day(30),
            minAmount = 2000, maxAmount = 6000,
            sort = SortOrder.AMOUNT_DESC
        )
        assertEquals(listOf(2L), ids(f))
    }

    @Test
    fun activeCount_includesPaymentFilter() {
        assertEquals(0, ExpenseFilter().activeCount)
        assertEquals(1, ExpenseFilter(paymentMethods = setOf(PaymentMethod.CASH)).activeCount)
        assertFalse(ExpenseFilter().isActive)
        assertTrue(ExpenseFilter(type = TypeFilter.INCOME).isActive)
    }

    @Test
    fun datePresets() {
        val today = LocalDate.of(2026, 11, 19)
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30), DatePreset.THIS_MONTH.range(today))
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31), DatePreset.LAST_MONTH.range(today))
        assertEquals(LocalDate.of(2026, 10, 21) to today, DatePreset.LAST_30_DAYS.range(today))
    }
}
