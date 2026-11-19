package com.harcamatakip.app.domain

import com.harcamatakip.app.domain.usecase.SafeSpending
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SafeSpendingTest {
    private val end = LocalDate.of(2026, 11, 30)

    @Test
    fun daysLeft_includesToday() {
        assertEquals(21, SafeSpending.daysLeftInclusive(LocalDate.of(2026, 11, 10), end))
        assertEquals(1, SafeSpending.daysLeftInclusive(end, end))
        assertEquals(1, SafeSpending.daysLeftInclusive(end.plusDays(3), end)) // en az 1
    }

    @Test
    fun perDay_isRemainingDividedByDaysLeft() {
        // Limit 3.000,00 ₺, harcanan 1.000,00 ₺, 21 gün kaldı -> 2.000,00 / 21 = 95,23 ₺ (kuruş: 9523)
        val r = SafeSpending.compute(300_000, 100_000, LocalDate.of(2026, 11, 10), end)!!
        assertEquals(200_000L, r.remaining)
        assertEquals(21, r.daysLeft)
        assertEquals(9_523L, r.perDay)
        assertFalse(r.isExceeded)
        val text = SafeSpending.summary(r, "₺")
        assertTrue(text, text.contains("95,23"))
        assertTrue(text, text.contains("21 gün kaldı"))
    }

    @Test
    fun exceeded_hasNoDailyAllowance() {
        val r = SafeSpending.compute(300_000, 350_000, LocalDate.of(2026, 11, 10), end)!!
        assertTrue(r.isExceeded)
        assertEquals(50_000L, r.exceededBy)
        assertEquals(0L, r.perDay)
        assertTrue(SafeSpending.summary(r, "₺").contains("aştın"))
    }

    @Test
    fun lastDay_usesTodayWording() {
        val r = SafeSpending.compute(300_000, 100_000, end, end)!!
        assertEquals(200_000L, r.perDay)
        assertTrue(SafeSpending.summary(r, "₺").startsWith("Bugün en fazla"))
    }

    @Test
    fun noLimit_returnsNull() {
        assertNull(SafeSpending.compute(0, 100, LocalDate.of(2026, 11, 10), end))
    }
}
