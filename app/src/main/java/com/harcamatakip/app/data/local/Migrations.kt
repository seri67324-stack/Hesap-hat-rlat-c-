package com.harcamatakip.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Eski tek "Kart" yönteminin dönüştürüleceği yeni yöntem kodu. */
const val LEGACY_CARD_TARGET = "CREDIT_CARD"

/**
 * v1 -> v2:
 *  - expenses / recurring: `paymentLabel` sütunu eklenir; eski "CARD" değeri [LEGACY_CARD_TARGET]'e çevrilir.
 *  - quick_templates (hızlı işlem şablonları) tablosu eklenir.
 *  - budget_alert_state (bütçe uyarısı durumu) tablosu eklenir.
 * Mevcut veriler korunur; hiçbir tablo silinmez.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `paymentLabel` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `paymentLabel` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `expenses` SET `paymentMethod` = '$LEGACY_CARD_TARGET' WHERE `paymentMethod` = 'CARD'")
        db.execSQL("UPDATE `recurring` SET `paymentMethod` = '$LEGACY_CARD_TARGET' WHERE `paymentMethod` = 'CARD'")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quick_templates` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`isIncome` INTEGER NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`paymentMethod` TEXT NOT NULL, " +
                "`paymentLabel` TEXT NOT NULL DEFAULT '', " +
                "`note` TEXT NOT NULL, " +
                "`useCount` INTEGER NOT NULL, " +
                "`lastUsedDay` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quick_templates_categoryId` ON `quick_templates` (`categoryId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget_alert_state` (" +
                "`categoryId` INTEGER NOT NULL, " +
                "`periodKey` TEXT NOT NULL, " +
                "`sent80` INTEGER NOT NULL, " +
                "`sent100` INTEGER NOT NULL, " +
                "PRIMARY KEY(`categoryId`, `periodKey`))"
        )
    }
}
