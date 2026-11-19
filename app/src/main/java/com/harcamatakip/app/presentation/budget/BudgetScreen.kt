package com.harcamatakip.app.presentation.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.core.util.formatPlain
import com.harcamatakip.app.core.util.parseAmountToKurus
import com.harcamatakip.app.core.util.sanitizeAmountInput
import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.data.repository.BudgetAlertManager
import com.harcamatakip.app.data.repository.BudgetRepository
import com.harcamatakip.app.data.repository.CategoryRepository
import com.harcamatakip.app.data.repository.ExpenseRepository
import com.harcamatakip.app.data.settings.SettingsRepository
import com.harcamatakip.app.presentation.components.BudgetGauge
import com.harcamatakip.app.presentation.components.CategoryAvatar
import com.harcamatakip.app.domain.usecase.SafeDailyLimit
import com.harcamatakip.app.domain.usecase.SafeSpending
import com.harcamatakip.app.presentation.components.NotificationPermissionCard
import com.harcamatakip.app.presentation.components.SafeSpendingCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Bütçe satırı: categoryId = 0 genel bütçe. */
data class BudgetRow(
    val categoryId: Long,
    val title: String,
    val category: CategoryEntity?,
    val limit: Long?,
    val spent: Long,
    /** Şimdiki harcama hızıyla ay sonu tahmini (ayın ilk günlerinde null). */
    val projected: Long? = null
)

