package com.harcamatakip.app.presentation.analytics

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.harcamatakip.app.core.theme.ExpenseRed
import com.harcamatakip.app.core.theme.IncomeGreen
import com.harcamatakip.app.core.util.TR
import com.harcamatakip.app.core.util.fmt
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.data.repository.BudgetRepository
import com.harcamatakip.app.data.repository.CategoryRepository
import com.harcamatakip.app.data.repository.ExpenseRepository
import com.harcamatakip.app.data.repository.RecurringRepository
import com.harcamatakip.app.data.settings.SettingsRepository
import com.harcamatakip.app.domain.model.PaymentMethod
import com.harcamatakip.app.domain.model.Period
import com.harcamatakip.app.domain.model.TxItem
import com.harcamatakip.app.domain.usecase.AnalyticsCalculator
import com.harcamatakip.app.domain.usecase.CategoryTotal
import com.harcamatakip.app.domain.usecase.CategoryTrend
import com.harcamatakip.app.domain.usecase.ProjectionBreakdown
import com.harcamatakip.app.domain.usecase.SafeDailyLimit
import com.harcamatakip.app.domain.usecase.SafeSpending
import com.harcamatakip.app.domain.usecase.DayTotal
import com.harcamatakip.app.domain.usecase.Insights
import com.harcamatakip.app.domain.usecase.MonthTotal
import com.harcamatakip.app.domain.usecase.PaymentSplit
import com.harcamatakip.app.domain.usecase.TrendPoint
import com.harcamatakip.app.domain.usecase.WeekdayAvg
import com.harcamatakip.app.presentation.components.BarChart
import com.harcamatakip.app.presentation.components.CategoryAvatar
import com.harcamatakip.app.presentation.components.ComparisonBars
import com.harcamatakip.app.presentation.components.ComparisonRow
import com.harcamatakip.app.presentation.components.SafeSpendingCard
import com.harcamatakip.app.presentation.components.TrendBadge
import com.harcamatakip.app.presentation.components.CumulativeChart
import com.harcamatakip.app.presentation.components.DateRangePickerDialogM3
import com.harcamatakip.app.presentation.components.DonutChart
import com.harcamatakip.app.presentation.components.DonutSlice
import com.harcamatakip.app.presentation.components.EmptyState
import com.harcamatakip.app.presentation.components.SimpleBarChart
import com.harcamatakip.app.presentation.components.TransactionRow
import com.harcamatakip.app.presentation.components.TrendChart
import dagger.hilt.android.lifecycle.HiltViewModel
import com.harcamatakip.app.data.local.ExpenseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import javax.inject.Inject
import kotlin.math.abs

data class PeriodSelection(
    val period: Period = Period.MONTH,
    val offset: Int = 0,
    val custom: Pair<LocalDate, LocalDate>? = null,
    val monthsBack: Int = 6
)

