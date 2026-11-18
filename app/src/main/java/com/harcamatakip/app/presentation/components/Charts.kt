package com.harcamatakip.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harcamatakip.app.core.util.fmt
import com.harcamatakip.app.core.util.formatCompact
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.domain.usecase.MonthTotal
import com.harcamatakip.app.domain.usecase.TrendPoint
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

data class DonutSlice(val value: Float, val color: Color)

/** Animasyonlu halka grafik. Dilime dokununca seçilir. */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    description: String = "",
    center: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)

    Box(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description }
            .pointerInput(slices, selected) {
                detectTapGestures { offset ->
                    val minDim = min(size.width, size.height).toFloat()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = offset.x - cx
                    val dy = offset.y - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    // Çizimdekiyle aynı geometri: halkanın tam iç ve dış yarıçapı
                    val stroke = minDim * 0.18f
                    val radius = minDim / 2f - stroke * 0.6f
                    val inner = radius - stroke * 0.6f
                    val outer = radius + stroke * 0.6f
                    if (dist < inner || dist > outer) {
                        onSelect(null)
                        return@detectTapGestures
                    }
                    // 0° yukarıda başlar, saat yönünde
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (angle < 0f) angle += 360f
                    var acc = 0f
                    slices.forEachIndexed { i, s ->
                        val sweep = s.value / total * 360f
                        if (angle >= acc && angle < acc + sweep) {
                            onSelect(if (selected == i) null else i)
                        }
                        acc += sweep
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.18f
            val inset = stroke * 0.6f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            var start = -90f
            val gap = if (slices.size > 1) 1.5f else 0f
            slices.forEachIndexed { i, s ->
                val sweep = s.value / total * 360f * progress.value
                val width = if (selected == i) stroke * 1.15f else stroke
                drawArc(
                    color = s.color.copy(alpha = if (selected == null || selected == i) 1f else 0.45f),
                    startAngle = start,
                    sweepAngle = max(sweep - gap, 0.1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = width)
                )
                start += sweep
            }
        }
        center()
    }
}

private val AxisWidth = 44.dp

private fun java.time.YearMonth.fmt(pattern: String): String =
    this.atDay(1).fmt(pattern)

/**
 * Eksen için "yuvarlak" adım (1, 2, 2.5, 5, 10 x 10^n). Değerler kuruş cinsindendir.
 * Veri yoksa (max = 0) eksen 0..400 arası makul bir ölçekte çizilir; "0 0 0 0 0" etiketi çıkmaz.
 */
private fun niceStep(max: Long, ticks: Int): Long {
    val basis = if (max <= 0L) 40_000L else max
    val raw = basis.toDouble() / ticks
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val nice = when {
        norm <= 1.0 -> 1.0
        norm <= 2.0 -> 2.0
        norm <= 2.5 -> 2.5
        norm <= 5.0 -> 5.0
        else -> 10.0
    }
    return (nice * mag).roundToLong().coerceAtLeast(1L)
}

