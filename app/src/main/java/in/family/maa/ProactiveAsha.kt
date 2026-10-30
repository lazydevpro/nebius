package `in`.family.maa

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Proactive assistant listener and notification manager.
 * Detects missed calls and incoming family media from WhatsApp notifications,
 * turns Asha red on Home and overlay, and reads out updates aloud to Mom when tapped.
 */
object ProactiveAsha {
    private const val PREFS_NAME = "proactive_asha"
    private const val KEY_LAST_MISSED_VIEWED = "last_missed_viewed_at"
    private const val KEY_WHATSAPP_SENDER = "sender"
    private const val KEY_WHATSAPP_IS_PHOTO = "is_photo"
    private const val KEY_WHATSAPP_TEXT = "text"
    private const val KEY_WHATSAPP_TIME = "timestamp"
    private const val KEY_WHATSAPP_CONSUMED = "consumed"
    private const val KEY_WHATSAPP_MESSAGE_TIME = "message_time"
    private const val KEY_NOTIF_MISSED_CALLER = "notif_missed_caller"
    private const val KEY_NOTIF_MISSED_NUMBER = "notif_missed_number"
    private const val KEY_NOTIF_MISSED_TIME = "notif_missed_time"
    private const val KEY_NOTIF_MISSED_CONSUMED = "notif_missed_consumed"

    data class PendingAlert(
        val sender: String,
        val isPhoto: Boolean,
        val text: String,
        val timestamp: Long,
        var consumed: Boolean = false
    ) {
        fun promptTitle(context: Context): String {
            val isHi = AppLanguage.isHindi(context)
            return if (isPhoto) {
                if (isHi) "कुछ नए फ़ोटो भेजे हैं $sender ने, देखना है?"
                else "New photos received from $sender. Would you like to view them?"
            } else {
                if (isHi) "नया संदेश आया है $sender से"
                else "New message from $sender"
            }
        }

        fun spokenPrompt(context: Context): String {
            val isHi = AppLanguage.isHindi(context)
            return if (isPhoto) {
                if (isHi) "कुछ नए फ़ोटो भेजे हैं $sender ने, देखना है तो 'हाँ' बोलिए"
                else "New photos have arrived from $sender. Say 'yes' if you want to see them."
            } else {
                if (isHi) "$sender से नया संदेश आया है।"
                else "You have a new message from $sender."
            }
        }
    }

    data class MissedCallInfo(
        val count: Int,
        val latestName: String,
        val latestNumber: String,
        val latestTimestamp: Long
    )

    data class UnreadSummary(
        val title: String,
        val spokenText: String,
        val actionLabel: String,
        val actionIntent: Intent,
        val medicineId: String? = null,
        val opensDirectly: Boolean = false
    )

    @Volatile
    private var latestAlert: PendingAlert? = null

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyUpdatesChanged() {
        listeners.forEach { runCatching { it.invoke() } }
    }

    /** A group counts as family by its name, or by being listed under trusted WhatsApp names in settings. */
    private fun isFamilyGroup(context: Context, chat: String): Boolean {
        val trusted = context.getSharedPreferences("family", Context.MODE_PRIVATE).getString("trusted", "").orEmpty()
            .split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        return chat.contains("family", ignoreCase = true) || chat.contains("परिवार") ||
            trusted.any { chat.equals(it, ignoreCase = true) }
    }

