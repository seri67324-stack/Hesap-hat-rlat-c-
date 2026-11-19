package com.harcamatakip.app.domain.usecase

import com.harcamatakip.app.core.util.changePercent
import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.data.local.CategoryMonthRow
import com.harcamatakip.app.data.local.MonthSumRow
import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.local.RecurringEntity
import com.harcamatakip.app.domain.model.PaymentMethod
import com.harcamatakip.app.domain.model.Period
import com.harcamatakip.app.domain.model.RecurrenceUtil
import com.harcamatakip.app.domain.model.TxItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class CategoryTotal(val category: CategoryEntity?, val amount: Long, val percent: Float)
data class MonthTotal(val ym: YearMonth, val income: Long, val expense: Long)
data class DayTotal(val day: LocalDate, val expense: Long)

/** Çizgi grafikteki bir nokta: günlük, haftalık veya aylık toplam olabilir. */
data class TrendPoint(val start: LocalDate, val end: LocalDate, val value: Long)

data class WeekdayAvg(val day: DayOfWeek, val average: Long)
/** Kart/hesap etiketi bazında toplam (ör. "Garanti •1234": 1.250 ₺). */
data class LabelTotal(val method: String, val label: String, val amount: Long)

/** Ödeme yöntemi dağılımı: Nakit / Banka Kartı / Kredi Kartı + etiket kırılımı. */
data class PaymentSplit(
    val cash: Long,
    val debit: Long,
    val credit: Long,
    val labels: List<LabelTotal> = emptyList()
) {
    /** Her iki kart türünün toplamı. */
    val card: Long get() = debit + credit
    val total: Long get() = cash + debit + credit

    fun amountOf(method: String): Long = when (PaymentMethod.normalize(method)) {
        PaymentMethod.DEBIT_CARD -> debit
        PaymentMethod.CREDIT_CARD -> credit
        else -> cash
    }
}

/** Önceki dönemle karşılaştırma. [changePct] önceki 0 ise null ("yeni" harcama). */
data class CategoryTrend(
    val categoryId: Long,
    val current: Long,
    val previous: Long,
    val changePct: Double?
)

/** "Dönem sonu tahmini toplam" kartındaki açıklamanın ham sayıları (hepsi kuruş). */
data class ProjectionBreakdown(
    val fixed: Long,
    val variableSoFar: Long,
    val elapsedDays: Long,
    val lengthDays: Long,
    val dailyVariableRate: Long,
    val projectedVariable: Long,
    val upcomingRecurring: Long,
    val total: Long
) {
    val remainingDays: Long get() = (lengthDays - elapsedDays).coerceAtLeast(0)
}

data class Insights(
    val changePct: Double?,
    val previousLabel: String,
    val topCategory: CategoryTotal?,
    val avgDaily: Long,
    val projected: Long?,
    val biggest: TxItem?
)

/** Saf hesaplama fonksiyonları (test edilebilir, Android bağımlılığı yok). */
object AnalyticsCalculator {

    /** "Diğer" grubu için kullanılan yapay kategorinin kimliği. */
    const val GROUP_ID = -1L

    fun rangeFor(
        period: Period,
        offset: Int,
        weekStart: DayOfWeek,
        custom: Pair<LocalDate, LocalDate>?,
        today: LocalDate
    ): Pair<LocalDate, LocalDate> = when (period) {
        Period.WEEK -> {
            val start = today.with(TemporalAdjusters.previousOrSame(weekStart)).plusWeeks(offset.toLong())
            start to start.plusDays(6)
        }
        Period.MONTH -> {
            val ym = YearMonth.from(today).plusMonths(offset.toLong())
            ym.atDay(1) to ym.atEndOfMonth()
        }
        Period.YEAR -> {
            val year = today.year + offset
            LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
        }
        Period.CUSTOM -> custom ?: (today.withDayOfMonth(1) to today)
    }

    /**
     * Karşılaştırma için önceki dönem. Ay için "önceki takvim ayı", yıl için "önceki yıl",
     * hafta için "önceki hafta"; özel aralıkta aynı uzunlukta hemen önceki aralık.
     */
    fun previousRange(period: Period, from: LocalDate, to: LocalDate): Pair<LocalDate, LocalDate> =
        when (period) {
            Period.WEEK -> from.minusWeeks(1) to to.minusWeeks(1)
            Period.MONTH -> {
                val ym = YearMonth.from(from).minusMonths(1)
                ym.atDay(1) to ym.atEndOfMonth()
            }
            Period.YEAR -> from.minusYears(1) to to.minusYears(1)
            Period.CUSTOM -> {
                val len = to.toEpochDay() - from.toEpochDay() + 1
                from.minusDays(len) to from.minusDays(1)
            }
        }

