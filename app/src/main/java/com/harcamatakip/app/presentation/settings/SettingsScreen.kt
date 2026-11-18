package com.harcamatakip.app.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.harcamatakip.app.core.theme.ThemeMode
import com.harcamatakip.app.core.util.AppCurrency
import com.harcamatakip.app.data.repository.DataManager
import com.harcamatakip.app.data.settings.AppSettings
import com.harcamatakip.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val data: DataManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _importErrors = MutableStateFlow<List<String>>(emptyList())
    val importErrors: StateFlow<List<String>> = _importErrors.asStateFlow()

    fun consumeMessage() { _message.value = null }
    fun dismissErrors() { _importErrors.value = emptyList() }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { repo.setTheme(m) }
    fun setDynamic(b: Boolean) = viewModelScope.launch { repo.setDynamicColor(b) }
    fun setCurrency(c: AppCurrency) = viewModelScope.launch { repo.setCurrency(c) }
    fun setWeekStart(d: DayOfWeek) = viewModelScope.launch { repo.setWeekStart(d) }

    private fun run(okText: (Int) -> String, block: suspend () -> Int) {
        viewModelScope.launch {
            _message.value = try {
                okText(block())
            } catch (e: Exception) {
                "Hata: ${e.message ?: "işlem başarısız"}"
            }
        }
    }

    fun exportCsv(uri: android.net.Uri) = run({ "$it işlem CSV olarak dışa aktarıldı" }) { data.exportCsv(uri) }
    fun backup(uri: android.net.Uri) = run({ "Yedek alındı ($it işlem)" }) { data.backup(uri) }
    fun restore(uri: android.net.Uri) = run({ "Yedek geri yüklendi ($it işlem)" }) { data.restore(uri) }

    fun importCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val r = data.importCsv(uri)
                _message.value = "${r.imported} işlem içe aktarıldı" +
                    if (r.errors.isNotEmpty()) ", ${r.errors.size} satır atlandı" else ""
                _importErrors.value = r.errors
            } catch (e: Exception) {
                _message.value = "Hata: ${e.message ?: "dosya okunamadı"}"
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            data.deleteAll()
            _message.value = "Tüm veriler silindi"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCategories: () -> Unit,
    onBudget: () -> Unit,
    onRecurring: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val errors by vm.importErrors.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var currencyDialog by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let(vm::exportCsv)
    }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(vm::importCsv)
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(vm::backup)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) confirmRestore = it
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ayarlar", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            Group("Yönetim")
            Item("Kategoriler", "Ekle, düzenle, sil", Icons.Rounded.Category, onClick = onCategories)
            Item("Bütçe", "Aylık ve kategori limitleri", Icons.Rounded.Savings, onClick = onBudget)
            Item("Tekrarlayan işlemler", "Kira, abonelik, maaş", Icons.Rounded.Repeat, onClick = onRecurring)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Group("Görünüm")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tema", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.SYSTEM to "Sistem",
                        ThemeMode.LIGHT to "Açık",
                        ThemeMode.DARK to "Koyu"
                    ).forEach { (mode, label) ->
                        FilterChip(selected = s.themeMode == mode, onClick = { vm.setTheme(mode) }, label = { Text(label) })
                    }
                }
            }
            ListItem(
                headlineContent = { Text("Dinamik renk") },
                supportingContent = { Text("Android 12+ cihazlarda duvar kağıdı renklerini kullan") },
                trailingContent = { Switch(checked = s.dynamicColor, onCheckedChange = vm::setDynamic) }
            )
            ListItem(
                modifier = Modifier.clickable { currencyDialog = true },
                headlineContent = { Text("Para birimi") },
                supportingContent = { Text(s.currency.label) }
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Haftanın ilk günü", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DayOfWeek.MONDAY to "Pazartesi", DayOfWeek.SUNDAY to "Pazar").forEach { (d, label) ->
                        FilterChip(selected = s.weekStart == d, onClick = { vm.setWeekStart(d) }, label = { Text(label) })
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Group("Veri")
            Item("CSV dışa aktar", "Excel'de açılabilir", Icons.Rounded.FileUpload) {
                exportCsv.launch("harcamalar_${LocalDate.now()}.csv")
            }
            Item("CSV içe aktar", "Tarih;Tür;Tutar;Kategori;Not;Ödeme", Icons.Rounded.FileDownload) {
                importCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*"))
            }
            Item("Yedek al", "Tüm verileri tek dosyaya kaydet", Icons.Rounded.Backup) {
                backup.launch("harcama_yedek_${LocalDate.now()}.json")
            }
            Item("Yedeği geri yükle", "Mevcut verilerin yerine geçer", Icons.Rounded.Restore) {
                restore.launch(arrayOf("application/json", "*/*"))
            }
            Item("Tüm verileri sil", "Geri alınamaz", Icons.Rounded.DeleteForever, danger = true) {
                confirmDelete = true
            }
            Text(
                "Harcama Takip internet izni kullanmaz; tüm verilerin yalnızca bu cihazda tutulur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    if (currencyDialog) {
        AlertDialog(
            onDismissRequest = { currencyDialog = false },
            title = { Text("Para birimi") },
            text = {
                Column {
                    AppCurrency.entries.forEach { c ->
                        ListItem(
                            modifier = Modifier.clickable {
                                vm.setCurrency(c)
                                currencyDialog = false
                            },
                            headlineContent = { Text(c.label) },
                            trailingContent = { if (c == s.currency) Text("✓") }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { currencyDialog = false }) { Text("Kapat") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Tüm veriler silinsin mi?") },
            text = { Text("İşlemler, bütçeler, tekrarlayanlar ve özel kategoriler silinir. Bu geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAll()
                }) { Text("Hepsini sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
        )
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Yedek geri yüklensin mi?") },
            text = { Text("Mevcut tüm verilerin yedekteki verilerle değiştirilecek.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    vm.restore(uri)
                }) { Text("Geri yükle") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Vazgeç") } }
        )
    }

    if (errors.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::dismissErrors,
            title = { Text("Atlanan satırlar (${errors.size})") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    errors.take(50).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (errors.size > 50) Text("… ve ${errors.size - 50} satır daha")
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissErrors) { Text("Tamam") } }
        )
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
    )
}

@Composable
private fun Item(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = {
            Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = {
            Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        },
        supportingContent = { Text(subtitle) }
    )
}
