package com.harcamatakip.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.data.repository.BudgetRepository
import com.harcamatakip.app.data.repository.CategoryRepository
import com.harcamatakip.app.data.repository.ExpenseRepository
import com.harcamatakip.app.data.settings.SettingsRepository
import com.harcamatakip.app.domain.model.TxItem
import com.harcamatakip.app.presentation.components.BudgetGauge
import com.harcamatakip.app.presentation.components.SimpleBarChart
import com.harcamatakip.app.core.util.fmt
import com.harcamatakip.app.domain.usecase.AnalyticsCalculator
import com.harcamatakip.app.domain.usecase.DayTotal
import java.time.LocalDate
import com.harcamatakip.app.presentation.components.EmptyState
import com.harcamatakip.app.presentation.components.SectionHeader
import com.harcamatakip.app.presentation.components.ShimmerBox
import com.harcamatakip.app.presentation.components.TransactionRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val symbol: String = "₺",
    val balance: Long = 0,
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val recent: List<TxItem> = emptyList(),
    val generalBudget: Long? = null,
    val last7: List<DayTotal> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    expenses: ExpenseRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    settings: SettingsRepository
) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        expenses.observeAll(),
        categories.observeAll(),
        budgets.observeAll(),
        settings.settings
    ) { all, cats, budgetList, st ->
        val catMap = cats.associateBy { it.id }
        val ym = YearMonth.now()
        val from = ym.atDay(1).toEpochDay()
        val to = ym.atEndOfMonth().toEpochDay()
        val month = all.filter { it.date in from..to }
        HomeState(
            loading = false,
            symbol = st.currency.symbol,
            balance = all.sumOf { if (it.isIncome) it.amount else -it.amount },
            monthIncome = month.filter { it.isIncome }.sumOf { it.amount },
            monthExpense = month.filter { !it.isIncome }.sumOf { it.amount },
            recent = all.take(5).map { TxItem(it, catMap[it.categoryId]) },
            generalBudget = budgetList.firstOrNull { it.categoryId == 0L }?.limitAmount,
            last7 = AnalyticsCalculator.daily(all, 7, LocalDate.now())
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onBudget: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Genel Bakış", fontWeight = FontWeight.Bold) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("İşlem ekle") }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (s.loading) {
                item { ShimmerBox(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(170.dp)) }
                item { ShimmerBox(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(100.dp)) }
                item { ShimmerBox(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(200.dp)) }
            } else {
                item { BalanceCard(s) }
                item { BudgetCard(s, onBudget) }
                if (s.last7.any { it.expense > 0 }) {
                    item { WeekCard(s) }
                }
                item { SectionHeader("Son işlemler", actionLabel = "Tümü", onAction = onSeeAll) }
                if (s.recent.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.ReceiptLong,
                            title = "Henüz işlem yok",
                            subtitle = "Sağ alttaki butonla ilk gelir veya giderini ekle."
                        )
                    }
                } else {
                    items(s.recent, key = { it.expense.id }) { item ->
                        TransactionRow(item, s.symbol, onClick = { onOpen(item.expense.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(s: HomeState) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.padding(20.dp)) {
            val on = MaterialTheme.colorScheme.onPrimary
            Text("Toplam bakiye", color = on.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            Text(
                s.balance.formatMoney(s.symbol),
                color = on,
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(Modifier.height(16.dp))
            Text("Bu ay", color = on.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatPill(Modifier.weight(1f), "Gelir", s.monthIncome.formatMoney(s.symbol), true, on)
                StatPill(Modifier.weight(1f), "Gider", s.monthExpense.formatMoney(s.symbol), false, on)
            }
        }
    }
}

@Composable
private fun StatPill(modifier: Modifier, label: String, value: String, income: Boolean, on: Color) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(on.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (income) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                null, Modifier.size(18.dp), tint = on
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = on.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            Text(value, color = on, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun BudgetCard(s: HomeState, onClick: () -> Unit) {
    val limit = s.generalBudget
    Card(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (limit != null && limit > 0) {
                val ratio = s.monthExpense.toFloat() / limit
                BudgetGauge(
                    progress = ratio,
                    size = 76.dp,
                    strokeWidth = 8.dp,
                    centerTop = "%${(ratio * 100).toInt()}"
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Aylık bütçe", style = MaterialTheme.typography.titleMedium)
                    val remaining = limit - s.monthExpense
                    Text(
                        if (remaining >= 0) "Kalan: ${remaining.formatMoney(s.symbol)}"
                        else "Aşıldı: ${(-remaining).formatMoney(s.symbol)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Limit: ${limit.formatMoney(s.symbol)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("Aylık bütçe belirle", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Limit koy, %80 ve %100'de uyarı al.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

/** Son 7 günün harcamasını gösteren küçük çubuk grafik. */
@Composable
private fun WeekCard(s: HomeState) {
    val total = s.last7.sumOf { it.expense }
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Son 7 gün", style = MaterialTheme.typography.titleMedium)
            SimpleBarChart(
                values = s.last7.map { it.expense },
                labels = s.last7.map { it.day.fmt("EEE") },
                symbol = s.symbol,
                color = MaterialTheme.colorScheme.primary,
                height = 100.dp,
                header = "Toplam: ${total.formatMoney(s.symbol)} · günlük ort. ${(total / 7).formatMoney(s.symbol)}"
            )
        }
    }
}
