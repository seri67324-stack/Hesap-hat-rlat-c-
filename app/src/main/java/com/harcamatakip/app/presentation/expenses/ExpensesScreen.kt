package com.harcamatakip.app.presentation.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.harcamatakip.app.core.theme.ExpenseRed
import com.harcamatakip.app.core.theme.IncomeGreen
import com.harcamatakip.app.core.util.CategoryIcons
import com.harcamatakip.app.core.util.dayHeader
import com.harcamatakip.app.core.util.fmt
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.core.util.parseAmountToKurus
import com.harcamatakip.app.core.util.sanitizeAmountInput
import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.repository.CategoryRepository
import com.harcamatakip.app.data.repository.ExpenseRepository
import com.harcamatakip.app.data.settings.SettingsRepository
import com.harcamatakip.app.domain.model.DatePreset
import com.harcamatakip.app.domain.model.ExpenseFilter
import com.harcamatakip.app.domain.model.PaymentMethod
import com.harcamatakip.app.domain.model.SortOrder
import com.harcamatakip.app.domain.model.TxItem
import com.harcamatakip.app.domain.model.TypeFilter
import com.harcamatakip.app.domain.usecase.ExpenseFilterer
import com.harcamatakip.app.presentation.components.DateRangePickerDialogM3
import com.harcamatakip.app.presentation.components.EmptyState
import com.harcamatakip.app.presentation.components.ShimmerBox
import com.harcamatakip.app.presentation.components.TransactionRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ExpensesState(
    val loading: Boolean = true,
    val symbol: String = "₺",
    val items: List<TxItem> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val filter: ExpenseFilter = ExpenseFilter(),
    val totalIncome: Long = 0,
    val totalExpense: Long = 0
)

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenses: ExpenseRepository,
    categories: CategoryRepository,
    settings: SettingsRepository
) : ViewModel() {

    private val filter = MutableStateFlow(ExpenseFilter())

    val state: StateFlow<ExpensesState> = combine(
        expenses.observeAll(), categories.observeAll(), settings.settings, filter
    ) { all, cats, st, f ->
        val catMap = cats.associateBy { it.id }
        val sorted = ExpenseFilterer.apply(all, f, cats.associate { it.id to it.name })
        val filtered = sorted
        ExpensesState(
            loading = false,
            symbol = st.currency.symbol,
            items = sorted.map { TxItem(it, catMap[it.categoryId]) },
            categories = cats,
            filter = f,
            totalIncome = filtered.filter { it.isIncome }.sumOf { it.amount },
            totalExpense = filtered.filter { !it.isIncome }.sumOf { it.amount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpensesState())

    fun updateFilter(block: (ExpenseFilter) -> ExpenseFilter) = filter.update(block)
    fun clearFilters() = filter.update { ExpenseFilter(query = it.query, type = it.type) }

    fun delete(e: ExpenseEntity) {
        viewModelScope.launch { expenses.delete(e) }
    }

    /** "Geri al": aynı id ile tekrar eklenir. */
    fun restore(e: ExpenseEntity) {
        viewModelScope.launch { expenses.insert(e) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    vm: ExpensesViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Harcamalar", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = {
                            if (s.filter.activeCount > 0) Badge { Text("${s.filter.activeCount}") }
                        }) { Icon(Icons.Rounded.FilterList, "Filtrele") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Ekle") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = s.filter.query,
                onValueChange = { q -> vm.updateFilter { it.copy(query = q) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Not veya kategori ara") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (s.filter.query.isNotEmpty()) {
                        IconButton(onClick = { vm.updateFilter { it.copy(query = "") } }) {
                            Icon(Icons.Rounded.Clear, "Temizle")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeFilter.entries.forEach { t ->
                    FilterChip(
                        selected = s.filter.type == t,
                        onClick = { vm.updateFilter { it.copy(type = t) } },
                        label = { Text(t.label) }
                    )
                }
            }
            // Ödeme yöntemi (doğrudan seçilir) + kategori / tarih / sıralama için sheet kısayolları
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PaymentMethod.all, key = { it }) { code ->
                    FilterChip(
                        selected = code in s.filter.paymentMethods,
                        onClick = {
                            vm.updateFilter {
                                it.copy(
                                    paymentMethods = if (code in it.paymentMethods) it.paymentMethods - code
                                    else it.paymentMethods + code
                                )
                            }
                        },
                        label = { Text(PaymentMethod.label(code)) }
                    )
                }
                item(key = "chipCategory") {
                    FilterChip(
                        selected = s.filter.categoryIds.isNotEmpty(),
                        onClick = { showFilters = true },
                        label = {
                            Text(if (s.filter.categoryIds.isEmpty()) "Kategori" else "Kategori (${s.filter.categoryIds.size})")
                        },
                        trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) }
                    )
                }
                item(key = "chipDate") {
                    FilterChip(
                        selected = s.filter.from != null && s.filter.to != null,
                        onClick = { showFilters = true },
                        label = { Text("Tarih aralığı") },
                        trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) }
                    )
                }
                item(key = "chipSort") {
                    FilterChip(
                        selected = s.filter.sort != SortOrder.DATE_DESC,
                        onClick = { showFilters = true },
                        label = { Text(s.filter.sort.label) },
                        trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) }
                    )
                }
            }
            if (!s.loading && s.items.isNotEmpty()) {
                Text(
                    "${s.items.size} işlem · Gelir ${s.totalIncome.formatMoney(s.symbol)} · Gider ${s.totalExpense.formatMoney(s.symbol)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            when {
                s.loading -> Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(5) { ShimmerBox(Modifier.fillMaxWidth().height(60.dp)) }
                }

                s.items.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.ReceiptLong,
                    title = if (s.filter.isActive)
                        "Sonuç bulunamadı" else "Henüz işlem yok",
                    subtitle = if (s.filter.isActive)
                        "Filtreleri değiştirmeyi dene." else "İlk işlemini eklemek için Ekle'ye dokun."
                )

                else -> {
                    val grouped = s.filter.sort == SortOrder.DATE_DESC || s.filter.sort == SortOrder.DATE_ASC
                    val groups = if (grouped) s.items.groupBy { it.expense.date } else emptyMap()
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        if (grouped) {
                            groups.forEach { (day, list) ->
                                item(key = "h$day") {
                                    Text(
                                        dayHeader(day),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(list, key = { it.expense.id }) { item ->
                                    SwipeRow(item, s.symbol, onOpen, onDelete = { e ->
                                        vm.delete(e)
                                        scope.launch {
                                            snackbar.currentSnackbarData?.dismiss()
                                            val r = snackbar.showSnackbar(
                                                "İşlem silindi", "Geri al",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (r == SnackbarResult.ActionPerformed) vm.restore(e)
                                        }
                                    })
                                }
                            }
                        } else {
                            items(s.items, key = { it.expense.id }) { item ->
                                SwipeRow(item, s.symbol, onOpen, onDelete = { e ->
                                    vm.delete(e)
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val r = snackbar.showSnackbar(
                                            "İşlem silindi", "Geri al",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (r == SnackbarResult.ActionPerformed) vm.restore(e)
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            state = s,
            onChange = vm::updateFilter,
            onClear = vm::clearFilters,
            onDismiss = { showFilters = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.SwipeRow(
    item: TxItem,
    symbol: String,
    onOpen: (Long) -> Unit,
    onDelete: (ExpenseEntity) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item.expense)
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.animateItem(),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(ExpenseRed).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Rounded.Delete, "Sil", tint = Color.White)
            }
        }
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            TransactionRow(item, symbol, onClick = { onOpen(item.expense.id) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: ExpensesState,
    onChange: ((ExpenseFilter) -> ExpenseFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val f = state.filter
    var showRange by remember { mutableStateOf(false) }
    var minText by remember { mutableStateOf(f.minAmount?.let { String.format("%.2f", it / 100.0).replace('.', ',') } ?: "") }
    var maxText by remember { mutableStateOf(f.maxAmount?.let { String.format("%.2f", it / 100.0).replace('.', ',') } ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtrele ve sırala", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    onClear()
                    minText = ""
                    maxText = ""
                }) { Text("Temizle") }
            }

            Text("Sırala", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOrder.entries.forEach { o ->
                    FilterChip(
                        selected = f.sort == o,
                        onClick = { onChange { it.copy(sort = o) } },
                        label = { Text(o.label) }
                    )
                }
            }

            Text("Kategori", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { c ->
                    FilterChip(
                        selected = c.id in f.categoryIds,
                        onClick = {
                            onChange {
                                it.copy(
                                    categoryIds = if (c.id in it.categoryIds) it.categoryIds - c.id
                                    else it.categoryIds + c.id
                                )
                            }
                        },
                        label = { Text(c.name) },
                        leadingIcon = {
                            Icon(CategoryIcons.get(c.iconKey), null, tint = Color(c.colorArgb))
                        }
                    )
                }
            }

            Text("Ödeme yöntemi", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.all.forEach { code ->
                    FilterChip(
                        selected = code in f.paymentMethods,
                        onClick = {
                            onChange {
                                it.copy(
                                    paymentMethods = if (code in it.paymentMethods) it.paymentMethods - code
                                    else it.paymentMethods + code
                                )
                            }
                        },
                        label = { Text(PaymentMethod.label(code)) }
                    )
                }
            }

            Text("Tarih aralığı", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePreset.entries.forEach { preset ->
                    val (a, b) = preset.range(LocalDate.now())
                    FilterChip(
                        selected = f.from == a.toEpochDay() && f.to == b.toEpochDay(),
                        onClick = { onChange { it.copy(from = a.toEpochDay(), to = b.toEpochDay()) } },
                        label = { Text(preset.label) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showRange = true }) {
                    Icon(Icons.Rounded.CalendarMonth, null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    val from = f.from
                    val to = f.to
                    Text(
                        if (from != null && to != null)
                            "${LocalDate.ofEpochDay(from).fmt("d MMM yyyy")} – ${LocalDate.ofEpochDay(to).fmt("d MMM yyyy")}"
                        else "Tarih seç"
                    )
                }
                if (f.from != null) {
                    TextButton(onClick = { onChange { it.copy(from = null, to = null) } }) { Text("Kaldır") }
                }
            }

            Text("Tutar aralığı", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = {
                        minText = sanitizeAmountInput(it)
                        val v = parseAmountToKurus(minText)
                        onChange { f0 -> f0.copy(minAmount = v) }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("En az") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = {
                        maxText = sanitizeAmountInput(it)
                        val v = parseAmountToKurus(maxText)
                        onChange { f0 -> f0.copy(maxAmount = v) }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("En çok") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showRange) {
        val from = f.from
        val to = f.to
        DateRangePickerDialogM3(
            initial = if (from != null && to != null) LocalDate.ofEpochDay(from) to LocalDate.ofEpochDay(to) else null,
            onDismiss = { showRange = false },
            onConfirm = { a, b ->
                onChange { it.copy(from = a.toEpochDay(), to = b.toEpochDay()) }
                showRange = false
            }
        )
    }
}
