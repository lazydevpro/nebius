package `in`.family.maa

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class MaaAlarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val scheduledAt: Long = 0L,
    val snoozedUntil: Long = 0L,
    val exact: Boolean = false,
    val firedAt: Long = 0L
)

/** Wall-clock schedule, evaluated in the device's current zone. Empty days means once. */
object MaaAlarmClock {
    /** A lifecycle refresh must preserve an existing occurrence, including a delayed one. */
    fun reconcileOccurrence(alarm:MaaAlarm, now:Instant, zone:ZoneId, resetWallClock:Boolean=false):Instant? {
        if(!alarm.enabled) return null
        if(!resetWallClock && alarm.scheduledAt>0) return Instant.ofEpochMilli(alarm.scheduledAt)
        return next(alarm,now,zone)
    }
    fun next(alarm: MaaAlarm, now: Instant, zone: ZoneId): Instant? {
        if (!alarm.enabled) return null
        require(alarm.hour in 0..23 && alarm.minute in 0..59)
        if (alarm.snoozedUntil > now.toEpochMilli()) return Instant.ofEpochMilli(alarm.snoozedUntil)
        val today = now.atZone(zone).toLocalDate()
        for (offset in 0L..7L) {
            val date: LocalDate = today.plusDays(offset)
            if (alarm.days.isNotEmpty() && date.dayOfWeek !in alarm.days) continue
            // atZone resolves a spring-forward gap to the first matching shifted local time.
            val candidate = date.atTime(LocalTime.of(alarm.hour, alarm.minute)).atZone(zone).toInstant()
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }
}

class MaaAlarmStore(context: Context, name: String = "maa_alarms_v1") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    @Synchronized fun all(): List<MaaAlarm> = try {
        val a = JSONArray(prefs.getString("alarms", "[]"))
        (0 until minOf(a.length(), 50)).mapNotNull { index ->
            runCatching {
                val o = a.getJSONObject(index)
                val h = o.getInt("hour"); val m = o.getInt("minute")
                require(h in 0..23 && m in 0..59)
                val id = o.getString("id"); require(id.length in 1..80)
                val days = o.optString("days").split(',').mapNotNull { it.toIntOrNull() }
                    .filter { it in 1..7 }.map(DayOfWeek::of).toSet()
                MaaAlarm(id, h, m, o.optString("label").take(80), days, o.optBoolean("enabled", true),
                    o.optLong("scheduledAt"), o.optLong("snoozedUntil"), o.optBoolean("exact"), o.optLong("firedAt"))
            }.getOrNull()
        }.distinctBy { it.id }
    } catch (_: Exception) { emptyList() }
    @Synchronized fun put(alarm: MaaAlarm): Boolean {
        val existing = all(); if (existing.none { it.id == alarm.id } && existing.size >= 50) return false
        return write(existing.filterNot { it.id == alarm.id } + alarm)
    }
    @Synchronized fun remove(id: String): Boolean = write(all().filterNot { it.id == id })
    private fun write(items: List<MaaAlarm>): Boolean {
        val a = JSONArray()
        items.forEach { x -> a.put(JSONObject().put("id", x.id).put("hour", x.hour).put("minute", x.minute)
            .put("label", x.label).put("days", x.days.sortedBy { it.value }.joinToString(",") { it.value.toString() })
            .put("enabled", x.enabled).put("scheduledAt", x.scheduledAt).put("snoozedUntil", x.snoozedUntil).put("exact", x.exact).put("firedAt", x.firedAt)) }
        return prefs.edit().putString("alarms", a.toString()).commit()
    }
}
