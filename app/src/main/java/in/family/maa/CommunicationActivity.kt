package `in`.family.maa

import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object CommunicationIntents {
    fun sanitizeNumber(raw: String): String {
        val trimmed = raw.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        return if (trimmed.startsWith("+")) {
            "+" + trimmed.substring(1).filter { it.isDigit() }
        } else {
            trimmed.filter { it.isDigit() }
        }
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Exception) { false }
    }

    private fun requireNumber(number: String) = require(number.matches(Regex("\\+[1-9][0-9]{6,14}")))

    fun dial(number: String): Intent {
        val clean = if (number.startsWith("+")) number else sanitizeNumber(number)
        return Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", clean, null))
    }

    fun call(number: String): Intent {
        val clean = if (number.startsWith("+")) number else sanitizeNumber(number)
        return Intent(Intent.ACTION_CALL, Uri.fromParts("tel", clean, null))
    }

    fun callDirect(context: Context, number: String) {
        val cleanNumber = sanitizeNumber(number)
        if (cleanNumber.isEmpty()) {
            Toast.makeText(context, if (AppLanguage.isHindi(context)) "अमान्य फोन नंबर" else "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        if (isAirplaneModeOn(context)) {
            val msg = if (AppLanguage.isHindi(context)) "हवाई जहाज़ मोड चालू है। कृपया इसे बंद करें।" else "Airplane mode is ON. Please disable it to call."
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }

        val hasCallPerm = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val intent = if (hasCallPerm) call(cleanNumber) else dial(cleanNumber)
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: SecurityException) {
            try {
                val dialIntent = dial(cleanNumber).apply {
                    if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
            } catch (_: Exception) {
                Toast.makeText(context, if (AppLanguage.isHindi(context)) "कॉल ऐप उपलब्ध नहीं है" else "Phone app unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            try {
                val dialIntent = dial(cleanNumber).apply {
                    if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
            } catch (_: Exception) {
                Toast.makeText(context, if (AppLanguage.isHindi(context)) "कॉल ऐप उपलब्ध नहीं है" else "Phone app unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sms(context: Context, number: String, text: String): Intent {
        val clean = if (number.startsWith("+")) number else sanitizeNumber(number)
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", clean, null)).apply {
            putExtra("sms_body", text)
            Telephony.Sms.getDefaultSmsPackage(context)?.let { setPackage(it) }
        }
    }

    fun sendSmsDirect(context: Context, number: String, text: String): Boolean {
        val cleanNumber = sanitizeNumber(number)
        if (cleanNumber.isBlank()) return false
        if (isAirplaneModeOn(context)) return false
        val hasSmsPerm = context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasSmsPerm) return false
        return try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            } ?: return false

            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanNumber, null, text, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun whatsApp(number: String, text: String): Intent {
        val clean = sanitizeNumber(number)
        val plain = if (clean.startsWith("+")) clean.drop(1) else clean
        val uri = Uri.Builder().scheme("https").authority("wa.me").appendPath(plain)
            .apply { if (text.isNotEmpty()) appendQueryParameter("text", text) }.build()
        return Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
    }

    data class VideoCall(val intent: Intent, val announcement: String)

    fun isVideoCallRequest(text: String) = VIDEO_CALL_REQUEST.containsMatchIn(text)

    /** What to open for a spoken video call, or null when the words are not asking for one. */
    fun videoCall(context: Context, text: String): VideoCall? {
        if (!isVideoCallRequest(text)) return null
        // Whoever was named, otherwise the first family member.
        val person = FamilyDirectory.findMatch(context, text)?.takeIf { it.slot > 0 }
            ?: FamilyDirectory.read(context).firstOrNull { it.actionable } ?: return null
        val number = person.canonicalNumber ?: return null
        val strings = AppLanguage.localizedContext(context)
        whatsAppVideoCall(context, number)?.let {
            return VideoCall(it, strings.getString(R.string.assistant_video_calling, person.name))
        }
        val chat = whatsAppChat(context, number) ?: return null
        if (VideoCallAssist.ready(context)) {
            VideoCallAssist.arm()
            return VideoCall(chat, strings.getString(R.string.assistant_video_calling, person.name))
        }
        return VideoCall(chat, strings.getString(R.string.assistant_video_open_chat, person.name))
    }

    /**
     * WhatsApp adds a video call row to each saved contact it can reach, and opening that row starts
     * the call. Null when there is no such row, as when the number is not in the phone's contacts.
     */
    fun whatsAppVideoCall(context: Context, number: String): Intent? {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val contact = runCatching { contactId(context, number) }.getOrNull() ?: return null
        for ((pkg, type) in WHATSAPP_VIDEO_CALL_ROWS) {
            val row = runCatching {
                context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data._ID),
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contact.toString(), type),
                    null
                )?.use { if (it.moveToFirst()) it.getLong(0) else null }
            }.getOrNull() ?: continue
            return Intent(Intent.ACTION_VIEW)
                .setDataAndType(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, row), type)
                .setPackage(pkg)
        }
        return null
    }

    private fun contactId(context: Context, number: String): Long? =
        context.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun whatsAppChat(context: Context, number: String): Intent? {
        val pkg = WHATSAPP_VIDEO_CALL_ROWS.map { it.first }
            .firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null } ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${sanitizeNumber(number).removePrefix("+")}")).setPackage(pkg)
    }

    private val WHATSAPP_VIDEO_CALL_ROWS = listOf(
        "com.whatsapp" to "vnd.android.cursor.item/vnd.com.whatsapp.video.call",
        "com.whatsapp.w4b" to "vnd.android.cursor.item/vnd.com.whatsapp.w4b.video.call"
    )
    // "वीडियो बनाओ" means recording, so "video" alone is not enough.
    private val VIDEO_CALL_REQUEST = Regex(
        "(?:video|वीडियो|विडियो)\\s*(?:call|कॉल|काल|phone|फोन|फ़ोन|chat|चैट|(?:pe|par|पे|पर)\\s+(?:baat|बात))",
        RegexOption.IGNORE_CASE
    )
}

