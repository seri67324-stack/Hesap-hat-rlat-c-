package com.harcamatakip.app.domain

import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.local.QuickTemplateEntity
import com.harcamatakip.app.domain.model.PaymentMethod
import com.harcamatakip.app.domain.usecase.FrequentCombos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequentCombosTest {
    private val today = 1000L

    private fun e(
        id: Long, amount: Long, date: Long, cat: Long = 1, note: String = "", pay: String = PaymentMethod.CASH,
        recurringId: Long? = null, income: Boolean = false
    ) = ExpenseEntity(
        id = id, amount = amount, isIncome = income, categoryId = cat, date = date, note = note,
        paymentMethod = pay, recurringId = recurringId
    )

    @Test
    fun repeatedCombination_isSuggested_sortedByCount() {
        val list = listOf(
            e(1, 2500, 990, note = "Kahve"), e(2, 2500, 995, note = "Kahve"), e(3, 2500, 999, note = "Kahve"),
            e(4, 9000, 980, cat = 2), e(5, 9000, 990, cat = 2),
            e(6, 111, 999, cat = 3) // tek seferlik: öneri değil
        )
        val s = FrequentCombos.suggest(list, emptyList(), today)
        assertEquals(2, s.size)
        assertEquals(3, s[0].count)
        assertEquals(2500L, s[0].amount)
        assertEquals("Kahve", s[0].note)
        assertEquals(2, s[1].count)
    }

    @Test
    fun noteAndLegacyPaymentAreNormalizedWhenGrouping() {
        val list = listOf(
            e(1, 4000, 990, note = "Kahve", pay = "CARD"),
            e(2, 4000, 995, note = " kahve ", pay = PaymentMethod.CREDIT_CARD)
        )
        val s = FrequentCombos.suggest(list, emptyList(), today)
        assertEquals(1, s.size)
        assertEquals(PaymentMethod.CREDIT_CARD, s[0].paymentMethod)
    }

    @Test
    fun existingTemplate_isNotSuggestedAgain() {
        val list = listOf(e(1, 2500, 990, note = "Kahve"), e(2, 2500, 995, note = "Kahve"))
        val template = QuickTemplateEntity(
            id = 1, title = "Kahve", amount = 2500, isIncome = false, categoryId = 1, note = "Kahve"
        )
        assertTrue(FrequentCombos.suggest(list, listOf(template), today).isEmpty())
    }

    @Test
    fun recurringGeneratedAndOldEntries_areIgnored() {
        val list = listOf(
            e(1, 5000, 990, recurringId = 7), e(2, 5000, 995, recurringId = 7), // otomatik üretilen
            e(3, 800, 100), e(4, 800, 110)                                     // pencere dışı (120 günden eski)
        )
        assertTrue(FrequentCombos.suggest(list, emptyList(), today).isEmpty())
    }

    @Test
    fun limitIsRespected() {
        val list = (1L..6L).flatMap { c -> listOf(e(c * 10, 100 * c, 990, cat = c), e(c * 10 + 1, 100 * c, 995, cat = c)) }
        assertEquals(4, FrequentCombos.suggest(list, emptyList(), today).size)
        assertEquals(6, FrequentCombos.suggest(list, emptyList(), today, limit = 10).size)
    }
}