data class BudgetState(
    val symbol: String = "₺",
    val general: BudgetRow = BudgetRow(0, "Genel aylık bütçe", null, null, 0),
    val rows: List<BudgetRow> = emptyList(),
    val daysLeft: Int = 1,
    /** "Ay sonuna kadar günde ortalama X" özeti; genel bütçe yoksa null. */
    val safeLimit: SafeDailyLimit? = null
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    expenses: ExpenseRepository,
    categories: CategoryRepository,
    private val budgets: BudgetRepository,
    settings: SettingsRepository,
    private val alerts: BudgetAlertManager
) : ViewModel() {

    val state: StateFlow<BudgetState> = combine(
        expenses.observeAll(), categories.observeAll(), budgets.observeAll(), settings.settings
    ) { all, cats, budgetList, st ->
        val today = LocalDate.now()
        val ym = YearMonth.from(today)
        val from = ym.atDay(1).toEpochDay()
        val to = ym.atEndOfMonth().toEpochDay()
        val month = all.filter { !it.isIncome && it.date in from..to }
        val limits = budgetList.associate { it.categoryId to it.limitAmount }
        val elapsed = today.dayOfMonth
        val monthDays = ym.lengthOfMonth()
        fun proj(spent: Long): Long? = if (elapsed < 5) null else spent * monthDays / elapsed

        BudgetState(
            symbol = st.currency.symbol,
            general = month.sumOf { it.amount }.let { total ->
                BudgetRow(0, "Genel aylık bütçe", null, limits[0L], total, proj(total))
            },
            rows = cats.filter { !it.isIncome }.map { c ->
                val spent = month.filter { it.categoryId == c.id }.sumOf { it.amount }
                BudgetRow(c.id, c.name, c, limits[c.id], spent, proj(spent))
            },
            // Bugün dahil ay sonuna kalan gün sayısı
            daysLeft = SafeSpending.daysLeftInclusive(today, ym.atEndOfMonth()),
            safeLimit = limits[0L]?.let { SafeSpending.compute(it, month.sumOf { e -> e.amount }, today, ym.atEndOfMonth()) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetState())

    fun setLimit(categoryId: Long, limit: Long) {
        viewModelScope.launch {
            budgets.set(categoryId, limit)
            alerts.check()
        }
    }

    fun removeLimit(categoryId: Long) {
        viewModelScope.launch { budgets.remove(categoryId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(onBack: () -> Unit, vm: BudgetViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bütçe") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { NotificationPermissionCard() }
            item { GeneralBudgetCard(s, onEdit = { editing = s.general }) }
            s.safeLimit?.let { limit -> item { SafeSpendingCard(limit, s.symbol) } }
            item {
                Text(
                    "Kategori bütçeleri",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(s.rows, key = { it.categoryId }) { row ->
                CategoryBudgetRow(row, s.symbol, onClick = { editing = row })
            }
        }
    }

    editing?.let { row ->
        LimitDialog(
            row = row,
            symbol = s.symbol,
            onDismiss = { editing = null },
            onSave = {
                vm.setLimit(row.categoryId, it)
                editing = null
            },
            onRemove = {
                vm.removeLimit(row.categoryId)
                editing = null
            }
        )
    }
}

@Composable
private fun GeneralBudgetCard(s: BudgetState, onEdit: () -> Unit) {
    val g = s.general
    val limit = g.limit
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (limit != null && limit > 0) {
                val ratio = g.spent.toFloat() / limit
                BudgetGauge(
                    progress = ratio,
                    size = 110.dp,
                    centerTop = "%${(ratio * 100).toInt()}",
                    centerBottom = "harcandı"
                )
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Genel aylık bütçe", style = MaterialTheme.typography.titleMedium)
                    Text("Harcanan: ${g.spent.formatMoney(s.symbol)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Limit: ${limit.formatMoney(s.symbol)}", style = MaterialTheme.typography.bodyMedium)
                    val remaining = limit - g.spent
                    if (remaining >= 0) {
                        Text(
                            "Kalan: ${remaining.formatMoney(s.symbol)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        val pj = g.projected
                        if (pj != null && pj > limit) {
                            Text(
                                "Bu hızla ay sonunda ~${pj.formatMoney(s.symbol)} olur, limiti aşabilirsin",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            "Limit ${(-remaining).formatMoney(s.symbol)} aşıldı",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Column {
                    Text("Genel aylık bütçe", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Henüz belirlenmedi. Dokunup aylık gider limitini gir.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetRow(row: BudgetRow, symbol: String, onClick: () -> Unit) {
    val limit = row.limit
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(row.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium)
                if (limit != null && limit > 0) {
                    val ratio = (row.spent.toFloat() / limit)
                    BudgetBar(
                        ratio = ratio,
                        projectedRatio = row.projected?.let { it.toFloat() / limit },
                        color = when {
                            ratio >= 1f -> com.harcamatakip.app.core.theme.ExpenseRed
                            ratio >= 0.8f -> com.harcamatakip.app.core.theme.WarningAmber
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Text(
                        "${row.spent.formatMoney(symbol)} / ${limit.formatMoney(symbol)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val pj = row.projected
                    if (pj != null && pj > limit && row.spent <= limit) {
                        Text(
                            "Bu hızla ay sonu ~${pj.formatMoney(symbol)} (limiti aşabilirsin)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        "Bu ay: ${row.spent.formatMoney(symbol)} · Limit yok",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitDialog(
    row: BudgetRow,
    symbol: String,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    onRemove: () -> Unit
) {
    var text by remember { mutableStateOf(row.limit?.formatPlain() ?: "") }
    val amount = parseAmountToKurus(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.title) },
        text = {
            Column {
                Text("Aylık gider limiti ($symbol)", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = sanitizeAmountInput(it) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (row.limit != null) {
                    TextButton(onClick = onRemove) {
                        Text("Limiti kaldır", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null, onClick = { amount?.let(onSave) }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

/** Bütçe çubuğu: dolgu = harcanan, ince dikey çizgi = şimdiki hızla ay sonu tahmini. */
@Composable
private fun BudgetBar(
    ratio: Float,
    projectedRatio: Float?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
        if (projectedRatio != null) {
            Box(
                Modifier.fillMaxWidth(projectedRatio.coerceIn(0.02f, 1f)).fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    }
}