    /** Dönemin bugüne kadar geçen gün sayısı (en az 1, en çok dönem uzunluğu). */
    fun elapsedDays(from: LocalDate, to: LocalDate, today: LocalDate): Long {
        val length = (to.toEpochDay() - from.toEpochDay() + 1).coerceAtLeast(1L)
        val end = if (to.isAfter(today)) today else to
        return (end.toEpochDay() - from.toEpochDay() + 1).coerceIn(1L, length)
    }

    /**
     * Önceki dönemin, mevcut dönemle AYNI sayıda gününü kapsayan gider toplamı.
     * Ayın 10'unda bakan biri, geçen ayın ilk 10 günüyle karşılaştırılır (tamamıyla değil).
     */
    fun previousComparable(
        all: List<ExpenseEntity>,
        period: Period,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate
    ): Long {
        val (pFrom, pTo) = previousRange(period, from, to)
        val elapsed = elapsedDays(from, to, today)
        val start = pFrom.toEpochDay()
        val end = minOf(pTo.toEpochDay(), start + elapsed - 1)
        return all.filter { !it.isIncome && it.date in start..end }.sumOf { it.amount }
    }

    /**
     * Dönem sonu gider tahmini: tekrarlayan (sabit) giderler olduğu gibi alınır,
     * değişken harcama geçen güne göre dönem uzunluğuna yayılır, vadesi gelecek
     * tekrarlayan giderler ([upcomingRecurring]) eklenir.
     */
    fun projectExpense(
        expensesInRange: List<ExpenseEntity>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        upcomingRecurring: Long
    ): Long = projectionBreakdown(expensesInRange, from, to, today, upcomingRecurring).total

    /** [projectExpense] ile aynı hesap; kullanıcıya adım adım anlatabilmek için ara değerleri de verir. */
    fun projectionBreakdown(
        expensesInRange: List<ExpenseEntity>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        upcomingRecurring: Long
    ): ProjectionBreakdown {
        val length = (to.toEpochDay() - from.toEpochDay() + 1).coerceAtLeast(1L)
        val elapsed = elapsedDays(from, to, today)
        val fixed = expensesInRange.filter { it.recurringId != null }.sumOf { it.amount }
        val variable = expensesInRange.filter { it.recurringId == null }.sumOf { it.amount }
        val projectedVariable = variable * length / elapsed
        return ProjectionBreakdown(
            fixed = fixed,
            variableSoFar = variable,
            elapsedDays = elapsed,
            lengthDays = length,
            dailyVariableRate = variable / elapsed,
            projectedVariable = projectedVariable,
            upcomingRecurring = upcomingRecurring,
            total = fixed + projectedVariable + upcomingRecurring
        )
    }

