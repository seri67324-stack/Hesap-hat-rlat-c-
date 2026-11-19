package com.harcamatakip.app.presentation.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.harcamatakip.app.core.util.AppEvents
import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.local.QuickTemplateEntity
import com.harcamatakip.app.data.repository.BudgetAlertManager
import com.harcamatakip.app.data.repository.BudgetRepository
import com.harcamatakip.app.data.repository.DataManager
import com.harcamatakip.app.data.repository.TemplateAddResult
import com.harcamatakip.app.data.repository.TemplateRepository
import com.harcamatakip.app.data.repository.CategoryRepository
import com.harcamatakip.app.data.repository.ExpenseRepository
import com.harcamatakip.app.data.settings.SettingsRepository
import com.harcamatakip.app.domain.model.PaymentMethod
import com.harcamatakip.app.domain.model.TxItem
import com.harcamatakip.app.domain.usecase.BackupReminderPolicy
import com.harcamatakip.app.domain.usecase.ComboSuggestion
import com.harcamatakip.app.domain.usecase.FrequentCombos
import com.harcamatakip.app.domain.usecase.SafeDailyLimit
import com.harcamatakip.app.domain.usecase.SafeSpending
import com.harcamatakip.app.presentation.components.BackupReminderCard
import com.harcamatakip.app.presentation.components.QuickAddSection
import com.harcamatakip.app.presentation.components.SafeSpendingCard
import com.harcamatakip.app.presentation.components.SuggestionUi
import com.harcamatakip.app.presentation.components.TemplateUi
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val symbol: String = "₺",
    val balance: Long = 0,
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val recent: List<TxItem> = emptyList(),
    val generalBudget: Long? = null,
    val last7: List<DayTotal> = emptyList(),
    val templates: List<TemplateUi> = emptyList(),
    val suggestions: List<SuggestionUi> = emptyList(),
    /** "Ay sonuna kadar günde ortalama X" özeti; genel bütçe yoksa null. */
    val safeLimit: SafeDailyLimit? = null,
    val showBackupReminder: Boolean = false,
    val daysSinceBackup: Long? = null
)

