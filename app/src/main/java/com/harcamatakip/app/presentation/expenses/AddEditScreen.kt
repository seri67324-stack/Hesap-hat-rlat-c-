package com.harcamatakip.app.presentation.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.harcamatakip.app.core.theme.ExpenseRed
import com.harcamatakip.app.core.theme.IncomeGreen
import com.harcamatakip.app.core.util.CategoryIcons
import com.harcamatakip.app.core.util.fmt
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.core.util.formatPlain
import com.harcamatakip.app.core.util.parseAmountToKurus
import com.harcamatakip.app.core.util.sanitizeAmountInput
import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.local.QuickTemplateEntity
import com.harcamatakip.app.data.repository.BudgetAlertManager
import com.harcamatakip.app.data.repository.CategoryRepository
import com.harcamatakip.app.data.repository.ExpenseRepository
import com.harcamatakip.app.data.repository.TemplateAddResult
import com.harcamatakip.app.data.repository.TemplateRepository
import com.harcamatakip.app.data.settings.SettingsRepository
import com.harcamatakip.app.domain.model.PaymentMethod
import com.harcamatakip.app.presentation.components.SingleDatePickerDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AddEditState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val isIncome: Boolean = false,
    val amountText: String = "",
    val categoryId: Long? = null,
    val date: Long = LocalDate.now().toEpochDay(),
    val note: String = "",
    val payment: String = PaymentMethod.CASH,
    val paymentLabel: String = "",
    val recentLabels: List<String> = emptyList(),
    val templates: List<QuickTemplateEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val symbol: String = "₺",
    val error: String? = null,
    val message: String? = null,
    val done: Boolean = false
)

