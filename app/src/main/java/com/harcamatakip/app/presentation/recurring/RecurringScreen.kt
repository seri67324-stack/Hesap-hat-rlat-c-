package com.harcamatakip.app.presentation.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.harcamatakip.app.core.theme.ExpenseRed
import com.harcamatakip.app.core.theme.IncomeGreen
import com.harcamatakip.app.core.util.CategoryIcons
import com.harcamatakip.app.core.util.fmt
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.core.util.parseAmountToKurus
import com.harcamatakip.app.core.util.sanitizeAmountInput
import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.data.local.RecurringEntity
import com.harcamatakip.app.data.repository.CategoryRepository
import com.harcamatakip.app.data.repository.RecurringRepository
import com.harcamatakip.app.data.settings.SettingsRepository
import com.harcamatakip.app.domain.model.Frequency
import com.harcamatakip.app.presentation.components.CategoryAvatar
import com.harcamatakip.app.presentation.components.EmptyState
import com.harcamatakip.app.presentation.components.SingleDatePickerDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RecurringState(
    val items: List<RecurringEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val symbol: String = "₺"
)

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val repo: RecurringRepository,
    categories: CategoryRepository,
    settings: SettingsRepository
) : ViewModel() {

    val state: StateFlow<RecurringState> = combine(
        repo.observeAll(), categories.observeAll(), settings.settings
    ) { list, cats, st -> RecurringState(list, cats, st.currency.symbol) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringState())

    fun add(amount: Long, income: Boolean, categoryId: Long, note: String, freq: Frequency, startDay: Long) {
        viewModelScope.launch {
            repo.add(
                RecurringEntity(
                    amount = amount, isIncome = income, categoryId = categoryId, note = note,
                    frequency = freq.name, startDay = startDay, nextDueDay = startDay
                )
            )
            // Başlangıç bugün veya geçmişse hemen işle
            repo.processDue(LocalDate.now().toEpochDay())
        }
    }

    fun toggle(r: RecurringEntity, active: Boolean) {
        viewModelScope.launch { repo.update(r.copy(active = active)) }
    }

    fun delete(r: RecurringEntity) {
        viewModelScope.launch { repo.delete(r) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(onBack: () -> Unit, vm: RecurringViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RecurringEntity?>(null) }
    val catMap = s.categories.associateBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tekrarlayan işlemler") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Ekle") }
            )
        }
    ) { pad ->
        if (s.items.isEmpty()) {
            Column(Modifier.padding(pad)) {
                EmptyState(
                    icon = Icons.Rounded.Repeat,
                    title = "Tekrarlayan işlem yok",
                    subtitle = "Kira, abonelik veya maaş gibi düzenli işlemleri bir kez tanımla; otomatik eklensin."
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(s.items, key = { it.id }) { r ->
                    val cat = catMap[r.categoryId]
                    val freq = runCatching { Frequency.valueOf(r.frequency) }.getOrDefault(Frequency.MONTHLY)
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryAvatar(cat)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r.note.ifBlank { cat?.name ?: "İşlem" },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${freq.label} · Sonraki: ${LocalDate.ofEpochDay(r.nextDueDay).fmt("d MMM yyyy")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    (if (r.isIncome) "+" else "−") + r.amount.formatMoney(s.symbol),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (r.isIncome) IncomeGreen else ExpenseRed
                                )
                            }
                            Switch(checked = r.active, onCheckedChange = { vm.toggle(r, it) })
                            IconButton(onClick = { deleting = r }) { Icon(Icons.Rounded.Delete, "Sil") }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CreateDialog(
            categories = s.categories,
            symbol = s.symbol,
            onDismiss = { creating = false },
            onSave = { amount, income, catId, note, freq, start ->
                vm.add(amount, income, catId, note, freq, start)
                creating = false
            }
        )
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Tekrarlayan işlem silinsin mi?") },
            text = { Text("Daha önce eklenen işlemler kalır, yenileri eklenmez.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(r)
                    deleting = null
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateDialog(
    categories: List<CategoryEntity>,
    symbol: String,
    onDismiss: () -> Unit,
    onSave: (Long, Boolean, Long, String, Frequency, Long) -> Unit
) {
    var income by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var freq by remember { mutableStateOf(Frequency.MONTHLY) }
    var start by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var showDate by remember { mutableStateOf(false) }

    val amount = parseAmountToKurus(amountText)
    val visible = categories.filter { it.isIncome == income }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni tekrarlayan işlem") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Gider" to false, "Gelir" to true).forEachIndexed { i, (label, inc) ->
                        SegmentedButton(
                            selected = income == inc,
                            onClick = {
                                income = inc
                                categoryId = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, 2)
                        ) { Text(label) }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeAmountInput(it) },
                    label = { Text("Tutar ($symbol)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(60) },
                    label = { Text("Ad / not (ör. Kira)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Sıklık", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Frequency.entries.forEach { f ->
                        FilterChip(selected = freq == f, onClick = { freq = f }, label = { Text(f.label) })
                    }
                }
                Text("Kategori", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    visible.forEach { c ->
                        FilterChip(
                            selected = categoryId == c.id,
                            onClick = { categoryId = c.id },
                            label = { Text(c.name) },
                            leadingIcon = { Icon(CategoryIcons.get(c.iconKey), null, tint = Color(c.colorArgb)) }
                        )
                    }
                }
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Başlangıç: ${LocalDate.ofEpochDay(start).fmt("d MMM yyyy")}")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && categoryId != null,
                onClick = { onSave(amount!!, income, categoryId!!, note.trim(), freq, start) }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )

    if (showDate) {
        SingleDatePickerDialog(
            initialEpochDay = start,
            onDismiss = { showDate = false },
            onConfirm = {
                start = it
                showDate = false
            }
        )
    }
}
