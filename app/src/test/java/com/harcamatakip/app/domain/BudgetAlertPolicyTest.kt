package com.harcamatakip.app.domain

import com.harcamatakip.app.data.local.BudgetAlertStateEntity
import com.harcamatakip.app.domain.usecase.BudgetAlertPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAlertPolicyTest {

    private val limit = 1000L
    private fun fresh() = BudgetAlertStateEntity(categoryId = 0, periodKey = "2026-11")

    @Test
    fun belowEightyPercent_noNotification() {
        val d = BudgetAlertPolicy.evaluate(799, limit, fresh())
        assertNull(d.level)
        assertEquals(fresh(), d.newState)
    }

    @Test
    fun exactlyEightyPercent_notifiesOnce() {
        val first = BudgetAlertPolicy.evaluate(800, limit, fresh())
        assertEquals(BudgetAlertPolicy.LEVEL_80, first.level)
        assertTrue(first.newState.sent80)
        val second = BudgetAlertPolicy.evaluate(850, limit, first.newState)
        assertNull(second.level)
    }

    @Test
    fun exceeding_notifiesOnceAndMarksBothLevels() {
        val d = BudgetAlertPolicy.evaluate(1000, limit, fresh())
        assertEquals(BudgetAlertPolicy.LEVEL_100, d.level)
        assertTrue(d.newState.sent80 && d.newState.sent100)
        assertNull(BudgetAlertPolicy.evaluate(1500, limit, d.newState).level)
    }

    @Test
    fun eightyThenHundred_eachNotifiedOnce() {
        val a = BudgetAlertPolicy.evaluate(800, limit, fresh())
        val b = BudgetAlertPolicy.evaluate(1100, limit, a.newState)
        assertEquals(BudgetAlertPolicy.LEVEL_80, a.level)
        assertEquals(BudgetAlertPolicy.LEVEL_100, b.level)
        assertNull(BudgetAlertPolicy.evaluate(1200, limit, b.newState).level)
    }

    @Test
    fun manyConsecutiveExpenses_neverSpam() {
        var state = fresh()
        val sent = mutableListOf<Int>()
        // Aynı kategoriye art arda 100'er birimlik 15 işlem
        for (spent in 100..1500 step 100) {
            val d = BudgetAlertPolicy.evaluate(spent.toLong(), limit, state)
            d.level?.let { sent += it }
            state = d.newState
        }
        assertEquals(listOf(80, 100), sent)
    }

    @Test
    fun deletingExpenseAndReAdding_doesNotNotifyAgainInSamePeriod() {
        val a = BudgetAlertPolicy.evaluate(900, limit, fresh())
        val afterDelete = BudgetAlertPolicy.evaluate(100, limit, a.newState) // düştü
        val again = BudgetAlertPolicy.evaluate(900, limit, afterDelete.newState)
        assertEquals(BudgetAlertPolicy.LEVEL_80, a.level)
        assertNull(again.level)
    }

    @Test
    fun newPeriod_startsFresh() {
        val used = BudgetAlertPolicy.evaluate(1000, limit, fresh()).newState
        val nextMonth = used.copy(periodKey = "2026-12", sent80 = false, sent100 = false)
        assertEquals(BudgetAlertPolicy.LEVEL_100, BudgetAlertPolicy.evaluate(1000, limit, nextMonth).level)
    }

    @Test
    fun zeroLimit_neverNotifies() {
        assertNull(BudgetAlertPolicy.evaluate(500, 0, fresh()).level)
    }
}
