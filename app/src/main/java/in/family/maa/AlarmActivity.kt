package `in`.family.maa

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

class AlarmActivity : DailyToolsActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state) }
    override fun onResume() { super.onResume(); MaaAlarmScheduler.reconcile(this); render() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); render() }
    private fun render() {
        surface(R.string.daily_alarm_title)
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (!notificationManager.areNotificationsEnabled()) {
            text(getString(R.string.daily_alarm_notifications_off))
            button(getString(R.string.daily_enable_notifications)) {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 902)
                else launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        text(getString(if (MaaAlarmScheduler.exactAvailable(this)) R.string.daily_alarm_exact else R.string.daily_alarm_approximate), 18f)
        if (Build.VERSION.SDK_INT >= 31 && !MaaAlarmScheduler.exactAvailable(this)) button(getString(R.string.daily_alarm_allow_exact)) {
            launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        text(getString(R.string.daily_alarm_limitations), 16f)
        button(getString(R.string.daily_alarm_add)) { editor(null) }.id = R.id.daily_alarm_add
        val alarms = MaaAlarmStore(this).all().sortedBy { it.hour * 60 + it.minute }
        if (alarms.isEmpty()) text(getString(R.string.daily_alarm_empty))
        alarms.forEach { alarm ->
            val c = card()
            text(String.format(AppLanguage.locale(this), "%02d:%02d", alarm.hour, alarm.minute), 38f, c)
            if (alarm.label.isNotBlank()) text(alarm.label, 24f, c)
            val days = if (alarm.days.isEmpty()) getString(R.string.daily_alarm_once) else alarm.days.sortedBy { it.value }.joinToString(" · ") { it.getDisplayName(TextStyle.SHORT, AppLanguage.locale(this)) }
            text(days, 18f, c)
            if (alarm.enabled && alarm.scheduledAt > 0L) text(getString(R.string.daily_alarm_next, DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", AppLanguage.locale(this)).format(Instant.ofEpochMilli(alarm.scheduledAt).atZone(ZoneId.systemDefault()))), 16f, c)
            c.addView(Switch(this).apply {
                text = getString(if (alarm.enabled) R.string.daily_alarm_enabled else R.string.daily_alarm_disabled); textSize = 20f; minHeight = dp(64); isChecked = alarm.enabled
                setOnCheckedChangeListener { _, enabled ->
                    MaaAlarmRingService.stopIfCurrent(this@AlarmActivity,alarm.id)
                    getSystemService(NotificationManager::class.java).cancel(alarm.id.hashCode())
                    if (!MaaAlarmScheduler.saveAndSchedule(this@AlarmActivity, alarm.copy(enabled = enabled, snoozedUntil = 0,firedAt=0))) toast(R.string.daily_save_failed)
                    render()
                }
            })
            if (alarm.firedAt > 0 && (MaaAlarmRingService.ringingId == alarm.id || intent.getStringExtra(MaaAlarmScheduler.ID) == alarm.id)) {
                button(getString(R.string.daily_snooze), c) { alarmAction(alarm, MaaAlarmScheduler.SNOOZE) }
                button(getString(R.string.daily_dismiss), c) { alarmAction(alarm, MaaAlarmScheduler.DISMISS) }
            }
            button(getString(R.string.daily_edit), c) { editor(alarm) }
            button(getString(R.string.daily_delete), c) {
                AlertDialog.Builder(this).setTitle(R.string.daily_alarm_delete).setMessage(R.string.daily_delete_confirm)
                    .setNegativeButton(R.string.daily_cancel, null).setPositiveButton(R.string.daily_delete) { _, _ ->
                        MaaAlarmRingService.stopIfCurrent(this, alarm.id)
                        if (!MaaAlarmScheduler.delete(this, alarm)) toast(R.string.daily_save_failed)
                        render()
                    }.show()
            }
        }
    }
    private fun alarmAction(alarm: MaaAlarm, action: String) {
        sendBroadcast(Intent(this, MaaAlarmReceiver::class.java).setAction(action).putExtra(MaaAlarmScheduler.ID, alarm.id).putExtra(MaaAlarmScheduler.AT, alarm.firedAt))
        intent.removeExtra(MaaAlarmScheduler.ID)
        body.postDelayed({ if (!isFinishing) render() }, 100)
    }
    private fun editor(existing: MaaAlarm?) {
        val alarm = existing ?: MaaAlarm()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val label = EditText(this).apply { hint = getString(R.string.daily_alarm_label); setText(alarm.label); textSize = 20f; inputType = InputType.TYPE_CLASS_TEXT; maxLines = 2; id = R.id.daily_alarm_label }
        box.addView(label)
        val picker = TimePicker(this).apply { setIs24HourView(android.text.format.DateFormat.is24HourFormat(this@AlarmActivity)); hour = alarm.hour; minute = alarm.minute; id = R.id.daily_alarm_time }
        box.addView(picker)
        text(getString(R.string.daily_alarm_repeat_hint), 18f, box)
        val checks = DayOfWeek.values().associateWith { day -> CheckBox(this).apply {
            text = day.getDisplayName(TextStyle.FULL, AppLanguage.locale(this@AlarmActivity)); textSize = 20f; minHeight = dp(56); isChecked = day in alarm.days; box.addView(this)
        } }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) R.string.daily_alarm_add else R.string.daily_edit)
            .setView(ScrollView(this).apply { addView(box) }).setNegativeButton(R.string.daily_cancel, null).setPositiveButton(R.string.daily_save, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            picker.clearFocus()
            val updated = alarm.copy(hour = picker.hour, minute = picker.minute, label = label.text.toString().trim().take(80), days = checks.filterValues { it.isChecked }.keys, enabled = true, snoozedUntil = 0,firedAt=0)
            MaaAlarmRingService.stopIfCurrent(this,alarm.id)
            getSystemService(NotificationManager::class.java).cancel(alarm.id.hashCode())
            if (MaaAlarmScheduler.saveAndSchedule(this, updated)) { dialog.dismiss(); render() } else toast(R.string.daily_save_failed)
        } }; dialog.show()
    }
}
