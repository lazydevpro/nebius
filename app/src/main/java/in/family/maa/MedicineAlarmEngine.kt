package `in`.family.maa

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object MedicineAlarmEngine {
    const val ACTION_MEDICINE_ALARM = "in.family.maa.MEDICINE_ALARM_TRIGGER"
    const val EXTRA_MED_ID = "extra_med_id"
    private const val NOTIF_CHANNEL = "maa_medicine_channel"

    fun scheduleNextAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val medicines = MedicineManager.all(context).filter { it.enabled }
        if (medicines.isEmpty()) return

        val now = Calendar.getInstance()
        var earliestTriggerMs = Long.MAX_VALUE
        var nextMed: MedicineManager.MedicineItem? = null

        for (med in medicines) {
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, med.hour)
                set(Calendar.MINUTE, med.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // If already passed today or already taken today, schedule for tomorrow
            if (target.before(now) || MedicineManager.isTakenToday(context, med.id)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            if (target.timeInMillis < earliestTriggerMs) {
                earliestTriggerMs = target.timeInMillis
                nextMed = med
            }
        }

        if (nextMed == null || earliestTriggerMs == Long.MAX_VALUE) return

        val intent = Intent(context, MedicineAlarmReceiver::class.java).apply {
            action = ACTION_MEDICINE_ALARM
            putExtra(EXTRA_MED_ID, nextMed.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = PendingIntent.getActivity(
            context,
            9002,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // AlarmClockInfo guarantees waking device from Doze mode across Android 10-15
            am.setAlarmClock(AlarmManager.AlarmClockInfo(earliestTriggerMs, showIntent), pendingIntent)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, earliestTriggerMs, pendingIntent)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, earliestTriggerMs, pendingIntent)
            }
        } catch (_: Exception) {}
    }

    fun postMedicineNotification(context: Context, med: MedicineManager.MedicineItem) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val isHi = AppLanguage.isHindi(context)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL,
                if (isHi) "दवा रिमाइंडर" else "Medicine Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Audible reminders for senior citizen medicines"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            context,
            med.id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isHi) "🚨 दवा का समय: ${med.name(context)}" else "🚨 Medicine Time: ${med.name(context)}"
        val body = if (isHi) "${med.instruction(context)} · अभी लें" else "${med.instruction(context)} · Take now"

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, NOTIF_CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notif = builder
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_maa)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_MAX)
            .build()

        try {
            nm.notify(med.id.hashCode(), notif)
        } catch (_: Exception) {}
    }
}

class MedicineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getStringExtra(MedicineAlarmEngine.EXTRA_MED_ID) ?: return
        val med = MedicineManager.all(context).firstOrNull { it.id == medId }

        // Notify Proactive Asha to trigger red assistant button and voice summary
        ProactiveAsha.notifyUpdatesChanged()

        if (med != null && !MedicineManager.isTakenToday(context, medId)) {
            MedicineAlarmEngine.postMedicineNotification(context, med)
        }

        // Schedule next dose
        MedicineAlarmEngine.scheduleNextAlarm(context)
    }
}