    /**
     * Önceki dönemin, mevcut dönemle AYNI sayıda gününü kapsayan kategori bazlı gider toplamları
     * ([previousComparable] ile aynı adil karşılaştırma kuralı).
     */
    fun previousCategoryTotals(
        all: List<ExpenseEntity>,
        period: Period,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate
    ): Map<Long, Long> {
        val (pFrom, pTo) = previousRange(period, from, to)
        val elapsed = elapsedDays(from, to, today)
        val start = pFrom.toEpochDay()
        val end = minOf(pTo.toEpochDay(), start + elapsed - 1)
        return all.asSequence()
            .filter { !it.isIncome && it.date in start..end }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    /** Kategori başına mevcut/önceki dönem ve değişim yüzdesi. */
    fun categoryTrends(current: List<CategoryTotal>, previous: Map<Long, Long>): Map<Long, CategoryTrend> =
        current.mapNotNull { t ->
            val id = t.category?.id ?: return@mapNotNull null
            val prev = previous[id] ?: 0L
            id to CategoryTrend(id, t.amount, prev, changePercent(t.amount, prev))
        }.toMap()

    /** Bugünden sonra ve [to] dahil tarihe kadar vadesi gelecek aktif tekrarlayan giderlerin toplamı. */
    fun upcomingRecurringExpense(items: List<RecurringEntity>, today: LocalDate, to: LocalDate): Long {
        var sum = 0L
        val todayDay = today.toEpochDay()
        val endDay = to.toEpochDay()
        for (r in items) {
            if (!r.active || r.isIncome) continue
            var due = r.nextDueDay
            while (due <= endDay) {
                if (due > todayDay) sum += r.amount
                due = RecurrenceUtil.nextAfter(r.startDay, due, r.frequency)
            }
        }
        return sum
    }

    fun categoryTotals(
        expenses: List<ExpenseEntity>,
        cats: Map<Long, CategoryEntity>
    ): List<CategoryTotal> {
        val total = expenses.sumOf { it.amount }
        if (total == 0L) return emptyList()
        return expenses.groupBy { it.categoryId }
            .map { (id, list) ->
                val amount = list.sumOf { it.amount }
                CategoryTotal(cats[id], amount, amount.toFloat() / total)
            }
            .sortedByDescending { it.amount }
    }

    /** Halka grafik okunaklı kalsın diye küçük kategorileri tek bir "Diğer (n)" diliminde toplar. */
    fun groupSmall(totals: List<CategoryTotal>, maxSlices: Int = 7): List<CategoryTotal> {
        if (totals.size <= maxSlices) return totals
        val head = totals.take(maxSlices - 1)
        val tail = totals.drop(maxSlices - 1)
        val group = CategoryEntity(
            id = GROUP_ID,
            name = "Diğer (${tail.size})",
            iconKey = "more",
            colorArgb = 0xFF9E9E9EL
        )
        val percent = tail.sumOf { it.percent.toDouble() }.toFloat()
        return head + CategoryTotal(group, tail.sumOf { it.amount }, percent)
    }

    /** Son [months] ay için gelir/gider toplamları (eskiden yeniye). Tek geçişte hesaplanır. */
    fun monthly(all: List<ExpenseEntity>, months: Int, today: LocalDate): List<MonthTotal> {
        val current = YearMonth.from(today)
        val first = current.minusMonths((months - 1).toLong())
        val income = HashMap<YearMonth, Long>()
        val expense = HashMap<YearMonth, Long>()
        for (e in all) {
            val ym = YearMonth.from(LocalDate.ofEpochDay(e.date))
            if (ym.isBefore(first) || ym.isAfter(current)) continue
            val target = if (e.isIncome) income else expense
            target[ym] = (target[ym] ?: 0L) + e.amount
        }
        return (months - 1 downTo 0).map { back ->
            val ym = current.minusMonths(back.toLong())
            MonthTotal(ym, income[ym] ?: 0L, expense[ym] ?: 0L)
        }
    }

    /** [months] ay öncesinin ilk günü: SQL toplam sorgularının başlangıç tarihi (epoch day). */
    fun monthsWindowStart(months: Int, today: LocalDate): Long =
        YearMonth.from(today).minusMonths((months - 1).toLong()).atDay(1).toEpochDay()

    /** SQL'den gelen aylık toplam satırlarını grafik verisine çevirir (eksik aylar 0 olur). */
    fun monthlyFromSums(rows: List<MonthSumRow>, months: Int, today: LocalDate): List<MonthTotal> {
        val current = YearMonth.from(today)
        val income = HashMap<String, Long>()
        val expense = HashMap<String, Long>()
        for (r in rows) (if (r.isIncome) income else expense)[r.ym] = r.total
        return (months - 1 downTo 0).map { back ->
            val ym = current.minusMonths(back.toLong())
            val key = ym.toString() // YearMonth.toString() = "2026-11"
            MonthTotal(ym, income[key] ?: 0L, expense[key] ?: 0L)
        }
    }

    /** SQL'den gelen kategori+ay satırlarını, kategori başına [months] uzunluğunda listeye çevirir. */
    fun categoryMonthlyFromSums(rows: List<CategoryMonthRow>, months: Int, today: LocalDate): Map<Long, List<Long>> {
        val current = YearMonth.from(today)
        val index = (months - 1 downTo 0).mapIndexed { i, back -> current.minusMonths(back.toLong()).toString() to i }.toMap()
        val result = HashMap<Long, LongArray>()
        for (r in rows) {
            val i = index[r.ym] ?: continue
            result.getOrPut(r.categoryId) { LongArray(months) }[i] += r.total
        }
        return result.mapValues { it.value.toList() }
    }

    /** Kategori başına son [months] ayın gider toplamları (eskiden yeniye). */
    fun categoryMonthly(all: List<ExpenseEntity>, months: Int, today: LocalDate): Map<Long, List<Long>> {
        val current = YearMonth.from(today)
        val first = current.minusMonths((months - 1).toLong())
        val result = HashMap<Long, LongArray>()
        for (e in all) {
            if (e.isIncome) continue
            val ym = YearMonth.from(LocalDate.ofEpochDay(e.date))
            if (ym.isBefore(first) || ym.isAfter(current)) continue
            val diff = (current.year - ym.year) * 12 + (current.monthValue - ym.monthValue)
            result.getOrPut(e.categoryId) { LongArray(months) }[months - 1 - diff] += e.amount
        }
        return result.mapValues { it.value.toList() }
    }

    /** Son [days] gün için günlük gider toplamları (eskiden yeniye). */
    fun daily(all: List<ExpenseEntity>, days: Int, today: LocalDate): List<DayTotal> {
        val byDay = all.filter { !it.isIncome }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            DayTotal(d, byDay[d.toEpochDay()] ?: 0L)
        }
    }