@HiltViewModel
class AddEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val expenses: ExpenseRepository,
    categories: CategoryRepository,
    settings: SettingsRepository,
    private val alerts: BudgetAlertManager,
    private val templateRepo: TemplateRepository
) : ViewModel() {

    private val id: Long = savedState.get<Long>("id") ?: -1L
    private var loaded: ExpenseEntity? = null

    private val _state = MutableStateFlow(AddEditState())
    val state: StateFlow<AddEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            categories.observeAll().collect { list -> _state.update { it.copy(categories = list) } }
        }
        viewModelScope.launch {
            settings.settings.collect { st -> _state.update { it.copy(symbol = st.currency.symbol) } }
        }
        viewModelScope.launch {
            expenses.observeRecentLabels().collect { l -> _state.update { it.copy(recentLabels = l) } }
        }
        viewModelScope.launch {
            templateRepo.observeAll().collect { l -> _state.update { it.copy(templates = l) } }
        }
        viewModelScope.launch {
            if (id > 0) {
                val e = expenses.getById(id)
                if (e != null) {
                    loaded = e
                    _state.update {
                        it.copy(
                            loading = false, isEdit = true, isIncome = e.isIncome,
                            amountText = e.amount.formatPlain(), categoryId = e.categoryId,
                            date = e.date, note = e.note,
                            payment = PaymentMethod.normalize(e.paymentMethod),
                            paymentLabel = e.paymentLabel
                        )
                    }
                    return@launch
                }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun setIncome(income: Boolean) = _state.update { st ->
        val stillValid = st.categories.firstOrNull { it.id == st.categoryId }?.isIncome == income
        st.copy(isIncome = income, categoryId = if (stillValid) st.categoryId else null, error = null)
    }

    fun setAmount(text: String) = _state.update { it.copy(amountText = sanitizeAmountInput(text), error = null) }
    fun setCategory(id: Long) = _state.update { it.copy(categoryId = id, error = null) }
    fun setDate(day: Long) = _state.update { it.copy(date = day) }
    fun setNote(text: String) = _state.update { it.copy(note = text.take(200)) }
    fun setPayment(p: String) = _state.update {
        // Nakit seçilince kart etiketi anlamsızdır
        it.copy(payment = p, paymentLabel = if (p == PaymentMethod.CASH) "" else it.paymentLabel)
    }
    fun setPaymentLabel(text: String) = _state.update { it.copy(paymentLabel = text.take(PaymentMethod.LABEL_MAX)) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Şablona dokununca formu doldurur (kaydetmek için yine "Kaydet"e basılır). Tarih bugün olur. */
    fun applyTemplate(t: QuickTemplateEntity) = _state.update {
        it.copy(
            isIncome = t.isIncome, amountText = t.amount.formatPlain(), categoryId = t.categoryId,
            note = t.note, payment = PaymentMethod.normalize(t.paymentMethod), paymentLabel = t.paymentLabel,
            date = LocalDate.now().toEpochDay(), error = null
        )
    }

    /** Formdaki (veya düzenlenen) işlemi şablon olarak kaydeder. */
    fun saveAsTemplate(title: String) {
        val s = _state.value
        val amount = parseAmountToKurus(s.amountText)
        val categoryId = s.categoryId
        if (amount == null || categoryId == null) {
            _state.update { it.copy(error = "Şablon için tutar ve kategori gerekli") }
            return
        }
        val name = s.categories.firstOrNull { it.id == categoryId }?.name ?: "İşlem"
        viewModelScope.launch {
            val result = templateRepo.add(
                QuickTemplateEntity(
                    title = title.ifBlank { s.note.ifBlank { name } },
                    amount = amount, isIncome = s.isIncome, categoryId = categoryId,
                    paymentMethod = s.payment, paymentLabel = s.paymentLabel, note = s.note.trim()
                )
            )
            val msg = when (result) {
                TemplateAddResult.ADDED -> "Şablon kaydedildi · Ana ekrandan tek dokunuşla ekleyebilirsin"
                TemplateAddResult.DUPLICATE -> "Bu şablon zaten kayıtlı"
                TemplateAddResult.LIMIT -> "En fazla ${TemplateRepository.MAX_TEMPLATES} şablon kaydedebilirsin"
            }
            _state.update { it.copy(message = msg) }
        }
    }

    fun save() {
        val s = _state.value
        val amount = parseAmountToKurus(s.amountText)
        if (amount == null) {
            _state.update { it.copy(error = "Geçerli bir tutar gir") }
            return
        }
        val categoryId = s.categoryId
        if (categoryId == null) {
            _state.update { it.copy(error = "Bir kategori seç") }
            return
        }
        viewModelScope.launch {
            val entity = (loaded ?: ExpenseEntity(amount = amount, isIncome = s.isIncome, categoryId = categoryId, date = s.date))
                .copy(
                    amount = amount, isIncome = s.isIncome, categoryId = categoryId,
                    date = s.date, note = s.note.trim(), paymentMethod = s.payment,
                    paymentLabel = if (s.payment == PaymentMethod.CASH) "" else PaymentMethod.cleanLabel(s.paymentLabel)
                )
            if (s.isEdit) expenses.update(entity) else expenses.insert(entity)
            if (!s.isIncome) alerts.check()
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val e = loaded ?: return
        viewModelScope.launch {
            expenses.delete(e)
            _state.update { it.copy(done = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(onBack: () -> Unit, vm: AddEditViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(s.message) {
        s.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    LaunchedEffect(s.done) { if (s.done) onBack() }
    LaunchedEffect(s.loading) {
        if (!s.loading && !s.isEdit) {
            delay(300)
            runCatching { focus.requestFocus() }
        }
    }

    val tint = if (s.isIncome) IncomeGreen else ExpenseRed
    val visibleCategories = s.categories.filter { it.isIncome == s.isIncome }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isEdit) "İşlemi düzenle" else "Yeni işlem") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
                },
                actions = {
                    IconButton(onClick = { showTemplateDialog = true }) {
                        Icon(Icons.Rounded.BookmarkAdd, "Şablon olarak kaydet")
                    }
                    if (s.isEdit) {
                        IconButton(onClick = { showDelete = true }) { Icon(Icons.Rounded.Delete, "Sil") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hızlı şablonlar: dokununca form dolar
            val visibleTemplates = s.templates.filter { it.isIncome == s.isIncome }
            if (!s.isEdit && visibleTemplates.isNotEmpty()) {
                Text("Şablondan doldur", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleTemplates, key = { it.id }) { t ->
                        FilterChip(
                            selected = false,
                            onClick = { vm.applyTemplate(t) },
                            label = { Text("${t.title} · ${t.amount.formatMoney(s.symbol)}") }
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Gider" to false, "Gelir" to true).forEachIndexed { i, (label, income) ->
                    SegmentedButton(
                        selected = s.isIncome == income,
                        onClick = { vm.setIncome(income) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                    ) { Text(label) }
                }
            }

            // Büyük tutar girişi
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicTextField(
                    value = s.amountText,
                    onValueChange = vm::setAmount,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    textStyle = TextStyle(
                        fontSize = 48.sp, fontWeight = FontWeight.Bold,
                        color = tint, textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(tint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (s.amountText.isEmpty()) {
                                Text(
                                    "0,00",
                                    style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                )
                            }
                            inner()
                        }
                    }
                )
                Text(
                    "Tutar (${s.symbol})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Kategori", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                visibleCategories.forEach { c ->
                    FilterChip(
                        selected = s.categoryId == c.id,
                        onClick = { vm.setCategory(c.id) },
                        label = { Text(c.name) },
                        leadingIcon = {
                            Icon(CategoryIcons.get(c.iconKey), null, tint = Color(c.colorArgb))
                        }
                    )
                }
            }

            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CalendarMonth, null)
                Spacer(Modifier.width(8.dp))
                Text(LocalDate.ofEpochDay(s.date).fmt("d MMMM yyyy, EEEE"))
            }

            Text("Ödeme yöntemi", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PaymentMethod.all.forEachIndexed { i, code ->
                    SegmentedButton(
                        selected = s.payment == code,
                        onClick = { vm.setPayment(code) },
                        shape = SegmentedButtonDefaults.itemShape(i, PaymentMethod.all.size)
                    ) { Text(PaymentMethod.label(code), maxLines = 1) }
                }
            }
            if (s.payment != PaymentMethod.CASH) {
                OutlinedTextField(
                    value = s.paymentLabel,
                    onValueChange = vm::setPaymentLabel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Kart / hesap etiketi (isteğe bağlı)") },
                    placeholder = { Text("örn. Garanti •1234") },
                    singleLine = true
                )
                if (s.recentLabels.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.recentLabels.forEach { l ->
                            FilterChip(
                                selected = s.paymentLabel == l,
                                onClick = { vm.setPaymentLabel(l) },
                                label = { Text(l) }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = s.note,
                onValueChange = vm::setNote,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Not (isteğe bağlı)") },
                maxLines = 3
            )

            if (s.error != null) {
                Text(s.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = vm::save,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(if (s.isEdit) "Güncelle" else "Kaydet", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (showDate) {
        SingleDatePickerDialog(
            initialEpochDay = s.date,
            onDismiss = { showDate = false },
            onConfirm = { day ->
                vm.setDate(day)
                showDate = false
            }
        )
    }

    if (showTemplateDialog) {
        val defaultTitle = s.note.ifBlank { s.categories.firstOrNull { it.id == s.categoryId }?.name ?: "" }
        var title by remember { mutableStateOf(defaultTitle) }
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text("Şablon olarak kaydet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Tutar, kategori, ödeme yöntemi ve not şablona kaydedilir. Ana ekrandaki \"Hızlı ekle\" alanından tek dokunuşla eklenir.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(TemplateRepository.TITLE_MAX) },
                        label = { Text("Şablon adı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTemplateDialog = false
                    vm.saveAsTemplate(title)
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { showTemplateDialog = false }) { Text("Vazgeç") } }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("İşlem silinsin mi?") },
            text = { Text("Bu işlem kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.delete()
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Vazgeç") } }
        )
    }
}
