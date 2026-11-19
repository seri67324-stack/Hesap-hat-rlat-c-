package com.harcamatakip.app.data.repository

import androidx.room.withTransaction
import com.harcamatakip.app.data.local.AppDatabase
import com.harcamatakip.app.data.local.ExpenseDao
import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.local.QuickTemplateDao
import com.harcamatakip.app.data.local.QuickTemplateEntity
import com.harcamatakip.app.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

enum class TemplateAddResult { ADDED, DUPLICATE, LIMIT }

/** Hızlı işlem şablonları: kaydetme, silme ve tek dokunuşla işlem üretme. */
@Singleton
class TemplateRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: QuickTemplateDao,
    private val expenseDao: ExpenseDao
) {
    companion object {
        const val MAX_TEMPLATES = 24
        const val TITLE_MAX = 30
    }

    fun observeAll(): Flow<List<QuickTemplateEntity>> = dao.observeAll()

    private fun sameCombo(a: QuickTemplateEntity, b: QuickTemplateEntity) =
        a.isIncome == b.isIncome && a.categoryId == b.categoryId && a.amount == b.amount &&
            PaymentMethod.normalize(a.paymentMethod) == PaymentMethod.normalize(b.paymentMethod) &&
            a.note.trim().equals(b.note.trim(), ignoreCase = true)

    /** Aynı kombinasyon varsa eklemez; üst sınıra ulaşıldıysa [TemplateAddResult.LIMIT] döner. */
    suspend fun add(t: QuickTemplateEntity): TemplateAddResult = db.withTransaction {
        val all = dao.getAll()
        when {
            all.any { sameCombo(it, t) } -> TemplateAddResult.DUPLICATE
            all.size >= MAX_TEMPLATES -> TemplateAddResult.LIMIT
            else -> {
                dao.insert(
                    t.copy(
                        id = 0,
                        title = t.title.trim().take(TITLE_MAX),
                        paymentMethod = PaymentMethod.normalize(t.paymentMethod),
                        paymentLabel = PaymentMethod.cleanLabel(t.paymentLabel)
                    )
                )
                TemplateAddResult.ADDED
            }
        }
    }

    suspend fun delete(t: QuickTemplateEntity) = dao.delete(t)

    /** Şablondan [day] tarihli işlem oluşturur ve kullanım sayacını artırır. Yeni işlemin id'sini döner. */
    suspend fun createExpense(t: QuickTemplateEntity, day: Long): Long = db.withTransaction {
        val id = expenseDao.insert(
            ExpenseEntity(
                amount = t.amount,
                isIncome = t.isIncome,
                categoryId = t.categoryId,
                date = day,
                note = t.note,
                paymentMethod = PaymentMethod.normalize(t.paymentMethod),
                paymentLabel = t.paymentLabel
            )
        )
        dao.markUsed(t.id, day)
        id
    }
}