    /** [from]..[to] aralığındaki günlük gider toplamları; bugünden sonraki günler dahil edilmez. */
    fun dailyRange(all: List<ExpenseEntity>, from: LocalDate, to: LocalDate, today: LocalDate): List<DayTotal> {
        val end = if (to.isAfter(today)) today else to
        if (end.isBefore(from)) return emptyList()
        val byDay = all.filter { !it.isIncome }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val count = end.toEpochDay() - from.toEpochDay() + 1
        return (0 until count).map { k ->
            val d = from.plusDays(k)
            DayTotal(d, byDay[d.toEpochDay()] ?: 0L)
        }
    }

    /**
     * Çizgi grafik için noktalar: 62 güne kadar günlük, 200 güne kadar haftalık,
     * daha uzunsa aylık toplamlar (yıllık görünümde 365 nokta çizilmez).
     */
    fun trendPoints(daily: List<DayTotal>): List<TrendPoint> {
        if (daily.isEmpty()) return emptyList()
        return when {
            daily.size <= 62 -> daily.map { TrendPoint(it.day, it.day, it.expense) }
            daily.size <= 200 -> daily.chunked(7).map { week ->
                TrendPoint(week.first().day, week.last().day, week.sumOf { it.expense })
            }
            else -> daily.groupBy { YearMonth.from(it.day) }.values.map { list ->
                TrendPoint(list.first().day, list.last().day, list.sumOf { it.expense })
            }
        }
    }

    /** Günlük giderlerin kümülatif (birikimli) toplamı. */
    fun cumulative(daily: List<DayTotal>): List<Long> {
        var acc = 0L
        return daily.map {
            acc += it.expense
            acc
        }
    }

    /** Haftanın her günü için ortalama harcama; [weekStart]'tan başlayarak sıralanır. */
    fun weekdayAverages(daily: List<DayTotal>, weekStart: DayOfWeek): List<WeekdayAvg> =
        (0 until 7).map { k ->
            val dow = weekStart.plus(k.toLong())
            val days = daily.filter { it.day.dayOfWeek == dow }
            WeekdayAvg(dow, if (days.isEmpty()) 0L else days.sumOf { it.expense } / days.size)
        }

    fun paymentSplit(expenses: List<ExpenseEntity>): PaymentSplit {
        var cash = 0L
        var debit = 0L
        var credit = 0L
        val labels = HashMap<Pair<String, String>, Long>()
        for (e in expenses) {
            val method = PaymentMethod.normalize(e.paymentMethod)
            when (method) {
                PaymentMethod.DEBIT_CARD -> debit += e.amount
                PaymentMethod.CREDIT_CARD -> credit += e.amount
                else -> cash += e.amount
            }
            val label = e.paymentLabel.trim()
            if (label.isNotEmpty() && method != PaymentMethod.CASH) {
                val k = method to label
                labels[k] = (labels[k] ?: 0L) + e.amount
            }
        }
        val labelList = labels.map { (k, v) -> LabelTotal(k.first, k.second, v) }
            .sortedByDescending { it.amount }
        return PaymentSplit(cash, debit, credit, labelList)
    }
}