/** Snackbar olayı. [undoExpenseId] doluysa "Geri al" eylemi sunulur. */
data class HomeEvent(val message: String, val undoExpenseId: Long? = null)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenses: ExpenseRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    private val settings: SettingsRepository,
    private val templateRepo: TemplateRepository,
    private val alerts: BudgetAlertManager,
    private val data: DataManager
) : ViewModel() {

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    init {
        // Yedek hatırlatıcısı sayımı ilk açılış gününden başlar
        viewModelScope.launch { settings.ensureBackupBaseline(LocalDate.now().toEpochDay()) }
    }

    val state: StateFlow<HomeState> = combine(
        expenses.observeAll(),
        categories.observeAll(),
        budgets.observeAll(),
        settings.settings,
        templateRepo.observeAll()
    ) { all, cats, budgetList, st, templates ->
        val catMap = cats.associateBy { it.id }
        val todayDate = LocalDate.now()
        val today = todayDate.toEpochDay()
        val ym = YearMonth.from(todayDate)
        val from = ym.atDay(1).toEpochDay()
        val to = ym.atEndOfMonth().toEpochDay()
        val month = all.filter { it.date in from..to }
        val monthExpense = month.filter { !it.isIncome }.sumOf { it.amount }
        val generalBudget = budgetList.firstOrNull { it.categoryId == 0L }?.limitAmount
        val due = all.isNotEmpty() && BackupReminderPolicy.isDue(
            st.backupIntervalDays, today, st.lastBackupDay, st.backupSnoozeDay, st.backupBaselineDay
        )
        HomeState(
            loading = false,
            symbol = st.currency.symbol,
            balance = all.sumOf { if (it.isIncome) it.amount else -it.amount },
            monthIncome = month.filter { it.isIncome }.sumOf { it.amount },
            monthExpense = monthExpense,
            recent = all.take(5).map { TxItem(it, catMap[it.categoryId]) },
            generalBudget = generalBudget,
            last7 = AnalyticsCalculator.daily(all, 7, todayDate),
            templates = templates.map { TemplateUi(it, catMap[it.categoryId]) },
            suggestions = FrequentCombos.suggest(all, templates, today)
                .filter { catMap.containsKey(it.categoryId) }
                .map { SuggestionUi(it, catMap[it.categoryId]) },
            safeLimit = generalBudget?.let { SafeSpending.compute(it, monthExpense, todayDate, ym.atEndOfMonth()) },
            showBackupReminder = due,
            daysSinceBackup = BackupReminderPolicy.daysSince(st.lastBackupDay, today)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    /** Şablona tek dokunuş: bugünün tarihiyle işlem eklenir. */
    fun quickAdd(t: QuickTemplateEntity) {
        viewModelScope.launch {
            val id = templateRepo.createExpense(t, LocalDate.now().toEpochDay())
            if (!t.isIncome) alerts.check()
            _events.emit(HomeEvent("${t.title} eklendi", id))
        }
    }

    /** "Sık yaptıkların" çipine dokunuş: şablon kaydetmeden doğrudan işlem ekler. */
    fun quickAdd(s: ComboSuggestion) {
        viewModelScope.launch {
            val id = expenses.insert(
                ExpenseEntity(
                    amount = s.amount, isIncome = s.isIncome, categoryId = s.categoryId,
                    date = LocalDate.now().toEpochDay(), note = s.note,
                    paymentMethod = PaymentMethod.normalize(s.paymentMethod), paymentLabel = s.paymentLabel
                )
            )
            if (!s.isIncome) alerts.check()
            _events.emit(HomeEvent("İşlem eklendi", id))
        }
    }

    fun saveSuggestionAsTemplate(s: ComboSuggestion) {
        viewModelScope.launch {
            val name = state.value.suggestions.firstOrNull { it.suggestion == s }?.category?.name ?: "İşlem"
            val result = templateRepo.add(
                QuickTemplateEntity(
                    title = s.note.ifBlank { name }, amount = s.amount, isIncome = s.isIncome,
                    categoryId = s.categoryId, paymentMethod = s.paymentMethod,
                    paymentLabel = s.paymentLabel, note = s.note
                )
            )
            _events.emit(
                HomeEvent(
                    when (result) {
                        TemplateAddResult.ADDED -> "Şablon kaydedildi"
                        TemplateAddResult.DUPLICATE -> "Bu şablon zaten kayıtlı"
                        TemplateAddResult.LIMIT -> "En fazla ${TemplateRepository.MAX_TEMPLATES} şablon kaydedebilirsin"
                    }
                )
            )
        }
    }

    fun deleteTemplate(t: QuickTemplateEntity) {
        viewModelScope.launch {
            templateRepo.delete(t)
            _events.emit(HomeEvent("Şablon silindi"))
        }
    }

    fun undo(expenseId: Long) {
        viewModelScope.launch { expenses.deleteById(expenseId) }
    }

    /** Mevcut JSON yedekleme akışı (DataManager.backup); başarılıysa son yedek günü güncellenir. */
    fun backup(uri: Uri) {
        viewModelScope.launch {
            val msg = try {
                "Yedek alındı (${data.backup(uri)} işlem)"
            } catch (e: Exception) {
                "Hata: ${e.message ?: "yedek alınamadı"}"
            }
            _events.emit(HomeEvent(msg))
        }
    }

    /** "Yarın hatırlat": kart bugün gizlenir, yarın yeniden görünür. */
    fun snoozeBackup() {
        viewModelScope.launch {
            val st = settings.settingsOnce()
            settings.snoozeBackup(
                BackupReminderPolicy.snoozeDayForTomorrow(LocalDate.now().toEpochDay(), st.backupIntervalDays)
            )
        }
    }
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
    val snackbar = remember { SnackbarHostState() }
    var deletingTemplate by remember { mutableStateOf<QuickTemplateEntity?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::backup) }
    val startBackup by AppEvents.startBackup.collectAsStateWithLifecycle()
    // Yedek hatırlatma bildirimine dokunulduysa kaydetme penceresi doğrudan açılır
    LaunchedEffect(startBackup) {
        if (startBackup) {
            AppEvents.consumeBackup()
            backupLauncher.launch("harcama_yedek_${LocalDate.now()}.json")
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            snackbar.currentSnackbarData?.dismiss()
            val undoId = ev.undoExpenseId
            val r = snackbar.showSnackbar(
                ev.message,
                actionLabel = if (undoId != null) "Geri al" else null,
                duration = SnackbarDuration.Short
            )
            if (r == SnackbarResult.ActionPerformed && undoId != null) vm.undo(undoId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Genel Bakış", fontWeight = FontWeight.Bold) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
                if (s.showBackupReminder) {
                    item {
                        BackupReminderCard(
                            daysSinceLastBackup = s.daysSinceBackup,
                            onBackup = { backupLauncher.launch("harcama_yedek_${LocalDate.now()}.json") },
                            onSnooze = vm::snoozeBackup,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                item { BudgetCard(s, onBudget) }
                s.safeLimit?.let { limit ->
                    item { SafeSpendingCard(limit, s.symbol, Modifier.padding(horizontal = 16.dp)) }
                }
                item {
                    QuickAddSection(
                        templates = s.templates,
                        suggestions = s.suggestions,
                        symbol = s.symbol,
                        onTemplateClick = vm::quickAdd,
                        onTemplateLongClick = { deletingTemplate = it },
                        onSuggestionClick = { sug -> vm.quickAdd(sug) },
                        onSuggestionSave = vm::saveSuggestionAsTemplate
                    )
                }
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

    deletingTemplate?.let { t ->
        AlertDialog(
            onDismissRequest = { deletingTemplate = null },
            title = { Text("Şablon silinsin mi?") },
            text = { Text("\"${t.title}\" şablonu silinir. Daha önce eklenen işlemler etkilenmez.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTemplate(t)
                    deletingTemplate = null
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingTemplate = null }) { Text("Vazgeç") } }
        )
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
