package `in`.family.maa

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.Instant
import java.time.ZoneId

object MaaAlarmScheduler {
    const val ID = "alarm_id"
    const val AT = "alarm_at"
    const val FIRE = "in.family.maa.ALARM_FIRE"
    const val DISMISS = "in.family.maa.ALARM_DISMISS"
    const val SNOOZE = "in.family.maa.ALARM_SNOOZE"
    const val CHANNEL = "maa_alarm_ring_v1"
    fun exactAvailable(c: Context): Boolean = Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun pending(c: Context, alarm: MaaAlarm): PendingIntent = PendingIntent.getBroadcast(c, 0,
        Intent(c, MaaAlarmReceiver::class.java).setAction(FIRE).setData(Uri.parse("maa-alarm://fire/${alarm.id}"))
            .putExtra(ID, alarm.id).putExtra(AT, alarm.scheduledAt), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(c: Context, alarm: MaaAlarm) { c.getSystemService(AlarmManager::class.java).cancel(pending(c, alarm)) }
    fun saveAndSchedule(c: Context, alarm: MaaAlarm, preserveOccurrence:Boolean=false): Boolean {
        val store = MaaAlarmStore(c)
        val next = (if(preserveOccurrence) MaaAlarmClock.reconcileOccurrence(alarm,Instant.now(),ZoneId.systemDefault()) else MaaAlarmClock.next(alarm, Instant.now(), ZoneId.systemDefault()))?.toEpochMilli() ?: 0L
        val scheduled = alarm.copy(scheduledAt = next, exact = exactAvailable(c))
        // Durable state must exist before Android can deliver the intent.
        if (!store.put(scheduled)) return false
        cancel(c, scheduled)
        if (next == 0L) return true
        val manager = c.getSystemService(AlarmManager::class.java)
        return try {
            if (scheduled.exact) {
                val show = PendingIntent.getActivity(c, 0, Intent(c, AlarmActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(next, show), pending(c, scheduled))
            } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(c, scheduled))
            true
        } catch (_: SecurityException) {
            // Special access can disappear between the check and scheduling.
            val approximate = scheduled.copy(exact = false)
            store.put(approximate)
            try { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(c, approximate)); true }
            catch (_: RuntimeException) { store.put(approximate.copy(enabled = false, scheduledAt = 0)); false }
        } catch (_: RuntimeException) { store.put(scheduled.copy(enabled = false, scheduledAt = 0)); false }
    }
    fun reconcile(c: Context, resetWallClock:Boolean=false) { MaaAlarmStore(c).all().forEach { saveAndSchedule(c, it, preserveOccurrence=!resetWallClock) } }
    fun delete(c: Context, alarm: MaaAlarm): Boolean {
        if (!MaaAlarmStore(c).remove(alarm.id)) return false
        cancel(c, alarm)
        return true
    }
    fun notification(c: Context, alarm: MaaAlarm, approximate: Boolean = false): Notification {
        val strings = AppLanguage.localizedContext(c)
        val manager = c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, strings.getString(R.string.daily_alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            description = strings.getString(R.string.daily_alarm_channel_description)
            setSound(null, null); enableVibration(true)
        })
        val open = PendingIntent.getActivity(c, 1, Intent(c, AlarmActivity::class.java).setData(Uri.parse("maa-alarm://open/${alarm.id}")).putExtra(ID, alarm.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String) = PendingIntent.getBroadcast(c, 0, Intent(c, MaaAlarmReceiver::class.java)
            .setAction(name).setData(Uri.parse("maa-alarm://$name/${alarm.id}")).putExtra(ID, alarm.id).putExtra(AT, alarm.firedAt), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_maa)
            .setContentTitle(alarm.label.ifBlank { strings.getString(R.string.daily_alarm_title) })
            .setContentText(strings.getString(if (approximate) R.string.daily_alarm_delayed else R.string.daily_alarm_ringing))
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open).setOngoing(!approximate).setAutoCancel(approximate)
            .addAction(Notification.Action.Builder(null, strings.getString(R.string.daily_snooze), action(SNOOZE)).build())
            .addAction(Notification.Action.Builder(null, strings.getString(R.string.daily_dismiss), action(DISMISS)).build()).build()
    }
}

class MaaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(MaaAlarmScheduler.ID) ?: return
        val store = MaaAlarmStore(context)
        val alarm = store.all().firstOrNull { it.id == id } ?: return
        when (intent.action) {
            MaaAlarmScheduler.FIRE -> {
                val at = intent.getLongExtra(MaaAlarmScheduler.AT, 0L)
                if (!alarm.enabled || at == 0L || at != alarm.scheduledAt || at > System.currentTimeMillis() + 1000) return
                // Consume the instance before ringing; duplicate broadcasts cannot start it again.
                val fired = alarm.copy(enabled = alarm.days.isNotEmpty(), scheduledAt = 0, snoozedUntil = 0, firedAt = at)
                if (!store.put(fired)) return
                if (alarm.days.isNotEmpty()) MaaAlarmScheduler.saveAndSchedule(context, fired)
                if (alarm.exact) {
                    try { context.startForegroundService(Intent(context, MaaAlarmRingService::class.java).putExtra(MaaAlarmScheduler.ID, id)); return }
                    catch (_: RuntimeException) { /* Background limitations still produce an honest notification when permitted. */ }
                }
                try { context.getSystemService(NotificationManager::class.java).notify(id.hashCode(), MaaAlarmScheduler.notification(context, fired, true)) } catch (_: SecurityException) { }
            }
            MaaAlarmScheduler.SNOOZE -> {
                if (alarm.firedAt == 0L || alarm.firedAt != intent.getLongExtra(MaaAlarmScheduler.AT, 0)) return
                MaaAlarmRingService.stopIfCurrent(context, id)
                context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
                MaaAlarmScheduler.saveAndSchedule(context, alarm.copy(enabled = true, snoozedUntil = System.currentTimeMillis() + 10 * 60_000L, firedAt = 0))
            }
            MaaAlarmScheduler.DISMISS -> {
                if (alarm.firedAt == 0L || alarm.firedAt != intent.getLongExtra(MaaAlarmScheduler.AT, 0)) return
                if (!store.put(alarm.copy(firedAt = 0))) return
                MaaAlarmRingService.stopIfCurrent(context, id)
                context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
            }
        }
    }
}

class MaaAlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) MaaAlarmScheduler.reconcile(context,resetWallClock=intent.action==Intent.ACTION_TIME_CHANGED || intent.action==Intent.ACTION_TIMEZONE_CHANGED)
    }
}
