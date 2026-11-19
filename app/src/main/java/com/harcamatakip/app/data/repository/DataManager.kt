package com.harcamatakip.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.harcamatakip.app.core.util.CategoryPalette
import com.harcamatakip.app.data.local.AppDatabase
import com.harcamatakip.app.data.local.BudgetAlertDao
import com.harcamatakip.app.data.local.BudgetDao
import com.harcamatakip.app.data.local.BudgetEntity
import com.harcamatakip.app.data.local.CategoryDao
import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.data.local.ExpenseDao
import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.local.QuickTemplateDao
import com.harcamatakip.app.data.local.QuickTemplateEntity
import com.harcamatakip.app.data.local.RecurringDao
import com.harcamatakip.app.data.local.RecurringEntity
import com.harcamatakip.app.data.settings.SettingsRepository
import com.harcamatakip.app.domain.model.PaymentMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val imported: Int, val errors: List<String>)

/** CSV dışa/içe aktarma, JSON yedekleme/geri yükleme ve tüm verileri silme. */
@Singleton
class DataManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val recurringDao: RecurringDao,
    private val templateDao: QuickTemplateDao,
    private val alertDao: BudgetAlertDao,
    private val categoryRepo: CategoryRepository,
    private val settings: SettingsRepository
) {

    // ---------------------------------------------------------------- CSV

    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val cats = categoryDao.getAll().associateBy { it.id }
        val list = expenseDao.getAll().sortedWith(compareBy({ it.date }, { it.id }))
        val sb = StringBuilder()
        sb.append('﻿') // Excel Türkçe karakterleri doğru okusun
        sb.append("Tarih;Tür;Tutar;Kategori;Not;Ödeme;Etiket\n")
        for (e in list) {
            val amount = String.format(java.util.Locale.US, "%.2f", e.amount / 100.0)
            sb.append(LocalDate.ofEpochDay(e.date)).append(';')
                .append(if (e.isIncome) "Gelir" else "Gider").append(';')
                .append(amount).append(';')
                .append(csvEscape(cats[e.categoryId]?.name ?: "Diğer")).append(';')
                .append(csvEscape(e.note)).append(';')
                .append(PaymentMethod.label(e.paymentMethod)).append(';')
                .append(csvEscape(e.paymentLabel)).append('\n')
        }
        writeText(uri, sb.toString())
        list.size
    }

    suspend fun importCsv(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = readText(uri).removePrefix("﻿")
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val sep = if (firstLine.contains(';')) ';' else ','
        val rows = parseCsv(text, sep)

        val errors = mutableListOf<String>()
        val categories = categoryDao.getAll().toMutableList()
        val toInsert = mutableListOf<ExpenseEntity>()

        // 0. satır başlık
        for ((index, row) in rows.withIndex()) {
            if (index == 0) continue
            val lineNo = index + 1
            if (row.size < 4) {
                errors += "Satır $lineNo: en az 4 sütun gerekli"
                continue
            }
            val date = parseDate(row[0].trim())
            if (date == null) { errors += "Satır $lineNo: tarih okunamadı (${row[0]})"; continue }
            val typeText = row[1].trim().lowercase()
            val isIncome = when (typeText) {
                "gelir", "income" -> true
                "gider", "expense" -> false
                else -> { errors += "Satır $lineNo: tür 'Gelir' veya 'Gider' olmalı"; continue }
            }
            val amount = row[2].trim().replace(',', '.').toBigDecimalOrNull()
            if (amount == null || amount.signum() <= 0) {
                errors += "Satır $lineNo: tutar geçersiz (${row[2]})"
                continue
            }
            val kurus = amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()

            val catName = row[3].trim().ifBlank { if (isIncome) "Diğer Gelir" else "Diğer" }
            var cat = categories.firstOrNull { it.isIncome == isIncome && it.name.equals(catName, true) }
            if (cat == null) {
                val fresh = CategoryEntity(
                    name = catName, iconKey = "category",
                    colorArgb = CategoryPalette[categories.size % CategoryPalette.size],
                    isIncome = isIncome
                )
                val id = categoryDao.insert(fresh)
                cat = fresh.copy(id = id)
                categories += cat
            }
            val note = row.getOrNull(4)?.trim().orEmpty()
            // "Nakit" / "Banka Kartı" / "Kredi Kartı"; eski dosyalardaki "Kart" kredi kartına eşlenir
            val payment = PaymentMethod.fromText(row.getOrNull(5)) ?: PaymentMethod.CASH
            val label = if (payment == PaymentMethod.CASH) "" else PaymentMethod.cleanLabel(row.getOrNull(6).orEmpty())
            toInsert += ExpenseEntity(
                amount = kurus, isIncome = isIncome, categoryId = cat.id,
                date = date.toEpochDay(), note = note, paymentMethod = payment, paymentLabel = label
            )
        }
        expenseDao.insertAll(toInsert)
        ImportResult(toInsert.size, errors)
    }

    private fun parseDate(s: String): LocalDate? {
        val formats = listOf("yyyy-MM-dd", "dd.MM.yyyy", "d.M.yyyy", "dd/MM/yyyy", "d/M/yyyy")
        for (f in formats) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(f))
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun csvEscape(s: String): String {
        val clean = s.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(';') || clean.contains('"') || clean.contains(',')) {
            "\"" + clean.replace("\"", "\"\"") + "\""
        } else clean
    }

    private fun parseCsv(text: String, sep: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    sb.append(c)
                }
            } else if (c == '"') {
                inQuotes = true
            } else if (c == sep) {
                row.add(sb.toString())
                sb.clear()
            } else if (c == '\n') {
                row.add(sb.toString())
                sb.clear()
                rows.add(row)
                row = mutableListOf()
            } else if (c != '\r') {
                sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    // ------------------------------------------------------ JSON yedekleme

    suspend fun backup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val expenses = expenseDao.getAll()
        val root = JSONObject()
            .put("version", 2)
            .put("exportedAt", LocalDate.now().toString())
            .put("categories", JSONArray(categoryDao.getAll().map { it.toJson() }))
            .put("expenses", JSONArray(expenses.map { it.toJson() }))
            .put("budgets", JSONArray(budgetDao.getAll().map { it.toJson() }))
            .put("recurring", JSONArray(recurringDao.getAll().map { it.toJson() }))
            .put("templates", JSONArray(templateDao.getAll().map { it.toJson() }))
        writeText(uri, root.toString())
        // Yedek hatırlatıcısı için son yedek günü (yalnızca yazma başarılı olduktan sonra)
        settings.markBackupDone(LocalDate.now().toEpochDay())
        expenses.size
    }

    /** Dosya önce tamamen okunup doğrulanır; geçersizse mevcut veriye dokunulmaz. */
    suspend fun restore(uri: Uri): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(readText(uri))
        require(root.has("expenses") && root.has("categories")) { "Bu bir Harcama Takip yedeği değil" }

        val categories = root.getJSONArray("categories").map { it.toCategory() }
        val expenses = root.getJSONArray("expenses").map { it.toExpense() }
        val budgets = root.optJSONArray("budgets")?.map { it.toBudget() } ?: emptyList()
        val recurring = root.optJSONArray("recurring")?.map { it.toRecurring() } ?: emptyList()
        val templates = root.optJSONArray("templates")?.map { it.toTemplate() } ?: emptyList() // v1 yedeğinde yok

        db.withTransaction {
            expenseDao.deleteAll()
            recurringDao.deleteAll()
            budgetDao.deleteAll()
            templateDao.deleteAll()
            alertDao.deleteAll()
            categoryDao.deleteAll()
            categoryDao.insertAll(categories)
            expenseDao.insertAll(expenses)
            budgetDao.insertAll(budgets)
            recurringDao.insertAll(recurring)
            templateDao.insertAll(templates)
        }
        categoryRepo.ensureDefaults()
        expenses.size
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.withTransaction {
            expenseDao.deleteAll()
            recurringDao.deleteAll()
            budgetDao.deleteAll()
            templateDao.deleteAll()
            alertDao.deleteAll()
            categoryDao.deleteAll()
        }
        categoryRepo.ensureDefaults()
    }

    // ------------------------------------------------------------- yardımcılar

    private fun writeText(uri: Uri, text: String) {
        val out = ctx.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Dosya açılamadı")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun readText(uri: Uri): String {
        val input = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Dosya açılamadı")
        return input.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun <T> JSONArray.map(block: (JSONObject) -> T): List<T> =
        (0 until length()).map { block(getJSONObject(it)) }

    private fun CategoryEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("iconKey", iconKey)
        .put("colorArgb", colorArgb).put("isIncome", isIncome).put("isFallback", isFallback)

    private fun JSONObject.toCategory() = CategoryEntity(
        id = getLong("id"), name = getString("name"), iconKey = optString("iconKey", "category"),
        colorArgb = getLong("colorArgb"), isIncome = getBoolean("isIncome"),
        isFallback = optBoolean("isFallback", false)
    )

    private fun ExpenseEntity.toJson() = JSONObject()
        .put("id", id).put("amount", amount).put("isIncome", isIncome)
        .put("categoryId", categoryId).put("date", date).put("note", note)
        .put("payment", paymentMethod).put("paymentLabel", paymentLabel)
        .put("recurringId", recurringId ?: JSONObject.NULL)

    private fun JSONObject.toExpense() = ExpenseEntity(
        id = getLong("id"), amount = getLong("amount"), isIncome = getBoolean("isIncome"),
        categoryId = getLong("categoryId"), date = getLong("date"), note = optString("note", ""),
        // Eski yedeklerdeki "CARD" burada yeni koda çevrilir
        paymentMethod = PaymentMethod.normalize(optString("payment", "CASH")),
        paymentLabel = optString("paymentLabel", ""),
        recurringId = if (isNull("recurringId")) null else getLong("recurringId")
    )

    private fun BudgetEntity.toJson() = JSONObject()
        .put("id", id).put("categoryId", categoryId).put("limitAmount", limitAmount)

    private fun JSONObject.toBudget() = BudgetEntity(
        id = getLong("id"), categoryId = getLong("categoryId"), limitAmount = getLong("limitAmount")
    )

    private fun RecurringEntity.toJson() = JSONObject()
        .put("id", id).put("amount", amount).put("isIncome", isIncome)
        .put("categoryId", categoryId).put("note", note).put("payment", paymentMethod)
        .put("paymentLabel", paymentLabel).put("frequency", frequency).put("startDay", startDay)
        .put("nextDueDay", nextDueDay).put("active", active)

    private fun JSONObject.toRecurring() = RecurringEntity(
        id = getLong("id"), amount = getLong("amount"), isIncome = getBoolean("isIncome"),
        categoryId = getLong("categoryId"), note = optString("note", ""),
        paymentMethod = PaymentMethod.normalize(optString("payment", "CASH")),
        paymentLabel = optString("paymentLabel", ""), frequency = getString("frequency"),
        startDay = getLong("startDay"), nextDueDay = getLong("nextDueDay"),
        active = optBoolean("active", true)
    )

    private fun QuickTemplateEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("amount", amount).put("isIncome", isIncome)
        .put("categoryId", categoryId).put("payment", paymentMethod).put("paymentLabel", paymentLabel)
        .put("note", note).put("useCount", useCount).put("lastUsedDay", lastUsedDay)

    private fun JSONObject.toTemplate() = QuickTemplateEntity(
        id = getLong("id"), title = getString("title"), amount = getLong("amount"),
        isIncome = getBoolean("isIncome"), categoryId = getLong("categoryId"),
        paymentMethod = PaymentMethod.normalize(optString("payment", "CASH")),
        paymentLabel = optString("paymentLabel", ""), note = optString("note", ""),
        useCount = optInt("useCount", 0), lastUsedDay = optLong("lastUsedDay", 0)
    )
}