    /** chat is the contact or group name WhatsApp shows. Chats outside the family never raise an alert. */
    fun onWhatsAppNotification(context: Context, chat: String, text: String, messageTime: Long = 0L) {
        val cleanTitle = chat.trim()
        val cleanText = text.trim()
        if (cleanTitle.isBlank()) return

        val family = FamilyDirectory.read(context)
        val matched = family.firstOrNull {
            it.actionable && (cleanTitle.contains(it.name, ignoreCase = true) || it.name.contains(cleanTitle, ignoreCase = true))
        }
        if (matched == null && !isFamilyGroup(context, cleanTitle)) return

        // WhatsApp reposts notifications, which must not bring back a photo already seen.
        val seen = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (messageTime > 0L) {
            if (messageTime <= seen.getLong(KEY_WHATSAPP_MESSAGE_TIME, 0L)) return
            seen.edit().putLong(KEY_WHATSAPP_MESSAGE_TIME, messageTime).apply()
        }

        // Keep the first photo's time so later photos join it, and later chat cannot replace it.
        if (hasPendingPhoto(context)) return

        val senderLabel = when {
            matched != null && matched.slot == 1 -> if (AppLanguage.isHindi(context)) "बेटे" else matched.name
            matched != null -> matched.name
            else -> cleanTitle
        }

        // WhatsApp marks photos with this emoji in every language, unlike a chat that merely says "photo".
        val isPhoto = cleanText.contains("📷")

        val alert = PendingAlert(
            sender = senderLabel,
            isPhoto = isPhoto,
            text = cleanText,
            timestamp = System.currentTimeMillis()
        )
        latestAlert = alert

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_WHATSAPP_SENDER, senderLabel)
            .putBoolean(KEY_WHATSAPP_IS_PHOTO, isPhoto)
            .putString(KEY_WHATSAPP_TEXT, cleanText)
            .putLong(KEY_WHATSAPP_TIME, alert.timestamp)
            .putBoolean(KEY_WHATSAPP_CONSUMED, false)
            .apply()

