package `in`.family.maa

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Switch
import java.text.DateFormat
import java.util.Date

class NotificationCenterActivity : DailyToolsActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state) }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        surface(R.string.daily_notifications_title)
        text(getString(R.string.daily_notifications_privacy), 18f)
        body.addView(Switch(this).apply {
            text = getString(R.string.daily_notifications_optin); textSize = 22f; minHeight = dp(64)
            isChecked = NotificationArchive.enabled(this@NotificationCenterActivity)
            var restoring = false
            setOnCheckedChangeListener { _, value ->
                if (restoring) return@setOnCheckedChangeListener
                restoring = true
                isChecked = NotificationArchive.enabled(this@NotificationCenterActivity)
                restoring = false
                CaregiverLock.request(this@NotificationCenterActivity) {
                    if (CaregiverLock.unlocked(this@NotificationCenterActivity)) NotificationArchive.setEnabled(this@NotificationCenterActivity, value)
                    render()
                }
            }
        })
        text(getString(if (NotificationArchive.listenerConnected) R.string.daily_notifications_connected else R.string.daily_notifications_disconnected), 18f)
        button(getString(R.string.daily_notifications_access)) { launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button(getString(R.string.daily_refresh)) { FamilyNotifications.refreshArchive();render() }
        if (!NotificationArchive.enabled(this)) return
        text(getString(R.string.daily_notifications_active), 26f)
        val active = NotificationArchive.active()
        if (active.isEmpty()) text(getString(R.string.daily_notifications_no_active))
        active.forEach { item ->
            val c = card(); text(item.record.title.ifBlank { item.record.packageName }, 24f, c); text(item.record.text, 20f, c)
            text(appName(item.record.packageName), 16f, c)
            if (item.open != null) button(getString(R.string.daily_notifications_open), c) {
                val now = NotificationArchive.current(item.record.key)
                if (now?.record == item.record && now.open == item.open) send(item.record, item.open) else { toast(R.string.daily_notifications_expired); render() }
            }
            item.actions.forEachIndexed { index, action ->
                button(action.title?.toString().orEmpty().ifBlank { getString(R.string.daily_notifications_action) }, c) {
                    AlertDialog.Builder(this).setTitle(action.title).setMessage(getString(R.string.daily_notifications_action_confirm, item.record.title, appName(item.record.packageName)))
                        .setNegativeButton(R.string.daily_cancel, null).setPositiveButton(R.string.daily_continue) { _, _ ->
                            val now = NotificationArchive.current(item.record.key)
                            val currentAction = now?.actions?.getOrNull(index)
                            if (now?.record == item.record && currentAction?.actionIntent == action.actionIntent) send(item.record, action.actionIntent)
                            else toast(R.string.daily_notifications_expired)
                        }.show()
                }
            }
        }
        text(getString(R.string.daily_notifications_history), 26f)
        button(getString(R.string.daily_notifications_clear)) {
            AlertDialog.Builder(this).setTitle(R.string.daily_notifications_clear).setMessage(R.string.daily_delete_confirm)
                .setNegativeButton(R.string.daily_cancel, null).setPositiveButton(R.string.daily_delete) { _, _ -> NotificationArchive.clear(this); render() }.show()
        }
        val history = NotificationArchive.history(this)
        if (history.isEmpty()) text(getString(R.string.daily_notifications_empty))
        history.forEach { item -> val c = card(); text(item.title.ifBlank { appName(item.packageName) }, 23f, c); text(item.text, 19f, c)
            text("${appName(item.packageName)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, AppLanguage.locale(this)).format(Date(item.postedAt))}", 15f, c)
        }
    }
    private fun send(record: ArchivedNotification, intent: PendingIntent) {
        val consumed = NotificationArchive.takeIntent(record, intent)
        if (consumed == null) { toast(R.string.daily_notifications_expired); render(); return }
        try { consumed.send() } catch (_: PendingIntent.CanceledException) { toast(R.string.daily_notifications_expired) }
        catch (_: SecurityException) { toast(R.string.daily_unavailable) }
        render()
    }
    private fun appName(packageName: String): String = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() } catch (_: Exception) { packageName }
}
