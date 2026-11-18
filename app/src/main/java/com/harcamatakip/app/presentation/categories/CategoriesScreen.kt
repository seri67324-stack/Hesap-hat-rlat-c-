package com.harcamatakip.app.presentation.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.harcamatakip.app.core.util.CategoryIcons
import com.harcamatakip.app.core.util.CategoryPalette
import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.data.repository.CategoryRepository
import com.harcamatakip.app.presentation.components.CategoryAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repo: CategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(c: CategoryEntity) {
        viewModelScope.launch { if (c.id == 0L) repo.add(c) else repo.update(c) }
    }

    fun delete(c: CategoryEntity) {
        viewModelScope.launch { repo.deleteAndReassign(c) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kategoriler") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Kategori ekle") }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            val expense = cats.filter { !it.isIncome }
            val income = cats.filter { it.isIncome }
            item {
                Text(
                    "Gider kategorileri", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
                )
            }
            items(expense, key = { it.id }) { c -> CategoryRow(c, { editing = c }, { deleting = c }) }
            item {
                Text(
                    "Gelir kategorileri", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp)
                )
            }
            items(income, key = { it.id }) { c -> CategoryRow(c, { editing = c }, { deleting = c }) }
        }
    }

    if (creating) {
        CategoryEditDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = {
                vm.save(it)
                creating = false
            }
        )
    }
    editing?.let { c ->
        CategoryEditDialog(
            initial = c,
            onDismiss = { editing = null },
            onSave = {
                vm.save(it)
                editing = null
            }
        )
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("\"${c.name}\" silinsin mi?") },
            text = { Text("Bu kategorideki işlemler \"${if (c.isIncome) "Diğer Gelir" else "Diğer"}\" kategorisine taşınır. Bu kategoriye ait bütçe de silinir.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(c)
                    deleting = null
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun CategoryRow(c: CategoryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        leadingContent = { CategoryAvatar(c) },
        headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Düzenle") }
                if (!c.isFallback) {
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Sil") }
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditDialog(
    initial: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var iconKey by remember { mutableStateOf(initial?.iconKey ?: "category") }
    var color by remember { mutableStateOf(initial?.colorArgb ?: CategoryPalette[0]) }
    var isIncome by remember { mutableStateOf(initial?.isIncome ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni kategori" else "Kategoriyi düzenle") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Ad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (initial == null) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Gider" to false, "Gelir" to true).forEachIndexed { i, (label, inc) ->
                            SegmentedButton(
                                selected = isIncome == inc,
                                onClick = { isIncome = inc },
                                shape = SegmentedButtonDefaults.itemShape(i, 2)
                            ) { Text(label) }
                        }
                    }
                }
                Text("İkon", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryIcons.all.forEach { (key, icon) ->
                        val selected = key == iconKey
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color(color).copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .clickable { iconKey = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon, key, Modifier.size(22.dp),
                                tint = if (selected) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text("Renk", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryPalette.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { color = c }
                        )
                    }
                }
                Spacer(Modifier.width(1.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: CategoryEntity(name = "", iconKey = "", colorArgb = 0, isIncome = isIncome))
                            .copy(name = name.trim(), iconKey = iconKey, colorArgb = color)
                    )
                }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
