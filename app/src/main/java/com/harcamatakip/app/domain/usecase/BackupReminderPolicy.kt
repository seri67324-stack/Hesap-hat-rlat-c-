package com.harcamatakip.app.domain.usecase

/** Yerel yedek hatırlatıcısı kuralları. Tüm günler epoch day'dir; [intervalDays] = 0 hatırlatıcıyı kapatır. */
object BackupReminderPolicy {
    const val DEFAULT_INTERVAL_DAYS = 7

    /** Sayımın başladığı gün: son yedek, erteleme veya ilk kurulum gününden en yenisi. */
    fun referenceDay(lastBackupDay: Long?, snoozeDay: Long?, baselineDay: Long?, today: Long): Long =
        maxOf(lastBackupDay ?: Long.MIN_VALUE, snoozeDay ?: Long.MIN_VALUE, baselineDay ?: today)

    fun isDue(intervalDays: Int, today: Long, lastBackupDay: Long?, snoozeDay: Long?, baselineDay: Long?): Boolean {
        if (intervalDays <= 0) return false
        return today - referenceDay(lastBackupDay, snoozeDay, baselineDay, today) >= intervalDays
    }

    /** Bildirim, hatırlatma vadesi geldiğinde ve son bildirimden en az [intervalDays] gün sonra gönderilir. */
    fun shouldNotify(due: Boolean, intervalDays: Int, today: Long, lastNudgeDay: Long?): Boolean =
        due && intervalDays > 0 && (lastNudgeDay == null || today - lastNudgeDay >= intervalDays)

    /** "Ertele": kart yarın yeniden görünür. */
    fun snoozeDayForTomorrow(today: Long, intervalDays: Int): Long = today + 1 - intervalDays

    fun daysSince(lastBackupDay: Long?, today: Long): Long? = lastBackupDay?.let { (today - it).coerceAtLeast(0) }
}
