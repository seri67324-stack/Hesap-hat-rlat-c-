package com.harcamatakip.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harcamatakip.app.core.theme.ExpenseRed
import com.harcamatakip.app.core.theme.IncomeGreen
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.core.util.formatSignedPercent
import com.harcamatakip.app.domain.usecase.CategoryTrend
import kotlin.math.abs

/**
 * Önceki döneme göre değişim rozeti: artış kırmızı (+%12), azalış yeşil (−%8).
 * Önceki dönemde hiç harcama yoksa "Yeni" gösterilir. Renge ek olarak işaret ve erişilebilirlik metni vardır.
 */
@Composable
fun TrendBadge(trend: CategoryTrend?, modifier: Modifier = Modifier) {
    if (trend == null || (trend.current == 0L && trend.previous == 0L)) return
    val pct = trend.changePct
    val (text, color, desc) = when {
        pct == null -> Triple("Yeni", MaterialTheme.colorScheme.onSurfaceVariant, "Önceki dönemde harcama yoktu")
        abs(pct) < 0.5 -> Triple("%0", MaterialTheme.colorScheme.onSurfaceVariant, "Önceki döneme göre değişmedi")
        pct > 0 -> Triple(formatSignedPercent(pct), ExpenseRed, "Önceki döneme göre yüzde ${abs(pct).toInt()} arttı")
        else -> Triple(formatSignedPercent(pct), IncomeGreen, "Önceki döneme göre yüzde ${abs(pct).toInt()} azaldı")
    }
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = desc },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

data class ComparisonRow(
    val name: String,
    val color: Color,
    val current: Long,
    val previous: Long,
    val trend: CategoryTrend?
)

/**
 * Kategori karşılaştırma çubukları: her kategori için bu dönem (dolu) ve önceki dönem (soluk) çubuğu
 * aynı ölçekte yan yana çizilir; sağda değişim rozeti gösterilir.
 */
@Composable
fun ComparisonBars(rows: List<ComparisonRow>, symbol: String, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val maxV = rows.maxOf { maxOf(it.current, it.previous) }.coerceAtLeast(1L)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { r ->
            Column(
                Modifier.semantics {
                    contentDescription = "${r.name}: bu dönem ${r.current.formatMoney(symbol)}, önceki dönem ${r.previous.formatMoney(symbol)}"
                },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(r.current.formatMoney(symbol), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    TrendBadge(r.trend)
                }
                ComparisonBar(r.current.toFloat() / maxV, r.color)
                ComparisonBar(r.previous.toFloat() / maxV, r.color.copy(alpha = 0.35f), height = 6.dp)
                Text(
                    "Önceki: ${r.previous.formatMoney(symbol)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ComparisonBar(fraction: Float, color: Color, height: androidx.compose.ui.unit.Dp = 10.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "cmpBar")
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(animated.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
