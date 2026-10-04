package `in`.family.maa

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
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
    private fun requireNumber(number: String) = require(number.matches(Regex("\\+[1-9][0-9]{6,14}")))
    fun dial(number: String): Intent {
        requireNumber(number)
        return Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
    }
    fun sms(context: Context, number: String, text: String): Intent {
        requireNumber(number)
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).apply {
            putExtra("sms_body", text)
            Telephony.Sms.getDefaultSmsPackage(context)?.let { setPackage(it) }
        }
    }
    fun whatsApp(number: String, text: String): Intent {
        requireNumber(number)
        val uri = Uri.Builder().scheme("https").authority("wa.me").appendPath(number.drop(1))
            .apply { if (text.isNotEmpty()) appendQueryParameter("text", text) }.build()
        return Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
    }
}

/** Owned family selection and message drafts; external apps retain final send/call authority. */
class CommunicationActivity : CommunicationUiActivity() {
    private var person: FamilyDirectory.Person? = null
    private var screen = "family"
    private var channel = "sms"
    private var editor: EditText? = null
    private var notice = ""
    companion object {
        const val EXTRA_SLOT = "family_slot"
        const val EXTRA_CHANNEL = "draft_channel"
        fun intent(context: Context, slot: Int = 0) = Intent(context, CommunicationActivity::class.java)
            .putExtra(EXTRA_SLOT, slot)
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val slot = state?.getInt("slot") ?: intent.getIntExtra(EXTRA_SLOT, 0)
        person = FamilyDirectory.read(this).firstOrNull { it.slot == slot }
        channel = (state?.getString("channel") ?: intent.getStringExtra(EXTRA_CHANNEL))
            .takeIf { it == "whatsapp" } ?: "sms"
        val requestedCompose = state?.getString("screen") == "compose" || intent.hasExtra(EXTRA_CHANNEL)
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
        surface(tr(R.string.communication_family))
        for (member in FamilyDirectory.read(this)) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = shape(Color.WHITE, true); setPadding(dp(16), dp(16), dp(16), dp(16))
                minimumHeight = dp(120); isClickable = true; isFocusable = true
                contentDescription = member.name + ". " + (member.canonicalNumber ?: tr(R.string.communication_setup_needed))
                setOnClickListener { person = member; notice = ""; showPerson() }
                addView(avatar(member.name, member.photoUri))
                addView(LinearLayout(this@CommunicationActivity).apply {
                    orientation = LinearLayout.VERTICAL; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    addView(label(member.name, 25f).apply { setTypeface(null, Typeface.BOLD) })
                    if (!member.actionable) addView(label(issueText(member), 17f))
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
            }
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        }
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
        if (!member.actionable) return showPerson()
        editor = null; screen = "compose"
        surface(tr(if (channel == "sms") R.string.communication_sms else R.string.communication_whatsapp), ::showPerson)
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
                val target = when (kind) {
                    "call" -> CommunicationIntents.dial(member.canonicalNumber!!)
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
        when (screen) { "compose" -> showPerson(); "person" -> showFamily(); else -> super.onBackPressed() }
    }
}
