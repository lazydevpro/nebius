package `in`.family.maa

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

data class ArchivedNotification(val key: String, val packageName: String, val title: String, val text: String, val postedAt: Long)
data class LiveNotification(val record: ArchivedNotification, val open: PendingIntent?, val actions: List<Notification.Action>)

/** Opt-in local history. Executable handles never enter persistent storage. */
object NotificationArchive {
    const val MAX_ITEMS = 100
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private val live = linkedMapOf<String, LiveNotification>()
    @Volatile var listenerConnected = false; private set
    private fun prefs(c: Context) = c.getSharedPreferences("maa_notification_archive_v1", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    @Synchronized fun setEnabled(c: Context, enabled: Boolean) {
        val edit = prefs(c).edit().putBoolean("enabled", enabled)
        if (!enabled) { edit.remove("items"); live.clear() }
        edit.commit()
        if(enabled) FamilyNotifications.refreshArchive()
    }
    @Synchronized fun clear(c: Context) { prefs(c).edit().remove("items").commit() }
    fun bounded(items: List<ArchivedNotification>, now: Long): List<ArchivedNotification> = items
        .filter { it.postedAt <= now && now - it.postedAt <= MAX_AGE_MS }
        .sortedByDescending { it.postedAt }.distinctBy { it.key }.take(MAX_ITEMS)
    @Synchronized fun history(c: Context): List<ArchivedNotification> {
        if (!enabled(c)) return emptyList()
        val data = try {
            val array = JSONArray(prefs(c).getString("items", "[]"))
            (0 until minOf(array.length(), MAX_ITEMS)).mapNotNull { i -> runCatching { array.getJSONObject(i).let {
                ArchivedNotification(it.getString("key"), it.getString("package"), it.optString("title"), it.optString("text"), it.getLong("at"))
            } }.getOrNull() }
        } catch (_: Exception) { emptyList() }
        val cleaned = bounded(data, System.currentTimeMillis())
        if (cleaned != data) write(c, cleaned)
        return cleaned
    }
    private fun write(c: Context, items: List<ArchivedNotification>) {
        val data = JSONArray()
        items.forEach { data.put(JSONObject().put("key", it.key).put("package", it.packageName).put("title", it.title.take(200)).put("text", it.text.take(1000)).put("at", it.postedAt)) }
        prefs(c).edit().putString("items", data.toString()).commit()
    }
    @Synchronized fun onConnected(c: Context, active: Array<StatusBarNotification>) {
        listenerConnected = true; live.clear()
        active.forEach { record(c, it) }
    }
    @Synchronized fun onDisconnected() { listenerConnected = false; live.clear() }
    @Synchronized fun record(c: Context, sbn: StatusBarNotification) {
        if (!enabled(c) || sbn.packageName == c.packageName || sbn.notification.visibility == Notification.VISIBILITY_SECRET) {
            live.remove(sbn.key)
            return
        }
        val extras = sbn.notification.extras
        val record = ArchivedNotification(sbn.key, sbn.packageName, extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(200),
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().take(1000), sbn.postTime)
        live[sbn.key] = LiveNotification(record, sbn.notification.contentIntent,
            sbn.notification.actions.orEmpty().filter { it.remoteInputs.isNullOrEmpty() }.take(5))
        while (live.size > MAX_ITEMS) live.remove(live.keys.first())
        write(c, bounded(listOf(record) + history(c).filterNot { it.key == record.key }, System.currentTimeMillis()))
    }
    @Synchronized fun removed(key: String) { live.remove(key) }
    @Synchronized fun active(): List<LiveNotification> = if (listenerConnected) live.values.toList().sortedByDescending { it.record.postedAt } else emptyList()
    @Synchronized fun current(key: String): LiveNotification? = if (listenerConnected) live[key] else null
    /** Consume before dispatch so repeated confirmation taps cannot replay this snapshot. */
    @Synchronized fun takeIntent(record: ArchivedNotification, intent: PendingIntent): PendingIntent? {
        val now = current(record.key) ?: return null
        if (now.record != record || (now.open != intent && now.actions.none { it.actionIntent == intent })) return null
        live.remove(record.key)
        return intent
    }
}