        notifyUpdatesChanged()
    }

    fun onMissedCallNotification(context: Context, caller: String, text: String) {
        val cleanCaller = caller.trim()
        if (cleanCaller.isBlank()) return

        val family = FamilyDirectory.read(context)
        val matched = family.firstOrNull {
            it.actionable && (cleanCaller.contains(it.name, ignoreCase = true) || it.name.contains(cleanCaller, ignoreCase = true))
        }
        val callerLabel = when {
            matched != null && matched.slot == 1 -> if (AppLanguage.isHindi(context)) "बेटे" else matched.name
            matched != null -> matched.name
            else -> cleanCaller
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_NOTIF_MISSED_CALLER, callerLabel)
            .putString(KEY_NOTIF_MISSED_NUMBER, matched?.canonicalNumber ?: cleanCaller)
            .putLong(KEY_NOTIF_MISSED_TIME, System.currentTimeMillis())
            .putBoolean(KEY_NOTIF_MISSED_CONSUMED, false)
            .apply()

        notifyUpdatesChanged()
    }

    private fun getLastMissedViewedAt(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAST_MISSED_VIEWED)) {
            val initTime = System.currentTimeMillis() - 5 * 60 * 1000L
            prefs.edit().putLong(KEY_LAST_MISSED_VIEWED, initTime).apply()
            return initTime
        }
        return prefs.getLong(KEY_LAST_MISSED_VIEWED, 0L)
    }

    fun getUnreadMissedCalls(context: Context): MissedCallInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Query CallLog if permission granted
        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            val lastViewed = getLastMissedViewedAt(context)
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE
            )
            val selection = "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf(CallLog.Calls.MISSED_TYPE.toString(), lastViewed.toString())
            val sortOrder = "${CallLog.Calls.DATE} DESC"

            try {
                context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                    val count = cursor.count
                    if (count > 0 && cursor.moveToFirst()) {
                        val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                        val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                        val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)

                        val rawNum = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                        val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                        val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L

                        val family = FamilyDirectory.read(context)
                        val canonical = FamilyDirectory.canonicalNumber(rawNum)
                        val match = family.firstOrNull {
                            it.actionable && ((canonical != null && it.canonicalNumber == canonical) || it.rawNumber == rawNum)
                        }

                        val displayName = when {
                            match != null && match.slot == 1 -> if (AppLanguage.isHindi(context)) "बेटे" else match.name
                            match != null -> match.name
                            cachedName.isNotBlank() -> cachedName
                            rawNum.isNotBlank() -> rawNum
                            else -> if (AppLanguage.isHindi(context)) "अपरिचित" else "Unknown"
                        }
                        return MissedCallInfo(count, displayName, rawNum, date)
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback to notification-intercepted missed call if unconsumed
        val notifConsumed = prefs.getBoolean(KEY_NOTIF_MISSED_CONSUMED, true)
        val notifTime = prefs.getLong(KEY_NOTIF_MISSED_TIME, 0L)
        val now = System.currentTimeMillis()
        if (!notifConsumed && now - notifTime <= 12 * 60 * 60 * 1000L) {
            val caller = prefs.getString(KEY_NOTIF_MISSED_CALLER, "").orEmpty()
            val number = prefs.getString(KEY_NOTIF_MISSED_NUMBER, "").orEmpty()
            if (caller.isNotBlank()) {
                return MissedCallInfo(1, caller, number, notifTime)
            }
        }

        return null
    }

    fun hasPendingWhatsApp(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_WHATSAPP_TIME, 0L)
        val consumed = prefs.getBoolean(KEY_WHATSAPP_CONSUMED, true)
        val now = System.currentTimeMillis()
        return !consumed && (now - timestamp <= 12 * 60 * 60 * 1000L)
    }

    fun hasUnreadMissedCalls(context: Context): Boolean {
        return getUnreadMissedCalls(context) != null
    }

    fun hasUnreadUpdates(context: Context): Boolean {
        return MedicineManager.hasDueMedicine(context) || hasPendingWhatsApp(context) || hasUnreadMissedCalls(context)
    }

    fun hasPendingPhoto(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_WHATSAPP_TIME, 0L)
        val consumed = prefs.getBoolean(KEY_WHATSAPP_CONSUMED, true)
        val isPhoto = prefs.getBoolean(KEY_WHATSAPP_IS_PHOTO, false)
        val now = System.currentTimeMillis()
        return !consumed && isPhoto && (now - timestamp <= 12 * 60 * 60 * 1000L)
    }

    /** Asha turns yellow for new family photos. Medicine and missed calls stay red and take priority. */
    fun showsPhotoAlert(context: Context): Boolean =
        hasPendingPhoto(context) && !MedicineManager.hasDueMedicine(context) && !hasUnreadMissedCalls(context)

    fun getPendingWhatsAppAlert(context: Context): PendingAlert? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_WHATSAPP_TIME, 0L)
        val consumed = prefs.getBoolean(KEY_WHATSAPP_CONSUMED, true)
        val now = System.currentTimeMillis()
        if (consumed || now - timestamp > 12 * 60 * 60 * 1000L) return null

        val sender = prefs.getString(KEY_WHATSAPP_SENDER, "").orEmpty()
        val isPhoto = prefs.getBoolean(KEY_WHATSAPP_IS_PHOTO, false)
        val text = prefs.getString(KEY_WHATSAPP_TEXT, "").orEmpty()
        if (sender.isBlank()) return null
        return PendingAlert(sender, isPhoto, text, timestamp, consumed)
    }

    fun markPhotosViewed(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WHATSAPP_CONSUMED, true).apply()
        latestAlert?.consumed = true
        notifyUpdatesChanged()
    }

    fun markMissedCallsViewed(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_MISSED_VIEWED, System.currentTimeMillis())
            .putBoolean(KEY_NOTIF_MISSED_CONSUMED, true)
            .apply()
        notifyUpdatesChanged()
    }

    fun markAllUpdatesViewed(context: Context) {
        markPhotosViewed(context)
        markMissedCallsViewed(context)
        notifyUpdatesChanged()
    }

    fun consumePendingAlert(context: Context): PendingAlert? {
        val alert = getPendingWhatsAppAlert(context) ?: return null
        markPhotosViewed(context)
        return alert.copy(consumed = true)
    }

    fun getUnreadSummary(context: Context): UnreadSummary {
        val isHi = AppLanguage.isHindi(context)

        // Case 0: Due Medicine Reminder (Highest priority health care alert)
        val dueMed = MedicineManager.getDueMedicine(context)
        if (dueMed != null) {
            val title = if (isHi) "💊 दवा का समय: ${dueMed.name(context)}" else "💊 Medicine Time: ${dueMed.name(context)}"
            val spoken = if (isHi) {
                "माँ जी, क्या आपने ${dueMed.name(context)} ले ली है? अगर ले ली है तो 'हाँ' बोलिए।"
            } else {
                "Mom, have you taken your ${dueMed.name(context)}? Say 'yes' if you have taken it."
            }
            val actionLabel = if (isHi) "✓ हाँ, दवा ले ली है" else "✓ Yes, I have taken it"
            return UnreadSummary(
                title = title,
                spokenText = spoken,
                actionLabel = actionLabel,
                actionIntent = Intent(context, MainActivity::class.java).putExtra("confirm_med_id", dueMed.id),
                medicineId = dueMed.id
            )
        }

        val whatsAppAlert = getPendingWhatsAppAlert(context)
        val missedCalls = getUnreadMissedCalls(context)

        // Case 1: Both WhatsApp alert AND Missed Calls
        if (whatsAppAlert != null && missedCalls != null) {
            val title = if (isHi) "नए फ़ोटो और मिस्ड कॉल" else "New photos & missed call"
            val sender = whatsAppAlert.sender
            val caller = missedCalls.latestName
            val spoken = if (isHi) {
                if (whatsAppAlert.isPhoto) {
                    "$sender के नए फ़ोटो आए हैं, और $caller की मिस्ड कॉल आई है। क्या आप फ़ोटो देखना चाहती हैं?"
                } else {
                    "$sender का नया संदेश आया है, और $caller की मिस्ड कॉल आई है।"
                }
            } else {
                if (whatsAppAlert.isPhoto) {
                    "New photos from $sender and a missed call from $caller. Would you like to view the photos?"
                } else {
                    "New message from $sender and a missed call from $caller."
                }
            }
            val actionLabel = if (whatsAppAlert.isPhoto) {
                if (isHi) "📷 फ़ोटो देखें" else "📷 View Photos"
            } else {
                if (isHi) "📞 मिस्ड कॉल देखें" else "📞 View Missed Calls"
            }
            val intent = if (whatsAppAlert.isPhoto) {
                Intent(context, GalleryActivity::class.java)
            } else {
                Intent(context, MissedCallsActivity::class.java)
            }
            return UnreadSummary(title, spoken, actionLabel, intent)
        }

        // Case 2: WhatsApp alert only
        if (whatsAppAlert != null) {
            val sender = whatsAppAlert.sender
            if (whatsAppAlert.isPhoto) {
                val title = if (isHi) "📷 $sender ने नई फ़ोटो भेजी है" else "📷 New photo from $sender"
                val spoken = if (isHi) "$sender से नई फ़ोटो आई है।" else "A new photo has arrived from $sender."
                val label = if (isHi) "📷 फ़ोटो देखें" else "📷 View Photos"
                val photos = GalleryActivity.newPhotosIntent(context, whatsAppAlert.timestamp, spoken)
                return UnreadSummary(title, spoken, label, photos, opensDirectly = true)
            } else {
                val title = if (isHi) "💬 $sender का नया संदेश" else "💬 New message from $sender"
                val spoken = if (isHi) {
                    "$sender से नया संदेश आया है।"
                } else {
                    "New message from $sender."
                }
                val label = if (isHi) "💬 संदेश देखें" else "💬 View Messages"
                return UnreadSummary(title, spoken, label, Intent(context, CommunicationActivity::class.java))
            }
        }

        // Case 3: Missed calls only
        if (missedCalls != null) {
            val caller = missedCalls.latestName
            val count = missedCalls.count
            val isSingle = count <= 1
            val title = if (isHi) {
                if (isSingle) "📞 $caller की मिस्ड कॉल" else "📞 $caller की $count मिस्ड कॉल"
            } else {
                if (isSingle) "📞 Missed call from $caller" else "📞 $count missed calls from $caller"
            }
            val spoken = if (isHi) {
                if (isSingle) "$caller की मिस्ड कॉल आई है। क्या आप वापस कॉल करना चाहती हैं?"
                else "$caller की $count मिस्ड कॉल आई हैं। क्या आप वापस कॉल करना चाहती हैं?"
            } else {
                if (isSingle) "You have a missed call from $caller. Would you like to call back?"
                else "You have $count missed calls from $caller. Would you like to call back?"
            }
            val label = if (isHi) "📞 $caller को कॉल करें" else "📞 Call $caller"
            val canonical = FamilyDirectory.canonicalNumber(missedCalls.latestNumber)
            val callIntent = if (canonical != null) {
                CommunicationIntents.dial(canonical)
            } else if (missedCalls.latestNumber.isNotBlank()) {
                Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", missedCalls.latestNumber, null))
            } else {
                Intent(context, MissedCallsActivity::class.java)
            }
            return UnreadSummary(title, spoken, label, callIntent)
        }

        // Case 4: No unread updates
        val title = if (isHi) "कोई नया अपडेट नहीं है" else "No new updates"
        val spoken = if (isHi) "कोई नया संदेश या मिस्ड कॉल नहीं है। आप क्या करना चाहती हैं?" else "There are no new notifications or missed calls. How can I help you?"
        val label = if (isHi) "होम स्क्रीन" else "Home"
        return UnreadSummary(title, spoken, label, Intent(context, MainActivity::class.java))
    }
}
