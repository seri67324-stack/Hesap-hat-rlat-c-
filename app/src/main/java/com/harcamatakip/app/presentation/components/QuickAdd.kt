package com.harcamatakip.app.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harcamatakip.app.core.theme.ExpenseRed
import com.harcamatakip.app.core.theme.IncomeGreen
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.data.local.QuickTemplateEntity
import com.harcamatakip.app.domain.usecase.ComboSuggestion

data class TemplateUi(val template: QuickTemplateEntity, val category: CategoryEntity?)
data class SuggestionUi(val suggestion: ComboSuggestion, val category: CategoryEntity?)

/**
 * "Hızlı ekle" bölümü: şablon çipine dokununca bugünün tarihiyle işlem anında eklenir (geri alınabilir),
 * uzun basınca şablon silinir. "Sık yaptıkların" çiplerinde yer işareti simgesi şablon olarak kaydeder.
 */
@Composable
fun QuickAddSection(
    templates: List<TemplateUi>,
    suggestions: List<SuggestionUi>,
    symbol: String,
    onTemplateClick: (QuickTemplateEntity) -> Unit,
    onTemplateLongClick: (QuickTemplateEntity) -> Unit,
    onSuggestionClick: (ComboSuggestion) -> Unit,
    onSuggestionSave: (ComboSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    if (templates.isEmpty() && suggestions.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Hızlı ekle")
        if (templates.isNotEmpty()) {
            Text(
                "Dokun: bugün için hemen ekle · Uzun bas: şablonu sil",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(templates, key = { it.template.id }) { t ->
                    QuickChip(
                        title = t.template.title,
                        amount = t.template.amount.formatMoney(symbol),
                        category = t.category,
                        isIncome = t.template.isIncome,
                        onClick = { onTemplateClick(t.template) },
                        onLongClick = { onTemplateLongClick(t.template) }
                    )
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            Text(
                "Sık yaptıkların · yer işaretiyle şablon yap",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions, key = { s -> with(s.suggestion) { "$isIncome|$categoryId|$amount|$paymentMethod|$note" } }) { s ->
                    QuickChip(
                        title = s.suggestion.note.ifBlank { s.category?.name ?: "İşlem" },
                        amount = "${s.suggestion.amount.formatMoney(symbol)} · ${s.suggestion.count}×",
                        category = s.category,
                        isIncome = s.suggestion.isIncome,
                        onClick = { onSuggestionClick(s.suggestion) },
                        onLongClick = null,
                        trailing = {
                            Icon(
                                Icons.Rounded.BookmarkAdd,
                                contentDescription = "Şablon olarak kaydet",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onSuggestionSave(s.suggestion) }
                                    .padding(8.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickChip(
    title: String,
    amount: String,
    category: CategoryEntity?,
    isIncome: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 10.dp, end = if (trailing != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(category, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(112.dp)
            )
            Text(
                (if (isIncome) "+" else "−") + amount,
                style = MaterialTheme.typography.labelMedium,
                color = if (isIncome) IncomeGreen else ExpenseRed,
                maxLines = 1
            )
        }
        if (trailing != null) trailing()
    }
}
