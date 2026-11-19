package com.harcamatakip.app.domain

import com.harcamatakip.app.domain.usecase.BackupReminderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderPolicyTest {

    @Test
    fun disabled_isNeverDue() {
        assertFalse(BackupReminderPolicy.isDue(0, today = 1000, lastBackupDay = null, snoozeDay = null, baselineDay = 1))
    }

    @Test
    fun neverBackedUp_dueAfterIntervalSinceBaseline() {
        assertFalse(BackupReminderPolicy.isDue(7, 106, null, null, 100))
        assertTrue(BackupReminderPolicy.isDue(7, 107, null, null, 100))
    }

    @Test
    fun missingBaseline_isNotDueYet() {
        assertFalse(BackupReminderPolicy.isDue(7, 500, null, null, null))
    }

    @Test
    fun lastBackupResetsTheClock() {
        assertFalse(BackupReminderPolicy.isDue(7, 116, lastBackupDay = 110, snoozeDay = null, baselineDay = 100))
        assertTrue(BackupReminderPolicy.isDue(7, 117, lastBackupDay = 110, snoozeDay = null, baselineDay = 100))
    }

    @Test
    fun snoozeHidesUntilTomorrow() {
        val today = 107L
        assertTrue(BackupReminderPolicy.isDue(7, today, null, null, 100))
        val snooze = BackupReminderPolicy.snoozeDayForTomorrow(today, 7)
        assertFalse(BackupReminderPolicy.isDue(7, today, null, snooze, 100))
        assertTrue(BackupReminderPolicy.isDue(7, today + 1, null, snooze, 100))
    }

    @Test
    fun notify_respectsIntervalBetweenNudges() {
        assertTrue(BackupReminderPolicy.shouldNotify(true, 7, 107, null))
        assertFalse(BackupReminderPolicy.shouldNotify(true, 7, 110, lastNudgeDay = 107))
        assertTrue(BackupReminderPolicy.shouldNotify(true, 7, 114, lastNudgeDay = 107))
        assertFalse(BackupReminderPolicy.shouldNotify(false, 7, 114, null))
        assertFalse(BackupReminderPolicy.shouldNotify(true, 0, 114, null))
    }

    @Test
    fun daysSince() {
        assertEquals(5L, BackupReminderPolicy.daysSince(100, 105))
        assertNull(BackupReminderPolicy.daysSince(null, 105))
        assertEquals(0L, BackupReminderPolicy.daysSince(110, 105)) // saat geri alınsa bile negatif olmaz
    }
}