data class AnalyticsState(
    val symbol: String = "₺",
    val selection: PeriodSelection = PeriodSelection(),
    val rangeLabel: String = "",
    val rangeFrom: LocalDate = LocalDate.now(),
    val rangeTo: LocalDate = LocalDate.now(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val canGoForward: Boolean = false,
    val income: Long = 0,
    val expense: Long = 0,
    val hasData: Boolean = false,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val incomeTotals: List<CategoryTotal> = emptyList(),
    val monthly: List<MonthTotal> = emptyList(),
    val daily: List<DayTotal> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val previousTrend: List<Long>? = null,
    val cumulative: List<Long> = emptyList(),
    val budget: Long? = null,
    val projected: Long? = null,
    /** Tahminin nasıl hesaplandığını anlatan ara değerler (tahmin yoksa null). */
    val projection: ProjectionBreakdown? = null,
    /** Yalnızca içinde bulunulan ay seçiliyken ve genel bütçe varken dolu. */
    val safeLimit: SafeDailyLimit? = null,
    /** Kategori kimliği -> önceki döneme göre değişim (gider). */
    val categoryTrends: Map<Long, CategoryTrend> = emptyMap(),
    val weekdays: List<WeekdayAvg> = emptyList(),
    val payment: PaymentSplit? = null,
    val categoryMonthly: Map<Long, List<Long>> = emptyMap(),
    val topTransactions: List<TxItem> = emptyList(),
    val insights: Insights? = null
)

/** Seçili dönemin işlemleri; seçimle birlikte tek pakette gelir ki ekran tutarsız (yarım) veri göstermesin. */
private data class Window(val sel: PeriodSelection, val weekStart: DayOfWeek, val all: List<ExpenseEntity>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    expenses: ExpenseRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    recurring: RecurringRepository,
    settings: SettingsRepository
) : ViewModel() {

    private val selection = MutableStateFlow(PeriodSelection())
    private val extras = combine(budgets.observeAll(), recurring.observeAll()) { b, r -> b to r }

    /**
     * Tüm tabloyu okumak yerine yalnızca seçili dönem + karşılaştırılan önceki dönem
     * aralığı Room'dan istenir (date indeksli sorgu). Seçim değişince sorgu yenilenir.
     */
    private val windowed = combine(selection, settings.settings) { sel, st -> sel to st.weekStart }
        .distinctUntilChanged()
        .flatMapLatest { (sel, weekStart) ->
            val today = LocalDate.now()
            val (from, to) = AnalyticsCalculator.rangeFor(sel.period, sel.offset, weekStart, sel.custom, today)
            val (pFrom, _) = AnalyticsCalculator.previousRange(sel.period, from, to)
            val start = if (pFrom.isBefore(from)) pFrom else from
            expenses.observeBetween(start.toEpochDay(), to.toEpochDay())
                .map { list -> Window(sel, weekStart, list) }
        }

    /** Aylık grafik ve kategori trendi: gruplama SQLite'ta yapılır, sadece toplam satırlar gelir. */
    private val monthSums = selection.map { it.monthsBack }
        .distinctUntilChanged()
        .flatMapLatest { n ->
            expenses.observeMonthSums(AnalyticsCalculator.monthsWindowStart(n, LocalDate.now()))
        }
    private val categorySums =
        expenses.observeCategoryMonthSums(AnalyticsCalculator.monthsWindowStart(6, LocalDate.now()))
    private val aggregates = combine(monthSums, categorySums) { m, c -> m to c }

    val state: StateFlow<AnalyticsState> = combine(
        windowed, categories.observeAll(), extras, aggregates, settings.settings
    ) { w, cats, (budgetList, recurringList), (monthRows, catRows), st ->
        val sel = w.sel
        val all = w.all
        val today = LocalDate.now()
        val catMap = cats.associateBy { it.id }
        val (from, to) = AnalyticsCalculator.rangeFor(sel.period, sel.offset, w.weekStart, sel.custom, today)
        val fromDay = from.toEpochDay()
        val toDay = to.toEpochDay()
        val inRange = all.filter { it.date in fromDay..toDay }
        val expensesInRange = inRange.filter { !it.isIncome }
        val incomeInRange = inRange.filter { it.isIncome }
        val expenseTotal = expensesInRange.sumOf { it.amount }

        val lengthDays = (toDay - fromDay + 1).coerceAtLeast(1L)
        val elapsedDays = AnalyticsCalculator.elapsedDays(from, to, today)
        val isCurrent = !today.isBefore(from) && !today.isAfter(to)
        val partial = isCurrent && sel.period != Period.CUSTOM

        // Önceki dönemle ADİL karşılaştırma: aynı sayıda gün (ayın 10'unda ilk 10 güne karşı)
        val prevExpense = AnalyticsCalculator.previousComparable(all, sel.period, from, to, today)

        val totals = AnalyticsCalculator.categoryTotals(expensesInRange, catMap)
        val incomeTotals = AnalyticsCalculator.categoryTotals(incomeInRange, catMap)

        val projection = if (partial && expenseTotal > 0) {
            val upcoming = AnalyticsCalculator.upcomingRecurringExpense(recurringList, today, to)
            AnalyticsCalculator.projectionBreakdown(expensesInRange, from, to, today, upcoming)
        } else null
        val projected = projection?.total

        val prevCategoryTotals = AnalyticsCalculator.previousCategoryTotals(all, sel.period, from, to, today)
        val categoryTrends = AnalyticsCalculator.categoryTrends(totals, prevCategoryTotals)

        val biggest = expensesInRange.maxByOrNull { it.amount }?.let { TxItem(it, catMap[it.categoryId]) }

        val insights = Insights(
            changePct = if (prevExpense > 0) (expenseTotal - prevExpense).toDouble() / prevExpense * 100 else null,
            previousLabel = when (sel.period) {
                Period.WEEK -> if (partial) "geçen haftanın aynı gününe" else "geçen haftaya"
                Period.MONTH -> if (partial) "geçen ayın aynı gününe" else "geçen aya"
                Period.YEAR -> if (partial) "geçen yılın aynı gününe" else "geçen yıla"
                Period.CUSTOM -> "önceki döneme"
            },
            topCategory = totals.firstOrNull(),
            avgDaily = if (expenseTotal > 0) expenseTotal / elapsedDays else 0,
            projected = projected,
            biggest = biggest
        )

        val daily = AnalyticsCalculator.dailyRange(all, from, to, today)
        val trend = AnalyticsCalculator.trendPoints(daily)

        // Önceki dönem çizgisi yalnızca günlük görünümde (aynı gün sırasına hizalanır)
        val previousTrend = if (trend.isNotEmpty() && trend.all { it.start == it.end }) {
            val (pFrom, pTo) = AnalyticsCalculator.previousRange(sel.period, from, to)
            AnalyticsCalculator.dailyRange(all, pFrom, pTo, today).map { it.expense }
                .takeIf { list -> list.any { it > 0 } }
        } else null

        val generalBudget = budgetList.firstOrNull { it.categoryId == 0L }?.limitAmount?.takeIf { it > 0 }
        val safeLimit = if (sel.period == Period.MONTH && isCurrent && generalBudget != null)
            SafeSpending.compute(generalBudget, expenseTotal, today, to) else null

        AnalyticsState(
            symbol = st.currency.symbol,
            selection = sel,
            rangeLabel = rangeLabel(sel.period, from, to),
            rangeFrom = from,
            rangeTo = to,
            weekStart = w.weekStart,
            canGoForward = sel.period != Period.CUSTOM && sel.offset < 0,
            income = incomeInRange.sumOf { it.amount },
            expense = expenseTotal,
            hasData = inRange.isNotEmpty(),
            categoryTotals = totals,
            incomeTotals = incomeTotals,
            monthly = AnalyticsCalculator.monthlyFromSums(monthRows, sel.monthsBack, today),
            daily = daily,
            trend = trend,
            previousTrend = previousTrend,
            cumulative = AnalyticsCalculator.cumulative(daily),
            budget = if (sel.period == Period.MONTH) generalBudget else null,
            projected = projected,
            projection = projection,
            safeLimit = safeLimit,
            categoryTrends = categoryTrends,
            weekdays = if (daily.size >= 7) AnalyticsCalculator.weekdayAverages(daily, w.weekStart) else emptyList(),
            payment = if (expensesInRange.isNotEmpty()) AnalyticsCalculator.paymentSplit(expensesInRange) else null,
            categoryMonthly = AnalyticsCalculator.categoryMonthlyFromSums(catRows, 6, today),
            topTransactions = expensesInRange.sortedByDescending { it.amount }.take(300)
                .map { TxItem(it, catMap[it.categoryId]) },
            insights = insights
        )
    }
        .flowOn(Dispatchers.Default) // ağır hesaplamalar ana iş parçacığını bloklamasın
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsState())

    fun setPeriod(p: Period) = selection.update { it.copy(period = p, offset = 0) }
    fun shift(delta: Int) = selection.update { it.copy(offset = (it.offset + delta).coerceAtMost(0)) }
    fun setCustom(from: LocalDate, to: LocalDate) =
        selection.update { it.copy(period = Period.CUSTOM, custom = from to to, offset = 0) }
    fun setMonthsBack(n: Int) = selection.update { it.copy(monthsBack = n) }

    private fun rangeLabel(p: Period, from: LocalDate, to: LocalDate): String = when (p) {
        Period.WEEK -> "${from.fmt("d MMM")} – ${to.fmt("d MMM yyyy")}"
        Period.MONTH -> YearMonth.from(from).atDay(1).fmt("MMMM yyyy")
        Period.YEAR -> from.fmt("yyyy")
        Period.CUSTOM -> "${from.fmt("d MMM yyyy")} – ${to.fmt("d MMM yyyy")}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(onOpenTransaction: (Long) -> Unit, vm: AnalyticsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showRange by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<CategoryTotal?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Analiz", fontWeight = FontWeight.Bold) }) }) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PeriodSelector(
                    s = s,
                    onPeriod = { p -> if (p == Period.CUSTOM) showRange = true else vm.setPeriod(p) },
                    onShift = vm::shift
                )
            }

            if (!s.hasData) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.PieChart,
                        title = "Bu dönemde veri yok",
                        subtitle = "Başka bir dönem seç veya yeni işlem ekle."
                    )
                }
            } else {
                item { SummaryRow(s) }
                s.safeLimit?.let { limit -> item { SafeSpendingCard(limit, s.symbol) } }
                s.insights?.let { ins -> item { InsightsSection(ins, s) } }
                if (s.selection.period == Period.MONTH && s.daily.size >= 2) {
                    item { CumulativeCard(s) }
                }
                item { TrendCard(s) }
                item { CategoryDonutCard(s, onDetail = { detail = it }) }
                if (s.categoryTrends.values.any { it.previous > 0 }) item { CategoryComparisonCard(s) }
                if (s.weekdays.isNotEmpty()) item { WeekdayCard(s) }
                if (s.selection.period == Period.MONTH) item { HeatmapCard(s) }
                s.payment?.let { p -> item { PaymentCard(p, s.symbol) } }
            }

            // Aylık grafik seçili dönemden bağımsızdır (son 6 / 12 ay)
            item { MonthlyCard(s, onMonths = vm::setMonthsBack) }
        }
    }

    if (showRange) {
        DateRangePickerDialogM3(
            initial = s.selection.custom,
            onDismiss = { showRange = false },
            onConfirm = { a, b ->
                vm.setCustom(a, b)
                showRange = false
            }
        )
    }

    detail?.let { t ->
        CategoryDetailSheet(
            t, s,
            onDismiss = { detail = null },
            onOpen = { id ->
                detail = null
                onOpenTransaction(id)
            }
        )
    }
}