/** Yatay kılavuz çizgileri + sol tarafta kısaltılmış tutar etiketleri (1,5 B / 2 Mn). */
private fun DrawScope.drawYAxis(
    measurer: TextMeasurer,
    step: Long,
    ticks: Int,
    left: Float,
    gridColor: Color,
    labelColor: Color
) {
    val style = TextStyle(fontSize = 10.sp, color = labelColor)
    for (i in 0..ticks) {
        val y = size.height * (1f - i / ticks.toFloat())
        drawLine(gridColor, Offset(left, y), Offset(size.width, y), 1.dp.toPx())
        val layout = measurer.measure((step * i).formatCompact(), style)
        val ty = (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
        drawText(layout, topLeft = Offset(left - 6.dp.toPx() - layout.size.width, ty))
    }
}

private fun pickIndex(x: Float, left: Float, width: Float, n: Int): Int =
    (((x - left) / (width - left)) * (n - 1)).roundToInt().coerceIn(0, n - 1)

/** Çubuğun üstüne çapraz çizgi deseni: gider çubuğu renk körü kullanıcılar için de ayırt edilir. */
private fun DrawScope.drawStripes(x: Float, top: Float, w: Float, h: Float, radius: CornerRadius, color: Color) {
    if (h <= 1f) return
    val path = Path().apply { addRoundRect(RoundRect(x, top, x + w, top + h, radius)) }
    clipPath(path) {
        val gap = 7.dp.toPx()
        var y = top - w
        while (y < top + h) {
            drawLine(color, Offset(x, y + w), Offset(x + w, y), strokeWidth = 1.5.dp.toPx())
            y += gap
        }
    }
}

/** Aylık gelir/gider çubuk grafiği. Aya dokununca gelir, gider ve net tutar gösterilir. */
@Composable
fun BarChart(
    data: List<MonthTotal>,
    symbol: String,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    var selected by remember(data) { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val ticks = 4
    val step = niceStep(data.maxOf { max(it.income, it.expense) }, ticks)
    val axisMax = (step * ticks).toFloat()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val netColor = MaterialTheme.colorScheme.onSurface
    val negativeColor = MaterialTheme.colorScheme.error
    val description = "Aylık gelir, gider ve net grafiği. " + data.joinToString(". ") {
        "${it.ym.fmt("MMMM yyyy")}: gelir ${it.income.formatMoney(symbol)}, gider ${it.expense.formatMoney(symbol)}, " +
            "net ${(it.income - it.expense).formatMoney(symbol)}"
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val sel = selected?.let { data.getOrNull(it) }
        Text(
            if (sel == null) "Detay için bir aya dokun"
            else "${sel.ym.fmt("MMMM yyyy")}\nGelir ${sel.income.formatMoney(symbol)} · " +
                "Gider ${sel.expense.formatMoney(symbol)} · Net ${(sel.income - sel.expense).formatMoney(symbol)}",
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            minLines = 2,
            maxLines = 2
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = description }
                .pointerInput(data) {
                    detectTapGestures { o ->
                        val left = AxisWidth.toPx()
                        val slot = (size.width - left) / data.size
                        val idx = ((o.x - left) / slot).toInt()
                        selected = if (o.x < left || idx !in data.indices || idx == selected) null else idx
                    }
                }
        ) {
            val left = AxisWidth.toPx()
            drawYAxis(measurer, step, ticks, left, gridColor, labelColor)
            val slot = (size.width - left) / data.size
            data.forEachIndexed { i, m ->
                val dim = if (selected == null || selected == i) 1f else 0.4f
                if (selected == i) {
                    drawRoundRect(
                        labelColor.copy(alpha = 0.12f),
                        topLeft = Offset(left + slot * i, 0f),
                        size = Size(slot, size.height),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                }
                val barW = slot * 0.32f
                val gap = slot * 0.04f
                val x0 = left + slot * i + slot / 2f - barW - gap / 2f
                val hIncome = m.income / axisMax * size.height * progress.value
                val hExpense = m.expense / axisMax * size.height * progress.value
                val radius = CornerRadius(5.dp.toPx())
                drawRoundRect(
                    incomeColor.copy(alpha = dim),
                    topLeft = Offset(x0, size.height - hIncome),
                    size = Size(barW, hIncome),
                    cornerRadius = radius
                )
                val xE = x0 + barW + gap
                drawRoundRect(
                    expenseColor.copy(alpha = dim),
                    topLeft = Offset(xE, size.height - hExpense),
                    size = Size(barW, hExpense),
                    cornerRadius = radius
                )
                drawStripes(xE, size.height - hExpense, barW, hExpense, radius, Color.White.copy(alpha = 0.4f * dim))
            }

            // Net çizgisi (gelir - gider). Eksi aylarda nokta tabana oturur ve içi boş halka olarak çizilir.
            val netPath = Path()
            data.forEachIndexed { i, m ->
                val cx = left + slot * i + slot / 2f
                val cy = size.height - (max(m.income - m.expense, 0L) / axisMax) * size.height * progress.value
                if (i == 0) netPath.moveTo(cx, cy) else netPath.lineTo(cx, cy)
            }
            if (data.size > 1) {
                drawPath(
                    netPath, netColor.copy(alpha = 0.8f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            data.forEachIndexed { i, m ->
                val net = m.income - m.expense
                val center = Offset(
                    left + slot * i + slot / 2f,
                    size.height - (max(net, 0L) / axisMax) * size.height * progress.value
                )
                if (net >= 0) {
                    drawCircle(netColor, 3.5.dp.toPx(), center)
                } else {
                    drawCircle(negativeColor, 4.dp.toPx(), center, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = AxisWidth)) {
            data.forEachIndexed { i, m ->
                // 12 ay görünümünde etiketler sıkışmasın: her ikinci etiket gösterilir
                val label = if (data.size > 8 && i % 2 == 1) "" else m.ym.fmt("MMM")
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1
                )
            }
        }
    }
}

private enum class Granularity { DAY, WEEK, MONTH }

private fun granularity(points: List<TrendPoint>): Granularity = when {
    points.all { it.start == it.end } -> Granularity.DAY
    points.all { ChronoUnit.DAYS.between(it.start, it.end) <= 6 } -> Granularity.WEEK
    else -> Granularity.MONTH
}

private fun pointLabel(p: TrendPoint, g: Granularity): String = when (g) {
    Granularity.DAY -> p.start.fmt("d MMMM, EEEE")
    Granularity.WEEK -> "${p.start.fmt("d MMM")} – ${p.end.fmt("d MMM")}"
    Granularity.MONTH -> p.start.fmt("MMMM yyyy")
}

/**
 * Harcama trend çizgisi: günlük / haftalık / aylık noktalar (dönem uzunluğuna göre),
 * ortalama (kesikli çizgi), en yüksek nokta işareti, isteğe bağlı önceki dönem çizgisi.
 * Dokunarak seçilir (aynı noktaya tekrar dokunmak seçimi kaldırır), sürükleyerek gezilir.
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    previous: List<Long>?,
    symbol: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
    }
    val gran = granularity(points)
    val ticks = 4
    val maxValue = points.maxOf { it.value }
    val prevMax = previous?.maxOrNull() ?: 0L
    val step = niceStep(max(maxValue, prevMax), ticks)
    val axisMax = (step * ticks).toFloat()
    val avg = points.sumOf { it.value } / points.size
    val peakIdx = points.indices.maxByOrNull { points[it].value } ?: 0
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val measurer = rememberTextMeasurer()
    val avgName = when (gran) {
        Granularity.DAY -> "Günlük ortalama"
        Granularity.WEEK -> "Haftalık ortalama"
        Granularity.MONTH -> "Aylık ortalama"
    }
    val description = "Harcama trend grafiği. En yüksek: ${pointLabel(points[peakIdx], gran)}, " +
        "${maxValue.formatMoney(symbol)}. $avgName: ${avg.formatMoney(symbol)}."

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val sel = selected
        val header = when {
            sel != null -> {
                val p = points[sel]
                val prev = previous?.getOrNull(sel)
                pointLabel(p, gran) + ": " + p.value.formatMoney(symbol) +
                    if (prev != null && gran == Granularity.DAY) "\nÖnceki dönem: ${prev.formatMoney(symbol)}" else ""
            }
            maxValue > 0 ->
                "En yüksek: ${pointLabel(points[peakIdx], gran)} · ${maxValue.formatMoney(symbol)}\n" +
                    "$avgName: ${avg.formatMoney(symbol)}"
            else -> "Bu dönemde harcama yok"
        }
        Text(
            header,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            minLines = 2,
            maxLines = 2
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { o ->
                        val left = AxisWidth.toPx()
                        val idx = pickIndex(o.x, left, size.width.toFloat(), points.size)
                        selected = if (o.x < left || idx == selected) null else idx
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { o ->
                            selected = pickIndex(o.x, AxisWidth.toPx(), size.width.toFloat(), points.size)
                        },
                        onHorizontalDrag = { change, _ ->
                            selected = pickIndex(change.position.x, AxisWidth.toPx(), size.width.toFloat(), points.size)
                        }
                    )
                }
        ) {
            val left = AxisWidth.toPx()
            val plotW = size.width - left
            drawYAxis(measurer, step, ticks, left, gridColor, labelColor)

            val stepX = plotW / (points.size - 1)
            fun xOf(i: Int) = left + stepX * i
            fun yOf(v: Long) = size.height - (v / axisMax) * size.height

            val line = Path()
            val fill = Path()
            points.forEachIndexed { i, p ->
                val x = xOf(i)
                val y = yOf(p.value)
                if (i == 0) {
                    line.moveTo(x, y)
                    fill.moveTo(x, size.height)
                    fill.lineTo(x, y)
                } else {
                    val px = xOf(i - 1)
                    val py = yOf(points[i - 1].value)
                    val cx = (px + x) / 2f
                    line.cubicTo(cx, py, cx, y, x, y)
                    fill.cubicTo(cx, py, cx, y, x, y)
                }
            }
            fill.lineTo(xOf(points.size - 1), size.height)
            fill.close()

            clipRect(left = left, right = left + plotW * progress.value) {
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
                drawPath(
                    line, color,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Önceki dönem: soluk kesikli çizgi (yalnızca günlük görünümde)
                if (previous != null && gran == Granularity.DAY) {
                    val pp = Path()
                    var started = false
                    for (i in 0 until min(previous.size, points.size)) {
                        val x = xOf(i)
                        val y = yOf(previous[i])
                        if (!started) {
                            pp.moveTo(x, y)
                            started = true
                        } else {
                            pp.lineTo(x, y)
                        }
                    }
                    if (started) {
                        drawPath(
                            pp, labelColor.copy(alpha = 0.6f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                            )
                        )
                    }
                }
            }

            if (avg > 0) {
                val ay = yOf(avg)
                drawLine(
                    color.copy(alpha = 0.7f), Offset(left, ay), Offset(size.width, ay),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
            }
            if (points.size <= 31) {
                points.forEachIndexed { i, p ->
                    if (p.value > 0) {
                        drawCircle(color.copy(alpha = progress.value), 2.5.dp.toPx(), Offset(xOf(i), yOf(p.value)))
                    }
                }
            }
            val s = selected
            if (s == null && maxValue > 0) {
                drawCircle(color, 5.dp.toPx(), Offset(xOf(peakIdx), yOf(maxValue)))
            }
            s?.let { i ->
                val x = xOf(i)
                drawLine(color.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                drawCircle(color, 6.dp.toPx(), Offset(x, yOf(points[i].value)))
                drawCircle(surface, 3.dp.toPx(), Offset(x, yOf(points[i].value)))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = AxisWidth),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labels = if (points.size >= 3) {
                listOf(points.first(), points[points.size / 2], points.last())
            } else {
                listOf(points.first(), points.last())
            }
            labels.forEach {
                val text = if (gran == Granularity.MONTH) it.start.fmt("MMM") else it.start.fmt("d MMM")
                Text(text, style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
    }
}

/**
 * Birikimli (kümülatif) harcama: ay içinde harcamanın nasıl biriktiğini, bütçe çizgisini
 * (varsa) ve ay sonu tahmininin kesikli devamını gösterir.
 */
@Composable
fun CumulativeChart(
    actual: List<Long>,
    totalDays: Int,
    start: LocalDate,
    budget: Long?,
    projected: Long?,
    symbol: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (actual.isEmpty() || totalDays < 2) return
    var selected by remember(actual) { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(actual) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
    }
    val ticks = 4
    val top = max(actual.last(), max(budget ?: 0L, projected ?: 0L))
    val step = niceStep(top, ticks)
    val axisMax = (step * ticks).toFloat()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val danger = MaterialTheme.colorScheme.error
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val measurer = rememberTextMeasurer()
    val description = "Birikimli harcama grafiği. Şu ana kadar ${actual.last().formatMoney(symbol)}" +
        (budget?.let { ", bütçe ${it.formatMoney(symbol)}" } ?: "") +
        (projected?.let { ", ay sonu tahmini ${it.formatMoney(symbol)}" } ?: "") + "."

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val sel = selected
        val header = if (sel != null) {
            val v = actual[sel]
            start.plusDays(sel.toLong()).fmt("d MMMM") + ": toplam " + v.formatMoney(symbol) +
                if (budget != null && budget > 0) " · bütçenin %${v * 100 / budget}'i" else ""
        } else {
            "Şu ana kadar: ${actual.last().formatMoney(symbol)}" +
                (budget?.let { " · Bütçe: ${it.formatMoney(symbol)}" } ?: "") +
                (projected?.let { "\nAy sonu tahmini: ${it.formatMoney(symbol)}" } ?: "\n")
        }
        Text(
            header,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            minLines = 2,
            maxLines = 2
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = description }
                .pointerInput(actual, totalDays) {
                    detectTapGestures { o ->
                        val left = AxisWidth.toPx()
                        val idx = pickIndex(o.x, left, size.width.toFloat(), totalDays).coerceAtMost(actual.size - 1)
                        selected = if (o.x < left || idx == selected) null else idx
                    }
                }
                .pointerInput(actual, totalDays) {
                    detectHorizontalDragGestures(
                        onDragStart = { o ->
                            selected = pickIndex(o.x, AxisWidth.toPx(), size.width.toFloat(), totalDays)
                                .coerceAtMost(actual.size - 1)
                        },
                        onHorizontalDrag = { change, _ ->
                            selected = pickIndex(change.position.x, AxisWidth.toPx(), size.width.toFloat(), totalDays)
                                .coerceAtMost(actual.size - 1)
                        }
                    )
                }
        ) {
            val left = AxisWidth.toPx()
            val plotW = size.width - left
            drawYAxis(measurer, step, ticks, left, gridColor, labelColor)
            val stepX = plotW / (totalDays - 1)
            fun xOf(i: Int) = left + stepX * i
            fun yOf(v: Long) = size.height - (v / axisMax) * size.height

            if (budget != null && budget > 0) {
                drawLine(
                    danger, Offset(left, yOf(budget)), Offset(size.width, yOf(budget)),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
            }

            val line = Path()
            val fill = Path()
            actual.forEachIndexed { i, v ->
                val x = xOf(i)
                val y = yOf(v)
                if (i == 0) {
                    line.moveTo(x, y)
                    fill.moveTo(x, size.height)
                    fill.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(xOf(actual.size - 1), size.height)
            fill.close()

            clipRect(left = left, right = left + plotW * progress.value) {
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
                drawPath(
                    line, color,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            if (projected != null && actual.size < totalDays) {
                val from = Offset(xOf(actual.size - 1), yOf(actual.last()))
                val to = Offset(xOf(totalDays - 1), yOf(projected))
                drawLine(
                    color.copy(alpha = 0.7f), from, to,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
                drawCircle(color.copy(alpha = 0.7f), 4.dp.toPx(), to)
            }

            val s = selected
            val markIdx = s ?: (actual.size - 1)
            val mx = xOf(markIdx)
            if (s != null) {
                drawLine(color.copy(alpha = 0.4f), Offset(mx, 0f), Offset(mx, size.height), 1.dp.toPx())
            }
            drawCircle(color, 6.dp.toPx(), Offset(mx, yOf(actual[markIdx])))
            drawCircle(surface, 3.dp.toPx(), Offset(mx, yOf(actual[markIdx])))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = AxisWidth),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(start.fmt("d MMM"), style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text(
                start.plusDays((totalDays - 1).toLong()).fmt("d MMM"),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
        }
    }
}

/** Basit, etiketli çubuk grafik (son 7 gün, haftanın günleri, kategori aylık trendi için). */
@Composable
fun SimpleBarChart(
    values: List<Long>,
    labels: List<String>,
    symbol: String,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    header: String = "Detay için bir çubuğa dokun"
) {
    if (values.isEmpty()) return
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val maxV = max(values.maxOrNull() ?: 0L, 1L)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = labels.zip(values).joinToString(". ") { (l, v) -> "$l: ${v.formatMoney(symbol)}" }

    Column(modifier.semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val sel = selected
        Text(
            if (sel != null) "${labels.getOrNull(sel) ?: ""}: ${values[sel].formatMoney(symbol)}" else header,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 2
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(values) {
                    detectTapGestures { o ->
                        val slot = size.width / values.size
                        val idx = (o.x / slot).toInt()
                        selected = if (idx !in values.indices || idx == selected) null else idx
                    }
                }
        ) {
            val slot = size.width / values.size
            val barW = slot * 0.5f
            values.forEachIndexed { i, v ->
                val h = v.toFloat() / maxV * size.height * progress.value
                val dim = if (selected == null || selected == i) 1f else 0.4f
                if (h > 0f) {
                    drawRoundRect(
                        color.copy(alpha = dim),
                        topLeft = Offset(slot * i + (slot - barW) / 2f, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(5.dp.toPx())
                    )
                }
            }
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1
                )
            }
        }
    }
}
