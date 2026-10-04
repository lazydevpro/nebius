package `in`.family.maa

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** No AlarmManager, communication, audio or live notification side effects. */
class DailyToolsTest {
    @Test fun lifecycleRefreshKeepsDelayedOccurrenceRatherThanSkippingToday() {
        val now=Instant.parse("2026-10-04T10:00:00Z")
        val due=now.minusSeconds(60)
        val alarm=MaaAlarm(hour=9,minute=59,scheduledAt=due.toEpochMilli())
        assertEquals(due,MaaAlarmClock.reconcileOccurrence(alarm,now,ZoneId.of("UTC")))
        assertEquals(Instant.parse("2026-10-05T09:59:00Z"),MaaAlarmClock.reconcileOccurrence(alarm,now,ZoneId.of("UTC"),true))
        assertNull(MaaAlarmClock.reconcileOccurrence(alarm.copy(enabled=false),now,ZoneId.of("UTC")))
    }
    @Test fun onceAlarmRollsToTomorrowWithoutRepeatingDays() {
        val now = Instant.parse("2026-10-04T10:00:00Z")
        assertEquals(Instant.parse("2026-10-05T07:00:00Z"), MaaAlarmClock.next(MaaAlarm(hour = 7), now, ZoneId.of("UTC")))
        assertNull(MaaAlarmClock.next(MaaAlarm(enabled = false), now, ZoneId.of("UTC")))
    }
    @Test fun weekdayAndSnoozeUseCorrectFutureInstance() {
        val now = Instant.parse("2026-10-04T10:00:00Z") // Sunday
        val monday = MaaAlarm(hour = 8, days = setOf(DayOfWeek.MONDAY))
        assertEquals(Instant.parse("2026-10-05T08:00:00Z"), MaaAlarmClock.next(monday, now, ZoneId.of("UTC")))
        assertEquals(now.plusSeconds(600), MaaAlarmClock.next(monday.copy(snoozedUntil = now.plusSeconds(600).toEpochMilli()), now, ZoneId.of("UTC")))
    }
    @Test fun dstGapResolvesAndFallBackDoesNotDuplicateWallTime() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), MaaAlarmClock.next(MaaAlarm(hour = 2, minute = 30), Instant.parse("2026-03-08T05:00:00Z"), zone))
        // The first 01:30 has passed; do not ring again at the repeated 01:30.
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), MaaAlarmClock.next(MaaAlarm(hour = 1, minute = 30), Instant.parse("2026-11-01T05:45:00Z"), zone))
    }
    @Test fun alarmPersistenceSurvivesInvalidRecordAndKeepsIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "maa_alarms_test_only"
        val preferences = context.getSharedPreferences(name, 0)
        try {
            preferences.edit().putString("alarms", "not json").commit()
            val store = MaaAlarmStore(context, name)
            assertTrue(store.all().isEmpty())
            val alarm = MaaAlarm(hour = 23, minute = 59, days = setOf(DayOfWeek.FRIDAY), label = "Fixture")
            assertTrue(store.put(alarm)); assertEquals(alarm, store.all().single())
            assertTrue(store.put(alarm.copy(enabled = false))); assertEquals(1, store.all().size)
            assertTrue(store.remove(alarm.id)); assertTrue(store.all().isEmpty())
        } finally { preferences.edit().clear().commit() }
    }
    @Test fun notificationHistoryBoundsRetentionAndDeduplicates() {
        val now = 2_000_000_000_000L
        val fresh = (0..150).map { ArchivedNotification("$it", "test", "t", "body", now - it) }
        val old = ArchivedNotification("old", "test", "", "", now - NotificationArchive.MAX_AGE_MS - 1)
        val future = ArchivedNotification("future", "test", "", "", now + 1)
        val bounded = NotificationArchive.bounded(fresh + fresh.first() + old + future, now)
        assertEquals(100, bounded.size); assertEquals(100, bounded.map { it.key }.toSet().size)
        assertFalse(bounded.any { it.key == "old" || it.key == "future" })
    }
    @Test fun sosRejectsAmbiguousNumbersAndKeepsCanonicalRecipient() {
        assertEquals("+919876543210", SosRecipient.canonical("+91 98765-43210"))
        listOf("9876543210", "+01234567890", "+91 123", "+919876543210;123", "123 or 456", "+919876543210 ext 5").forEach { assertNull(it, SosRecipient.canonical(it)) }
    }
}
