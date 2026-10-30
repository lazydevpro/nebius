package `in`.family.maa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Displays dedicated list of missed calls with extra-large accessible text and one-tap call back. */
class MissedCallsActivity : CommunicationUiActivity() {
    companion object {
        private const val REQUEST_CALL_LOG = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface(tr(R.string.missed_calls_title))
        loadMissedCalls()
    }

    override fun onResume() {
        super.onResume()
        ProactiveAsha.markMissedCallsViewed(this)
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            loadMissedCalls()
        }
    }

    private fun loadMissedCalls() {
        content.removeAllViews()

        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            renderPermissionRequired()
            return
        }

        val familyMembers = FamilyDirectory.read(this)
        val missedCalls = queryMissedCalls(familyMembers)

        if (missedCalls.isEmpty()) {
            renderEmptyState()
            return
        }

        missedCalls.forEach { item ->
            renderMissedCallCard(item)
        }
    }

    private data class MissedCallItem(
        val displayName: String,
        val rawNumber: String,
        val formattedTime: String
    )

    private fun queryMissedCalls(family: List<FamilyDirectory.Person>): List<MissedCallItem> {
        val list = mutableListOf<MissedCallItem>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE
        )
        val selection = "${CallLog.Calls.TYPE} = ?"
        val selectionArgs = arrayOf(CallLog.Calls.MISSED_TYPE.toString())
        val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 50"

        try {
            contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)

                while (cursor.moveToNext()) {
                    val rawNumber = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty().trim() else ""
                    val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val dateMillis = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L

                    val canonical = FamilyDirectory.canonicalNumber(rawNumber)
                    val familyMatch = family.firstOrNull {
                        it.actionable && ((canonical != null && it.canonicalNumber == canonical) || it.rawNumber == rawNumber)
                    }

                    val displayName = when {
                        familyMatch != null -> familyMatch.name
                        cachedName.isNotBlank() -> cachedName
                        rawNumber.isNotBlank() -> rawNumber
                        else -> tr(R.string.unknown_caller)
                    }

                    val relativeTime = if (dateMillis > 0) {
                        DateUtils.getRelativeTimeSpanString(
                            dateMillis,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE
                        ).toString()
                    } else ""

                    list.add(MissedCallItem(displayName, rawNumber, relativeTime))
                }
            }
        } catch (_: Exception) {
            // Content resolver query failure handled gracefully
        }
        return list
    }

    private fun renderPermissionRequired() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.WHITE, true)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        }

        val prompt = TextView(this).apply {
            text = tr(R.string.missed_calls_permission_needed)
            textSize = 22f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(16))
        }
        card.addView(prompt)

        val allowButton = Button(this).apply {
            text = tr(R.string.missed_calls_allow)
            textSize = 21f
            isAllCaps = false
            minHeight = dp(64)
            minimumHeight = dp(64)
            setTextColor(Color.WHITE)
            background = shape(green)
            stateListAnimator = null
            elevation = dp(2).toFloat()
            setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), REQUEST_CALL_LOG)
            }
        }
        card.addView(allowButton)
        content.addView(card)
    }

    private fun renderEmptyState() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = shape(Color.WHITE, true)
            setPadding(dp(24), dp(36), dp(24), dp(36))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(16) }
            setImageDrawable(MaaIcon(this@MissedCallsActivity, "phone", green))
        }
        card.addView(icon)

        val text = TextView(this).apply {
            text = tr(R.string.missed_calls_empty)
            textSize = 24f
            setTextColor(green)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        card.addView(text)
        content.addView(card)
    }

    private fun renderMissedCallCard(item: MissedCallItem) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.WHITE, true)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(2).toFloat()
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        }

        // Top Row: Red missed call badge + Caller Name
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val badge = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xfffde8e8.toInt())
            }
            setImageDrawable(MaaIcon(this@MissedCallsActivity, "missed_call", 0xffc5221f.toInt()))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        topRow.addView(badge)

        val nameView = TextView(this).apply {
            text = item.displayName
            textSize = 26f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        topRow.addView(nameView)
        card.addView(topRow)

        // Subtitle row: Number & Time
        val subRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, dp(12))
        }

        if (item.rawNumber.isNotBlank() && item.displayName != item.rawNumber) {
            val numView = TextView(this).apply {
                text = item.rawNumber
                textSize = 18f
                setTextColor(MaaDesign.muted)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            subRow.addView(numView)
        }

        if (item.formattedTime.isNotBlank()) {
            val timeView = TextView(this).apply {
                text = "⏱ " + item.formattedTime
                textSize = 17f
                setTextColor(Color.rgb(180, 40, 40))
                setTypeface(typeface, Typeface.BOLD)
            }
            subRow.addView(timeView)
        }
        card.addView(subRow)

        // Call Back button
        if (item.rawNumber.isNotBlank()) {
            val callBackButton = Button(this).apply {
                text = "📞  " + tr(R.string.missed_calls_call_back)
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                isAllCaps = false
                minHeight = dp(64)
                minimumHeight = dp(64)
                setTextColor(Color.WHITE)
                background = shape(green)
                stateListAnimator = null
                elevation = dp(2).toFloat()
                setOnClickListener {
                    val canonical = FamilyDirectory.canonicalNumber(item.rawNumber)
                    val dialIntent = if (canonical != null) {
                        CommunicationIntents.dial(canonical)
                    } else {
                        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", item.rawNumber, null))
                    }
                    try {
                        startActivity(dialIntent)
                    } catch (_: Exception) {
                        Toast.makeText(this@MissedCallsActivity, tr(R.string.app_unavailable), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            card.addView(callBackButton)
        }

        content.addView(card)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_LOG) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMissedCalls()
            } else {
                Toast.makeText(this, tr(R.string.missed_calls_permission_needed), Toast.LENGTH_LONG).show()
            }
        }
    }
}