/** Owned family selection and message drafts; external apps retain final send/call authority. */
class CommunicationActivity : CommunicationUiActivity() {
    private var person: FamilyDirectory.Person? = null
    private var screen = "family"
    private var channel = "sms"
    private var mode = "phone"
    private var editor: EditText? = null
    private var notice = ""
    companion object {
        const val EXTRA_SLOT = "family_slot"
        const val EXTRA_CHANNEL = "draft_channel"
        const val EXTRA_MODE = "communication_mode" // "phone" or "messages"
        fun intent(context: Context, slot: Int = 0) = Intent(context, CommunicationActivity::class.java)
            .putExtra(EXTRA_SLOT, slot)
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val slot = state?.getInt("slot") ?: intent.getIntExtra(EXTRA_SLOT, 0)
        person = FamilyDirectory.read(this).firstOrNull { it.slot == slot }
        mode = state?.getString("mode") ?: intent.getStringExtra(EXTRA_MODE)
            ?: if (intent.hasExtra(EXTRA_CHANNEL) && intent.getStringExtra(EXTRA_CHANNEL) != "call") "messages" else "phone"
        channel = (state?.getString("channel") ?: intent.getStringExtra(EXTRA_CHANNEL))
            .takeIf { it == "whatsapp" } ?: "sms"
        val requestedCompose = state?.getString("screen") == "compose" || (intent.hasExtra(EXTRA_CHANNEL) && mode == "messages")
        val previousNumber = state?.getString("number")
        when {
            person == null -> showFamily()
            previousNumber != null && previousNumber != person?.canonicalNumber -> {
                notice = tr(R.string.communication_changed); showPerson()
            }
            requestedCompose && person?.actionable == true -> showComposer()
            else -> showPerson()
        }
    }
    override fun onSaveInstanceState(state: Bundle) {
        saveDraft()
        state.putString("screen", screen); state.putInt("slot", person?.slot ?: 0)
        state.putString("number", person?.canonicalNumber); state.putString("channel", channel)
        state.putString("mode", mode)
        super.onSaveInstanceState(state)
    }
    override fun onPause() { saveDraft(); super.onPause() }
    private fun saveDraft() {
        val number = person?.canonicalNumber ?: return
        val draft = editor?.text?.toString() ?: return
        CommunicationDrafts.save(this, number, channel, draft)
    }
    private fun showFamily() {
        saveDraft(); editor = null; screen = "family"; person = null
        val title = if (mode == "messages") {
            if (channel == "whatsapp") tr(R.string.communication_whatsapp) else tr(R.string.home_messages)
        } else {
            tr(R.string.home_phone)
        }
        surface(title)
        for (member in FamilyDirectory.read(this)) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = shape(Color.WHITE, true); setPadding(dp(16), dp(16), dp(16), dp(16))
                minimumHeight = dp(120); isClickable = true; isFocusable = true
                contentDescription = member.name + ". " + (member.canonicalNumber ?: tr(R.string.communication_setup_needed))
                setOnClickListener {
                    val num = member.canonicalNumber ?: member.rawNumber
                    if (mode == "phone") {
                        if (num.isBlank()) {
                            Toast.makeText(this@CommunicationActivity, issueText(member).ifBlank { tr(R.string.missing_number) }, Toast.LENGTH_SHORT).show()
                        } else {
                            CommunicationIntents.callDirect(this@CommunicationActivity, num)
                        }
                    } else {
                        if (num.isBlank()) {
                            Toast.makeText(this@CommunicationActivity, issueText(member).ifBlank { tr(R.string.missing_number) }, Toast.LENGTH_SHORT).show()
                        } else {
                            person = member
                            notice = ""
                            showComposer()
                        }
                    }
                }
                addView(avatar(member.name, member.photoUri))
                addView(LinearLayout(this@CommunicationActivity).apply {
                    orientation = LinearLayout.VERTICAL; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    addView(label(member.name, 25f).apply { setTypeface(null, Typeface.BOLD) })
                    if (!member.actionable) addView(label(issueText(member), 17f))
                    else if (member.canonicalNumber != null) addView(label(member.canonicalNumber, 19f).apply { setTextColor(MaaDesign.muted) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })

                val isHi = AppLanguage.isHindi(this@CommunicationActivity)
                val actionBtn = android.widget.Button(this@CommunicationActivity).apply {
                    text = if (mode == "phone") (if (isHi) "📞 कॉल" else "📞 Call") else (if (isHi) "✉️ संदेश" else "✉️ Text")
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    background = MaaDesign.shape(this@CommunicationActivity, if (mode == "phone") Color.rgb(15, 119, 113) else Color.rgb(2, 132, 199), 16)
                    minHeight = dp(48); minimumHeight = dp(48)
                    setPadding(dp(16), 0, dp(16), 0)
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                addView(actionBtn)
            }
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        }
        if (mode == "phone") {
            if (getSharedPreferences("family", MODE_PRIVATE).getBoolean("family_only", true)) {
                action(tr(R.string.communication_more_tools), R.id.communication_more_tools, true) {
                    CaregiverLock.request(this) {
                        AlertDialog.Builder(this).setTitle(R.string.communication_more_tools)
                            .setItems(arrayOf(tr(R.string.communication_keypad), tr(R.string.communication_device_contacts))) { _, selected ->
                                startActivity(if (selected == 0) PhoneDialerActivity.intent(this) else PhoneContactsActivity.intent(this))
                            }.setNegativeButton(R.string.communication_cancel, null).show()
                    }
                }
            } else {
                action(tr(R.string.communication_keypad), R.id.communication_keypad, true) { startActivity(PhoneDialerActivity.intent(this)) }
                action(tr(R.string.communication_device_contacts), R.id.communication_contacts, true) { startActivity(PhoneContactsActivity.intent(this)) }
            }
        }
    }
    private fun issueText(member: FamilyDirectory.Person) = when (member.issue) {
        FamilyDirectory.Issue.MISSING_NUMBER -> tr(R.string.communication_missing_number)
        FamilyDirectory.Issue.INVALID_NUMBER -> tr(R.string.communication_invalid_number)
        FamilyDirectory.Issue.DUPLICATE_NUMBER -> tr(R.string.communication_duplicate_number)
        null -> ""
    }
    private fun personHeader(member: FamilyDirectory.Person) {
        content.addView(avatar(member.name, member.photoUri, 112), LinearLayout.LayoutParams(dp(112), dp(112)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12)
        })
        content.addView(label(member.name, 30f).apply { gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD) })
        content.addView(label(member.canonicalNumber ?: member.rawNumber, 22f).apply {
            id = R.id.communication_recipient_number; gravity = Gravity.CENTER; textDirection = View.TEXT_DIRECTION_LTR
        })
    }
    private fun showPerson() {
        saveDraft(); editor = null; screen = "person"
        val member = person ?: return showFamily()
        surface(tr(R.string.communication_person_title), ::showFamily)
        personHeader(member)
        if (notice.isNotBlank()) content.addView(label(notice))
        if (!member.actionable) {
            content.addView(label(issueText(member)))
            content.addView(label(tr(R.string.communication_caregiver_fix)))
            return
        }
        action(tr(R.string.communication_phone_call), R.id.communication_call) { confirmHandoff("call", "") }
        action(tr(R.string.communication_sms), R.id.communication_sms, true) { channel = "sms"; showComposer() }
        action(tr(R.string.communication_whatsapp), R.id.communication_whatsapp, true) { channel = "whatsapp"; showComposer() }
    }
    private fun showComposer() {
        val member = person ?: return showFamily()
        if (!member.actionable) return if (mode == "messages" && intent.getIntExtra(EXTRA_SLOT, 0) == 0) showFamily() else showPerson()
        editor = null; screen = "compose"
        val backNav = if (mode == "messages" && intent.getIntExtra(EXTRA_SLOT, 0) == 0) ::showFamily else ::showPerson
        surface(tr(if (channel == "sms") R.string.communication_sms else R.string.communication_whatsapp), backNav)
        content.addView(label(tr(R.string.communication_to, member.name), 27f).apply { setTypeface(null, Typeface.BOLD) })
        content.addView(label(member.canonicalNumber!!, 21f).apply {
            id = R.id.communication_recipient_number; textDirection = View.TEXT_DIRECTION_LTR
        })
        val input = EditText(this).apply {
            id = R.id.communication_draft; textSize = 24f; setTextColor(ink); setHintTextColor(Color.rgb(83, 99, 87))
            background = shape(Color.WHITE, true); setPadding(dp(18), dp(16), dp(18), dp(16))
            gravity = Gravity.TOP or Gravity.START; minLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(CommunicationDrafts.MAX_LENGTH))
            hint = tr(R.string.communication_write_message); contentDescription = tr(R.string.communication_write_message)
            setText(CommunicationDrafts.read(this@CommunicationActivity, member.canonicalNumber, channel))
        }
        editor = input
        content.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { saveDraft() }
        })
        action(tr(R.string.communication_review), R.id.communication_review) { confirmHandoff(channel, input.text.toString()) }
        action(tr(R.string.communication_clear_draft), R.id.communication_clear, true) {
            AlertDialog.Builder(this).setTitle(R.string.communication_clear_draft)
                .setMessage(R.string.communication_clear_confirm)
                .setNegativeButton(R.string.communication_cancel, null)
                .setPositiveButton(R.string.communication_clear) { _, _ -> input.setText(""); CommunicationDrafts.clear(this, member.canonicalNumber, channel) }.show()
        }
        content.addView(label(tr(R.string.communication_templates), 17f))
        listOf(R.string.communication_template_call, R.string.communication_template_ok, R.string.communication_template_help).forEach { resource ->
            action(tr(resource), secondary = true) {
                val text = tr(resource)
                if (input.text.isNullOrBlank()) input.setText(text) else {
                    input.append(if (input.text.endsWith("\n")) text else "\n$text")
                }
                input.setSelection(input.text.length)
            }
        }
    }
    private fun confirmHandoff(kind: String, text: String) {
        val member = person ?: return
        if (!FamilyDirectory.stillMatches(this, member)) {
            notice = tr(R.string.communication_changed)
            person = FamilyDirectory.read(this).firstOrNull { it.slot == member.slot }
            // Preserve the original recipient's draft before switching to an updated identity.
            editor?.text?.toString()?.let { member.canonicalNumber?.let { number -> CommunicationDrafts.save(this, number, channel, it) } }
            editor = null
            showPerson(); return
        }
        if (kind != "call" && text.isBlank()) {
            editor?.error = tr(R.string.communication_message_required); return
        }
        val title = tr(if (kind == "call") R.string.communication_review_call else R.string.communication_review_message)
        val explanation = tr(if (kind == "call") R.string.communication_call_confirmation else R.string.communication_message_confirmation)
        val body = "${member.name}\n${member.canonicalNumber}\n\n" + (if (kind == "call") "" else "$text\n\n") + explanation
        AlertDialog.Builder(this).setTitle(title).setMessage(body)
            .setNegativeButton(R.string.communication_cancel, null)
            .setPositiveButton(R.string.communication_continue) { _, _ ->
                if (!FamilyDirectory.stillMatches(this, member) || (kind != "call" && editor?.text?.toString() != text)) {
                    notice = tr(R.string.communication_changed)
                    saveDraft(); editor = null
                    person = FamilyDirectory.read(this).firstOrNull { it.slot == member.slot }
                    showPerson()
                    return@setPositiveButton
                }
                if (kind == "call") {
                    val targetNum = member.canonicalNumber ?: member.rawNumber
                    CommunicationIntents.callDirect(this@CommunicationActivity, targetNum)
                    return@setPositiveButton
                }
                if (kind == "sms") {
                    val targetNum = member.canonicalNumber ?: member.rawNumber
                    DirectSmsDispatcher.sendSmsWithCallback(this@CommunicationActivity, targetNum, text) { success, error ->
                        val isHi = AppLanguage.isHindi(this@CommunicationActivity)
                        if (success) {
                            CommunicationDrafts.clear(this@CommunicationActivity, member.canonicalNumber ?: member.rawNumber, channel)
                            editor?.setText("")
                            Toast.makeText(this@CommunicationActivity, if (isHi) "संदेश भेज दिया गया है ✓" else "Message sent directly ✓", Toast.LENGTH_SHORT).show()
                            if (mode == "messages" && intent.getIntExtra(EXTRA_SLOT, 0) == 0) showFamily() else finish()
                        } else {
                            val msg = error ?: if (isHi) "संदेश भेजा नहीं जा सका (ड्राफ्ट सुरक्षित है)" else "Could not send SMS (draft preserved)"
                            Toast.makeText(this@CommunicationActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                    return@setPositiveButton
                }
                val target = when (kind) {
                    "sms" -> CommunicationIntents.sms(this, member.canonicalNumber!!, text)
                    else -> CommunicationIntents.whatsApp(member.canonicalNumber!!, text)
                }
                try {
                    saveDraft(); startActivity(target)
                    Toast.makeText(this, tr(R.string.communication_handoff_only), Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, tr(if (kind == "whatsapp") R.string.communication_whatsapp_missing else R.string.communication_app_missing), Toast.LENGTH_LONG).show()
                }
            }.show()
    }
    @Deprecated("Platform compatibility")
    override fun onBackPressed() {
        when (screen) {
            "compose" -> if (mode == "messages" && intent.getIntExtra(EXTRA_SLOT, 0) == 0) showFamily() else showPerson()
            "person" -> showFamily()
            else -> super.onBackPressed()
        }
    }
}
