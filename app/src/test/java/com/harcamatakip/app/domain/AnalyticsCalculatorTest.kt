package com.harcamatakip.app.domain

import com.harcamatakip.app.data.local.CategoryMonthRow
import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.local.MonthSumRow
import com.harcamatakip.app.data.local.RecurringEntity
import com.harcamatakip.app.domain.model.Frequency
import com.harcamatakip.app.domain.model.Period
import com.harcamatakip.app.domain.model.RecurrenceUtil
import com.harcamatakip.app.domain.usecase.AnalyticsCalculator
import com.harcamatakip.app.domain.usecase.CategoryTotal
import com.harcamatakip.app.domain.usecase.DayTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AnalyticsCalculatorTest {

    private fun exp(
        amount: Long,
        date: LocalDate,
        income: Boolean = false,
        recurringId: Long? = null,
        category: Long = 1L
    ) = ExpenseEntity(
        amount = amount,
        isIncome = income,
        categoryId = category,
        date = date.toEpochDay(),
        recurringId = recurringId
    )

    private val today = LocalDate.of(2026, 11, 10)
    private val from = LocalDate.of(2026, 11, 1)
    private val to = LocalDate.of(2026, 11, 30)

    @Test
    fun previousComparable_usesSameNumberOfDays() {
        val all = listOf(
            exp(100, LocalDate.of(2026, 10, 5)),
            exp(500, LocalDate.of(2026, 10, 20)), // ayın 10'undan sonrası: karşılaştırmaya girmez
            exp(200, LocalDate.of(2026, 11, 3))
        )
        assertEquals(100L, AnalyticsCalculator.previousComparable(all, Period.MONTH, from, to, today))
    }

    @Test
    fun previousComparable_pastPeriodUsesFullPreviousPeriod() {
        val all = listOf(exp(100, LocalDate.of(2026, 10, 5)), exp(500, LocalDate.of(2026, 10, 20)))
        // Dönem tamamen geçmişte (bugün Aralık): önceki ayın tamamı
        val later = LocalDate.of(2026, 12, 15)
        assertEquals(600L, AnalyticsCalculator.previousComparable(all, Period.MONTH, from, to, later))
    }

    @Test
    fun previousRange_monthIsCalendarMonth() {
        val (a, b) = AnalyticsCalculator.previousRange(Period.MONTH, from, to)
        assertEquals(LocalDate.of(2026, 10, 1), a)
        assertEquals(LocalDate.of(2026, 10, 31), b)
    }

    @Test
    fun rangeFor_weekStartsOnGivenDay() {
        val (a, b) = AnalyticsCalculator.rangeFor(Period.WEEK, 0, DayOfWeek.MONDAY, null, LocalDate.of(2026, 11, 18))
        assertEquals(LocalDate.of(2026, 11, 16), a)
        assertEquals(LocalDate.of(2026, 11, 22), b)
    }

    @Test
    fun projectExpense_keepsFixedAndExtrapolatesVariable() {
        val list = listOf(
            exp(1000, LocalDate.of(2026, 11, 1), recurringId = 7L), // sabit
            exp(100, LocalDate.of(2026, 11, 2)),
            exp(200, LocalDate.of(2026, 11, 5))
        )
        // 10 gün geçti, 30 günlük ay: 1000 + 300*30/10 + 500 (yaklaşan sabit) = 2400
        assertEquals(2400L, AnalyticsCalculator.projectExpense(list, from, to, today, 500L))
    }

    @Test
    fun upcomingRecurring_countsOnlyFutureDueDatesInRange() {
        val r = RecurringEntity(
            amount = 300,
            isIncome = false,
            categoryId = 1,
            frequency = Frequency.WEEKLY.name,
            startDay = LocalDate.of(2026, 11, 3).toEpochDay(),
            nextDueDay = LocalDate.of(2026, 11, 17).toEpochDay()
        )
        // 17 ve 24 Kasım bugünden (10 Kasım) sonra ve ay içinde
        assertEquals(600L, AnalyticsCalculator.upcomingRecurringExpense(listOf(r), today, to))
    }

    @Test
    fun groupSmall_mergesTailIntoOther() {
        val totals = (1..9).map { CategoryTotal(null, (10 - it) * 100L, 0.1f) }
        val grouped = AnalyticsCalculator.groupSmall(totals, 7)
        assertEquals(7, grouped.size)
        assertEquals(AnalyticsCalculator.GROUP_ID, grouped.last().category?.id)
        // 7. dilim "Diğer": 7., 8. ve 9. kalemlerin toplamı
        assertEquals(totals.drop(6).sumOf { it.amount }, grouped.last().amount)
    }

    @Test
    fun groupSmall_leavesShortListsUntouched() {
        val totals = (1..5).map { CategoryTotal(null, it * 100L, 0.2f) }
        assertEquals(totals, AnalyticsCalculator.groupSmall(totals, 7))
    }

    @Test
    fun trendPoints_bucketsByLength() {
        val start = LocalDate.of(2026, 1, 1)
        fun days(n: Int) = (0 until n).map { DayTotal(start.plusDays(it.toLong()), 10L) }
        assertEquals(30, AnalyticsCalculator.trendPoints(days(30)).size)
        assertEquals(15, AnalyticsCalculator.trendPoints(days(100)).size) // haftalık
        assertEquals(10, AnalyticsCalculator.trendPoints(days(300)).size) // aylık (Oca..Eki)
    }

    @Test
    fun monthly_sumsIncomeAndExpensePerMonth() {
        val all = listOf(
            exp(1000, LocalDate.of(2026, 11, 2), income = true),
            exp(300, LocalDate.of(2026, 11, 3)),
            exp(200, LocalDate.of(2026, 10, 3)),
            exp(999, LocalDate.of(2025, 1, 3)) // pencere dışı
        )
        val m = AnalyticsCalculator.monthly(all, 2, today)
        assertEquals(2, m.size)
        assertEquals(200L, m[0].expense)
        assertEquals(1000L, m[1].income)
        assertEquals(300L, m[1].expense)
    }

    @Test
    fun cumulative_runningTotal() {
        val d = listOf(DayTotal(today, 100), DayTotal(today.plusDays(1), 0), DayTotal(today.plusDays(2), 50))
        assertEquals(listOf(100L, 100L, 150L), AnalyticsCalculator.cumulative(d))
    }

    @Test
    fun weekdayAverages_startsFromWeekStart() {
        val monday = LocalDate.of(2026, 11, 16)
        val d = listOf(DayTotal(monday, 200), DayTotal(monday.plusDays(7), 400), DayTotal(monday.plusDays(1), 90))
        val avg = AnalyticsCalculator.weekdayAverages(d, DayOfWeek.MONDAY)
        assertEquals(DayOfWeek.MONDAY, avg[0].day)
        assertEquals(300L, avg[0].average)
        assertEquals(90L, avg[1].average)
    }

    @Test
    fun recurrence_monthEndDoesNotDrift() {
        val start = LocalDate.of(2027, 1, 31).toEpochDay()
        val feb = RecurrenceUtil.nextAfter(start, start, Frequency.MONTHLY.name)
        assertEquals(LocalDate.of(2027, 2, 28), LocalDate.ofEpochDay(feb))
        val mar = RecurrenceUtil.nextAfter(start, feb, Frequency.MONTHLY.name)
        assertEquals(LocalDate.of(2027, 3, 31), LocalDate.ofEpochDay(mar))
    }

    @Test
    fun elapsedDays_isCappedAtPeriodLength() {
        assertEquals(10L, AnalyticsCalculator.elapsedDays(from, to, today))
        assertEquals(30L, AnalyticsCalculator.elapsedDays(from, to, LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun paymentSplit_separatesCashAndCard() {
        val list = listOf(
            exp(100, today).copy(paymentMethod = "CASH"),
            exp(250, today).copy(paymentMethod = "CARD")
        )
        val p = AnalyticsCalculator.paymentSplit(list)
        assertEquals(100L, p.cash)
        assertEquals(250L, p.card)
    }

    @Test
    fun monthlyFromSums_fillsMissingMonthsWithZero() {
        val rows = listOf(
            MonthSumRow("2026-11", true, 1000L),
            MonthSumRow("2026-11", false, 300L),
            MonthSumRow("2026-09", false, 50L)
        )
        val m = AnalyticsCalculator.monthlyFromSums(rows, 3, today)
        assertEquals(listOf("2026-09", "2026-10", "2026-11"), m.map { it.ym.toString() })
        assertEquals(50L, m[0].expense)
        assertEquals(0L, m[1].expense)
        assertEquals(1000L, m[2].income)
        assertEquals(300L, m[2].expense)
    }

    @Test
    fun categoryMonthlyFromSums_buildsPerCategorySeries() {
        val rows = listOf(
            CategoryMonthRow(5L, "2026-11", 120L),
            CategoryMonthRow(5L, "2026-10", 80L),
            CategoryMonthRow(9L, "2026-11", 10L),
            CategoryMonthRow(5L, "2020-01", 999L) // pencere dışı: yok sayılır
        )
        val r = AnalyticsCalculator.categoryMonthlyFromSums(rows, 3, today)
        assertEquals(listOf(0L, 80L, 120L), r[5L])
        assertEquals(listOf(0L, 0L, 10L), r[9L])
    }

    @Test
    fun monthsWindowStart_isFirstDayOfOldestMonth() {
        val start = AnalyticsCalculator.monthsWindowStart(3, today)
        assertEquals(LocalDate.of(2026, 9, 1), LocalDate.ofEpochDay(start))
    }
}