@Composable
private fun PeriodSelector(s: AnalyticsState, onPeriod: (Period) -> Unit, onShift: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { p ->
                FilterChip(
                    selected = s.selection.period == p,
                    onClick = { onPeriod(p) },
                    label = { Text(p.label) }
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onShift(-1) },
                enabled = s.selection.period != Period.CUSTOM
            ) { Icon(Icons.Rounded.ChevronLeft, "Önceki") }
            Text(
                s.rangeLabel,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { onShift(1) }, enabled = s.canGoForward) {
                Icon(Icons.Rounded.ChevronRight, "Sonraki")
            }
        }
    }
}

@Composable
private fun SummaryRow(s: AnalyticsState) {
    val net = s.income - s.expense
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(Modifier.weight(1f), "Gelir", s.income.formatMoney(s.symbol), IncomeGreen)
            SummaryCard(Modifier.weight(1f), "Gider", s.expense.formatMoney(s.symbol), ExpenseRed)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                Modifier.weight(1f), "Net",
                net.formatMoney(s.symbol),
                if (net >= 0) IncomeGreen else ExpenseRed
            )
            val rate = if (s.income > 0) "%${(net * 100.0 / s.income).toInt()}" else "—"
            SummaryCard(
                Modifier.weight(1f), "Tasarruf oranı", rate,
                if (s.income > 0 && net < 0) ExpenseRed else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun InsightsSection(ins: Insights, s: AnalyticsState) {
    val sym = s.symbol
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("İçgörüler", style = MaterialTheme.typography.titleMedium)

        val pct = ins.changePct
        if (pct != null) {
            val more = pct >= 0
            InsightCard(
                title = "${ins.previousLabel} göre %${"%.0f".format(abs(pct))} ${if (more) "fazla" else "az"} harcadın",
                subtitle = if (more) "Giderlerin arttı" else "Giderlerin azaldı",
                accent = if (more) ExpenseRed else IncomeGreen
            )
        }
        ins.topCategory?.let { top ->
            InsightCard(
                title = "En çok harcanan: ${top.category?.name ?: "Kategorisiz"}",
                subtitle = "${top.amount.formatMoney(sym)} · %${"%.0f".format(top.percent * 100)}",
                accent = top.category?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary
            )
        }
        InsightCard(
            title = "Ortalama günlük harcama",
            subtitle = ins.avgDaily.formatMoney(sym),
            accent = MaterialTheme.colorScheme.primary
        )
        ins.projected?.let {
            ProjectionCard(it, s.projection, sym)
        }
        ins.biggest?.let { b ->
            InsightCard(
                title = "En pahalı işlem",
                subtitle = "${b.expense.amount.formatMoney(sym)} · ${b.category?.name ?: "Kategorisiz"}" +
                    if (b.expense.note.isNotBlank()) " · ${b.expense.note}" else "",
                accent = ExpenseRed
            )
        }
    }
}

