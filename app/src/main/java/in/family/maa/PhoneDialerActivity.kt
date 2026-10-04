package `in`.family.maa

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/** Accessible number entry that leaves actual call placement with the selected system dialer. */
class PhoneDialerActivity : CommunicationUiActivity() {
    private lateinit var number: EditText
    private var initialName = ""
    private var initialNumber = ""
    companion object {
        fun intent(context: Context, number: String = "", name: String = "") = Intent(context, PhoneDialerActivity::class.java)
            .putExtra("number", number).putExtra("name", name)
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        initialName = state?.getString("name") ?: intent.getStringExtra("name").orEmpty()
        initialNumber = state?.getString("initialNumber") ?: intent.getStringExtra("number").orEmpty()
        surface(tr(R.string.communication_keypad))
        if (initialName.isNotBlank()) content.addView(label(initialName, 27f))
        content.addView(label(tr(R.string.communication_dial_explained), 17f))
        number = EditText(this).apply {
            id = R.id.communication_dial_number; textSize = 31f; setTextColor(ink); gravity = Gravity.CENTER
            background = shape(Color.WHITE, true); minimumHeight = dp(80)
            inputType = InputType.TYPE_CLASS_PHONE; filters = arrayOf(InputFilter.LengthFilter(64))
            setPadding(dp(12), dp(16), dp(12), dp(16)); textDirection = View.TEXT_DIRECTION_LTR
            hint = tr(R.string.communication_phone_number); contentDescription = tr(R.string.communication_phone_number)
            showSoftInputOnFocus = false
            setText(state?.getString("number") ?: initialNumber)
            setSelection(text.length)
        }
        content.addView(number, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("+", "0", "⌫")).forEach { digits ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (digit in digits) row.addView(Button(this).apply {
                text = digit; textSize = 32f; isAllCaps = false; setTextColor(ink)
                background = shape(Color.WHITE, true); minimumHeight = dp(76)
                contentDescription = if (digit == "⌫") tr(R.string.communication_delete_digit) else digit
                setOnClickListener {
                    val start = number.selectionStart.coerceAtLeast(0)
                    val end = number.selectionEnd.coerceAtLeast(start)
                    when {
                        digit != "⌫" -> number.text.replace(start, end, digit)
                        start != end -> number.text.delete(start, end)
                        start > 0 -> number.text.delete(start - 1, start)
                    }
                }
                if (digit == "⌫") setOnLongClickListener { number.setText(""); true }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), dp(9)) })
            content.addView(row)
        }
        action(tr(R.string.communication_review_call), R.id.communication_dial_review) { review() }
        action(tr(R.string.communication_device_contacts), secondary = true) { startActivity(PhoneContactsActivity.intent(this)) }
        action(tr(R.string.communication_system_history), secondary = true) {
            handoff(Intent(Intent.ACTION_VIEW).setType(CallLog.Calls.CONTENT_TYPE))
        }
        action(tr(R.string.communication_open_phone), secondary = true) { handoff(Intent(Intent.ACTION_DIAL)) }
    }
    @Suppress("DEPRECATION")
    private fun review() {
        val raw = number.text.toString()
        val canonical = FamilyDirectory.canonicalNumber(raw)
        val emergency = raw.matches(Regex("[0-9]{2,6}")) && try {
            PhoneNumberUtils.isLocalEmergencyNumber(this, raw)
        } catch (_: Exception) { false }
        val target = canonical ?: raw.takeIf { emergency }
        if (target == null) {
            number.error = tr(R.string.communication_invalid_number); return
        }
        val name = initialName.takeIf { FamilyDirectory.canonicalNumber(initialNumber) == canonical && canonical != null }.orEmpty()
        AlertDialog.Builder(this).setTitle(R.string.communication_review_call)
            .setMessage(listOf(name, target, tr(R.string.communication_call_confirmation)).filter { it.isNotBlank() }.joinToString("\n\n"))
            .setNegativeButton(R.string.communication_cancel, null)
            .setPositiveButton(R.string.communication_continue) { _, _ ->
                if (number.text.toString() == raw) handoff(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", target, null)))
            }.show()
    }
    private fun handoff(intent: Intent) {
        try {
            startActivity(intent)
            Toast.makeText(this, tr(R.string.communication_handoff_only), Toast.LENGTH_LONG).show()
        } catch (_: Exception) { Toast.makeText(this, tr(R.string.communication_app_missing), Toast.LENGTH_LONG).show() }
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putString("number", number.text.toString()); state.putString("name", initialName)
        state.putString("initialNumber", initialNumber)
        super.onSaveInstanceState(state)
    }
}