/** "Dönem sonu tahmini toplam" kartı; dokununca hesabın nasıl yapıldığını adım adım gösterir. */
@Composable
private fun ProjectionCard(total: Long, b: ProjectionBreakdown?, sym: String) {
    var open by remember { mutableStateOf(false) }
    Card(
        onClick = { open = !open },
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dönem sonu tahmini toplam", style = MaterialTheme.typography.titleMedium)
                    Text(
                        total.formatMoney(sym),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Rounded.Info, if (open) "Açıklamayı gizle" else "Nasıl hesaplanır?")
            }
            if (!open) {
                Text(
                    "Nasıl hesaplanır? Dokun.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (b != null) {
                Text(
                    "Tahmin üç parçadan oluşur:",
                    style = MaterialTheme.typography.bodyMedium
                )
                ExplainLine("1. Sabit giderler (tekrarlayan işlemlerden eklenenler) olduğu gibi alınır", b.fixed.formatMoney(sym))
                ExplainLine(
                    "2. Değişken harcama: ${b.elapsedDays} günde ${b.variableSoFar.formatMoney(sym)} harcandı " +
                        "(günde ort. ${b.dailyVariableRate.formatMoney(sym)}). Kalan ${b.remainingDays} gün de aynı hızla " +
                        "sürerse ${b.lengthDays} günlük dönem için",
                    b.projectedVariable.formatMoney(sym)
                )
                ExplainLine("3. Dönem bitmeden vadesi gelecek tekrarlayan giderler eklenir", b.upcomingRecurring.formatMoney(sym))
                Text(
                    "Toplam = 1 + 2 + 3. Bu bir tahmindir; harcama hızın değişirse sonuç da değişir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExplainLine(text: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun InsightCard(title: String, subtitle: String, accent: Color) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CumulativeCard(s: AnalyticsState) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Birikimli harcama", style = MaterialTheme.typography.titleMedium)
            Text(
                if (s.budget != null) "Kırmızı kesikli çizgi aylık bütçeni gösterir."
                else "Ay içinde harcamanın nasıl biriktiğini gösterir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CumulativeChart(
                actual = s.cumulative,
                totalDays = (s.rangeTo.toEpochDay() - s.rangeFrom.toEpochDay() + 1).toInt(),
                start = s.rangeFrom,
                budget = s.budget,
                projected = s.projected,
                symbol = s.symbol,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TrendCard(s: AnalyticsState) {
    val title = when {
        s.trend.isEmpty() || s.trend.all { it.start == it.end } -> "Günlük harcama"
        s.trend.all { java.time.temporal.ChronoUnit.DAYS.between(it.start, it.end) <= 6 } -> "Haftalık harcama"
        else -> "Aylık harcama"
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                s.rangeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (s.trend.size < 2) {
                Text("Grafik için en az 2 günlük veri gerekli.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TrendChart(s.trend, s.previousTrend, s.symbol, MaterialTheme.colorScheme.primary)
                if (s.previousTrend != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot(MaterialTheme.colorScheme.primary, "Bu dönem")
                        LegendDot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), "Önceki dönem (kesikli)")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDonutCard(s: AnalyticsState, onDetail: (CategoryTotal) -> Unit) {
    var showIncome by remember { mutableStateOf(false) }
    val totals = if (showIncome) s.incomeTotals else s.categoryTotals
    val grandTotal = if (showIncome) s.income else s.expense
    var selected by remember(totals) { mutableStateOf<Int?>(null) }
    val grouped = remember(totals) { AnalyticsCalculator.groupSmall(totals) }
    val merged = grouped.size != totals.size

    // Listedeki satır, halkada hangi dilime denk geliyor ("Diğer" dilimi birden çok satırı kapsar)
    fun sliceIndex(i: Int) = if (merged && i >= grouped.size - 1) grouped.size - 1 else i

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showIncome) "Kategorilere göre gelir" else "Kategorilere göre gider",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(selected = !showIncome, onClick = { showIncome = false }, label = { Text("Gider") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = showIncome, onClick = { showIncome = true }, label = { Text("Gelir") })
            }
            if (totals.isEmpty()) {
                Text(
                    if (showIncome) "Bu dönemde gelir yok." else "Bu dönemde gider yok.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            val fallback = MaterialTheme.colorScheme.outline
            val slices = grouped.map { DonutSlice(it.amount.toFloat(), it.category?.let { c -> Color(c.colorArgb) } ?: fallback) }
            val description = "Kategori dağılımı. " + grouped.joinToString(". ") {
                "${it.category?.name ?: "Kategorisiz"}: ${it.amount.formatMoney(s.symbol)}, yüzde ${"%.0f".format(it.percent * 100)}"
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DonutChart(
                    slices = slices,
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.width(220.dp),
                    description = description
                ) {
                    val sel = selected?.let { grouped.getOrNull(it) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            sel?.category?.name ?: "Toplam",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            (sel?.amount ?: grandTotal).formatMoney(s.symbol),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (sel != null) {
                            Text("%${"%.0f".format(sel.percent * 100)}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (!showIncome) {
                Text(
                    "Ayrıntı ve aylık trend için bir kategoriye dokun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            totals.forEachIndexed { i, t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            selected = sliceIndex(i)
                            if (!showIncome) onDetail(t)
                        }
                        .background(
                            if (selected == sliceIndex(i)) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryAvatar(t.category, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.category?.name ?: "Kategorisiz", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "%${"%.1f".format(t.percent * 100)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(t.percent.coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .background(t.category?.let { c -> Color(c.colorArgb) } ?: MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t.amount.formatMoney(s.symbol), style = MaterialTheme.typography.titleMedium)
                        if (!showIncome) TrendBadge(t.category?.id?.let { id -> s.categoryTrends[id] })
                    }
                }
            }
        }
    }
}

/** Kategori bazlı "bu dönem / önceki dönem" karşılaştırma çubukları (ilk 6 kategori). */
@Composable
private fun CategoryComparisonCard(s: AnalyticsState) {
    val rows = s.categoryTotals.take(6).mapNotNull { t ->
        val cat = t.category ?: return@mapNotNull null
        val tr = s.categoryTrends[cat.id] ?: return@mapNotNull null
        ComparisonRow(
            name = cat.name,
            color = Color(cat.colorArgb),
            current = tr.current,
            previous = tr.previous,
            trend = tr
        )
    }
    if (rows.isEmpty()) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Önceki döneme göre kategoriler", style = MaterialTheme.typography.titleMedium)
            Text(
                "Dolu çubuk bu dönem, soluk çubuk önceki dönem" +
                    (s.insights?.previousLabel?.let { " ($it göre)" } ?: "") + ". Kırmızı artış, yeşil azalış.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ComparisonBars(rows, s.symbol)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDetailSheet(
    t: CategoryTotal,
    s: AnalyticsState,
    onDismiss: () -> Unit,
    onOpen: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val catId = t.category?.id
    val trend = catId?.let { s.categoryTrends[it] }
    val monthly = catId?.let { s.categoryMonthly[it] } ?: List(6) { 0L }
    val labels = remember {
        (5 downTo 0).map { YearMonth.now().minusMonths(it.toLong()).atDay(1).fmt("MMM") }
    }
    val txs = remember(s.topTransactions, catId) {
        s.topTransactions.filter { it.expense.categoryId == catId }.take(5)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(t.category, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.category?.name ?: "Kategorisiz", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${s.rangeLabel} · %${"%.0f".format(t.percent * 100)} pay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        t.amount.formatMoney(s.symbol),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TrendBadge(trend)
                }
            }
            if (trend != null) {
                Text(
                    "Önceki dönemde ${trend.previous.formatMoney(s.symbol)} harcamıştın" +
                        (trend.changePct?.let { " · fark: ${(trend.current - trend.previous).formatMoney(s.symbol)}" } ?: " · bu kategoride önceki dönem harcama yoktu"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Text(
                "Son 6 ay",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            SimpleBarChart(
                values = monthly,
                labels = labels,
                symbol = s.symbol,
                color = t.category?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (txs.isNotEmpty()) {
                Text(
                    "Bu dönemin en büyük işlemleri · düzenlemek için dokun",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                txs.forEach { tx -> TransactionRow(tx, s.symbol, onClick = { onOpen(tx.expense.id) }) }
            }
        }
    }
}

@Composable
private fun WeekdayCard(s: AnalyticsState) {
    val top = s.weekdays.maxByOrNull { it.average }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Haftanın günlerine göre ortalama", style = MaterialTheme.typography.titleMedium)
            SimpleBarChart(
                values = s.weekdays.map { it.average },
                labels = s.weekdays.map { it.day.getDisplayName(TextStyle.SHORT, TR) },
                symbol = s.symbol,
                color = MaterialTheme.colorScheme.primary,
                header = if (top != null && top.average > 0)
                    "En çok harcadığın gün: ${top.day.getDisplayName(TextStyle.FULL, TR)} (ort. ${top.average.formatMoney(s.symbol)})"
                else "Detay için bir çubuğa dokun"
            )
        }
    }
}

@Composable
private fun HeatmapCard(s: AnalyticsState) {
    val byDay = remember(s.daily) { s.daily.associate { it.day to it.expense } }
    val maxV = (s.daily.maxOfOrNull { it.expense } ?: 0L).coerceAtLeast(1L)
    val today = LocalDate.now()
    var selected by remember(s.daily) { mutableStateOf<LocalDate?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Harcama takvimi", style = MaterialTheme.typography.titleMedium)
            val sel = selected
            Text(
                if (sel != null) "${sel.fmt("d MMMM, EEEE")}: ${(byDay[sel] ?: 0L).formatMoney(s.symbol)}"
                else "Koyu renk = yüksek harcama. Detay için bir güne dokun.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2
            )
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { k ->
                    Text(
                        s.weekStart.plus(k.toLong()).getDisplayName(TextStyle.SHORT, TR),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            val lead = (s.rangeFrom.dayOfWeek.value - s.weekStart.value + 7) % 7
            val cells = buildList<LocalDate?> {
                repeat(lead) { add(null) }
                var d = s.rangeFrom
                while (!d.isAfter(s.rangeTo)) {
                    add(d)
                    d = d.plusDays(1)
                }
                while (size % 7 != 0) add(null)
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val future = day.isAfter(today)
                            val ratio = if (future) 0f else (byDay[day] ?: 0L).toFloat() / maxV
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (future) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                                        else primary.copy(alpha = 0.10f + 0.80f * ratio)
                                    )
                                    .clickable(enabled = !future) { selected = if (selected == day) null else day },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${day.dayOfMonth}",
                                    fontSize = 11.sp,
                                    color = if (ratio > 0.55f) onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(p: PaymentSplit, symbol: String) {
    val total = p.total.coerceAtLeast(1L)
    val colors = mapOf(
        PaymentMethod.CASH to MaterialTheme.colorScheme.primary,
        PaymentMethod.DEBIT_CARD to MaterialTheme.colorScheme.tertiary,
        PaymentMethod.CREDIT_CARD to MaterialTheme.colorScheme.secondary
    )
    // Halkada yalnızca tutarı olan yöntemler; açıklama listesinde üç yöntem de görünür
    val active = PaymentMethod.all.filter { p.amountOf(it) > 0 }
    var selected by remember(p) { mutableStateOf<Int?>(null) }
    val slices = active.map { DonutSlice(p.amountOf(it).toFloat(), colors.getValue(it)) }
    val description = "Ödeme yöntemi dağılımı. " + PaymentMethod.all.joinToString(". ") {
        "${PaymentMethod.label(it)}: ${p.amountOf(it).formatMoney(symbol)}, yüzde ${p.amountOf(it) * 100 / total}"
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ödeme yöntemi", style = MaterialTheme.typography.titleMedium)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DonutChart(
                    slices = slices,
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.width(180.dp),
                    description = description
                ) {
                    val sel = selected?.let { active.getOrNull(it) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            sel?.let { PaymentMethod.label(it) } ?: "Toplam",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            (sel?.let { p.amountOf(it) } ?: p.total).formatMoney(symbol),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            PaymentMethod.all.forEach { m ->
                val amount = p.amountOf(m)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors.getValue(m)))
                    Spacer(Modifier.width(8.dp))
                    Text(PaymentMethod.label(m), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${amount.formatMoney(symbol)} · %${amount * 100 / total}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            if (p.labels.isNotEmpty()) {
                Text(
                    "Kart / hesap bazında",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                p.labels.take(5).forEach { l ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colors.getValue(l.method)))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${l.label} (${PaymentMethod.label(l.method)})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(l.amount.formatMoney(symbol), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyCard(s: AnalyticsState, onMonths: (Int) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aylık gelir / gider", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilterChip(selected = s.selection.monthsBack == 6, onClick = { onMonths(6) }, label = { Text("6 ay") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = s.selection.monthsBack == 12, onClick = { onMonths(12) }, label = { Text("12 ay") })
            }
            BarChart(s.monthly, s.symbol, IncomeGreen, ExpenseRed)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(IncomeGreen, "Gelir (düz)")
                LegendDot(ExpenseRed, "Gider (çizgili)")
                LegendDot(MaterialTheme.colorScheme.onSurface, "Net (çizgi · ○ eksi)")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
