package `in`.family.maa

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Medicine reminder manager for elderly care.
 * Tracks daily medicine schedule, computes real-time countdowns based on local time,
 * allows user/caregiver to configure medicines, detects when a dose is due,
 * and marks confirmations with voice or 1-tap.
 */
object MedicineManager {
    private const val PREFS_NAME = "medicine_tracker"
    private const val KEY_MEDICINES = "custom_medicines"

    data class MedicineItem(
        val id: String = UUID.randomUUID().toString(),
        val nameHi: String,
        val nameEn: String,
        val hour: Int,          // 0..23
        val minute: Int,        // 0..59
        val instructionHi: String, // e.g. "नाश्ते के बाद"
        val instructionEn: String, // e.g. "After breakfast"
        val enabled: Boolean = true
    ) {
        fun name(context: Context): String = if (AppLanguage.isHindi(context)) nameHi else nameEn
        fun instruction(context: Context): String = if (AppLanguage.isHindi(context)) instructionHi else instructionEn

        fun formattedTime(context: Context): String {
            val isHi = AppLanguage.isHindi(context)
            val period = when {
                hour < 12 -> if (isHi) "सुबह" else "AM"
                hour < 16 -> if (isHi) "दोपहर" else "PM"
                hour < 20 -> if (isHi) "शाम" else "PM"
                else -> if (isHi) "रात" else "PM"
            }
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            val minStr = if (minute < 10) "0$minute" else "$minute"
            return if (isHi) {
                "$period $displayHour:$minStr बजे"
            } else {
                "$displayHour:$minStr $period"
            }
        }
    }

    private val DEFAULT_MEDICINES = listOf(
        MedicineItem(
            id = "med_morning",
            nameHi = "बीपी की गोली",
            nameEn = "Blood Pressure Tablet",
            hour = 8,
            minute = 30,
            instructionHi = "नाश्ते के बाद",
            instructionEn = "After breakfast"
        ),
        MedicineItem(
            id = "med_afternoon",
            nameHi = "मल्टीविटामिन / दोपहर की दवा",
            nameEn = "Multivitamin Tablet",
            hour = 14,
            minute = 0,
            instructionHi = "दोपहर के खाने के बाद",
            instructionEn = "After lunch"
        ),
        MedicineItem(
            id = "med_night",
            nameHi = "शुगर / रात की दवा",
            nameEn = "Sugar / Night Medicine",
            hour = 20,
            minute = 30,
            instructionHi = "रात के खाने के बाद",
            instructionEn = "After dinner"
        )
    )

    fun all(context: Context): List<MedicineItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MEDICINES, null) ?: return DEFAULT_MEDICINES
        return try {
            val arr = JSONArray(raw)
            if (arr.length() == 0) return DEFAULT_MEDICINES
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.getJSONObject(idx)
                MedicineItem(
                    id = obj.getString("id"),
                    nameHi = obj.getString("nameHi"),
                    nameEn = obj.getString("nameEn"),
                    hour = obj.getInt("hour"),
                    minute = obj.getInt("minute"),
                    instructionHi = obj.optString("instructionHi", "खाने के बाद"),
                    instructionEn = obj.optString("instructionEn", "After meal"),
                    enabled = obj.optBoolean("enabled", true)
                )
            }.sortedBy { it.hour * 60 + it.minute }
        } catch (_: Exception) {
            DEFAULT_MEDICINES
        }
    }

    fun saveAll(context: Context, medicines: List<MedicineItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        medicines.forEach { med ->
            val obj = JSONObject().apply {
                put("id", med.id)
                put("nameHi", med.nameHi)
                put("nameEn", med.nameEn)
                put("hour", med.hour)
                put("minute", med.minute)
                put("instructionHi", med.instructionHi)
                put("instructionEn", med.instructionEn)
                put("enabled", med.enabled)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_MEDICINES, arr.toString()).apply()
        MedicineAlarmEngine.scheduleNextAlarm(context)
        ProactiveAsha.notifyUpdatesChanged()
    }

    fun addOrUpdate(context: Context, item: MedicineItem) {
        val list = all(context).toMutableList()
        val index = list.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            list[index] = item
        } else {
            list.add(item)
        }
        saveAll(context, list)
    }

    fun delete(context: Context, id: String) {
        val list = all(context).filterNot { it.id == id }
        saveAll(context, list)
    }

    private fun todayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun isTakenToday(context: Context, id: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("last_taken_date_$id", "") == todayDateString()
    }

    fun markConfirmed(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_taken_date_$id", todayDateString())
            .putLong("last_taken_time_$id", System.currentTimeMillis())
            .remove("snooze_until_$id")
            .apply()
        MedicineAlarmEngine.scheduleNextAlarm(context)
        ProactiveAsha.notifyUpdatesChanged()
    }

    fun snoozeMedicine(context: Context, id: String, minutes: Int = 15) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("snooze_until_$id", System.currentTimeMillis() + minutes * 60 * 1000L)
            .apply()
        MedicineAlarmEngine.scheduleNextAlarm(context)
        ProactiveAsha.notifyUpdatesChanged()
    }

    private fun isSnoozed(context: Context, id: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val until = prefs.getLong("snooze_until_$id", 0L)
        return System.currentTimeMillis() < until
    }

    /**
     * Determines if a medicine is currently due.
     * A medicine is due if:
     * 1. It is enabled and not yet taken today.
     * 2. Current local time is within 30 minutes before scheduled time or up to 2 hours after.
     * 3. It is not currently snoozed.
     */
    fun getDueMedicine(context: Context): MedicineItem? {
        val list = all(context).filter { it.enabled && !isTakenToday(context, it.id) && !isSnoozed(context, it.id) }
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (med in list) {
            val medMinutes = med.hour * 60 + med.minute
            val diff = nowMinutes - medMinutes
            // Due window: 30 minutes before up to 120 minutes overdue
            if (diff in -30..120) {
                return med
            }
        }
        return null
    }

    fun hasDueMedicine(context: Context): Boolean = getDueMedicine(context) != null

    data class MedicineStatus(
        val medicine: MedicineItem?,
        val isDue: Boolean,
        val allTaken: Boolean,
        val title: String,
        val countdownText: String
    )

    fun getNextMedicineStatus(context: Context): MedicineStatus {
        val isHi = AppLanguage.isHindi(context)
        val medicines = all(context).filter { it.enabled }
        if (medicines.isEmpty()) {
            return MedicineStatus(
                medicine = null,
                isDue = false,
                allTaken = true,
                title = if (isHi) "कोई दवा निर्धारित नहीं है" else "No medicines scheduled",
                countdownText = ""
            )
        }

        // 1. Check if there is an active DUE medicine
        val dueMed = getDueMedicine(context)
        if (dueMed != null) {
            val now = Calendar.getInstance()
            val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val medMinutes = dueMed.hour * 60 + dueMed.minute
            val diff = nowMinutes - medMinutes

            val countdown = when {
                diff > 0 -> if (isHi) "⚠️ $diff मिनट पहले का समय था · अभी लें (${dueMed.instruction(context)})"
                            else "⚠️ $diff mins overdue · Take now (${dueMed.instruction(context)})"
                diff == 0 -> if (isHi) "⚠️ अभी लेने का समय है (${dueMed.instruction(context)})"
                             else "⚠️ Time to take now (${dueMed.instruction(context)})"
                else -> {
                    val remaining = -diff
                    if (isHi) "⚠️ $remaining मिनट में लेने का समय (${dueMed.instruction(context)})"
                    else "⚠️ Due in $remaining mins (${dueMed.instruction(context)})"
                }
            }

            return MedicineStatus(
                medicine = dueMed,
                isDue = true,
                allTaken = false,
                title = dueMed.name(context),
                countdownText = if (isHi) "अभी लेने का समय (${dueMed.formattedTime(context)})"
                                else "Time to take now (${dueMed.formattedTime(context)})"
            )
        }

        // 2. Check for upcoming or missed medicine today
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val pendingToday = medicines.filter { !isTakenToday(context, it.id) }

        if (pendingToday.isEmpty()) {
            // All medicines for today taken!
            val firstMed = medicines.minByOrNull { it.hour * 60 + it.minute } ?: medicines.first()
            return MedicineStatus(
                medicine = null,
                isDue = false,
                allTaken = true,
                title = if (isHi) "✓ आज की सभी दवाएँ पूरी हुईं" else "✓ All medicines taken today",
                countdownText = if (isHi) "अगली दवा: कल ${firstMed.formattedTime(context)}"
                                else "Next: Tomorrow at ${firstMed.formattedTime(context)}"
            )
        }

        // Find closest upcoming medicine today (scheduled >= nowMinutes)
        val upcoming = pendingToday.firstOrNull { (it.hour * 60 + it.minute) >= nowMinutes }
            ?: pendingToday.first()

        return MedicineStatus(
            medicine = upcoming,
            isDue = false,
            allTaken = false,
            title = upcoming.name(context),
            countdownText = if (isHi) "अगली दवा: ${upcoming.formattedTime(context)}"
                            else "Next: ${upcoming.formattedTime(context)}"
        )
    }

    /**
     * Accessible dialog allowing user or caregiver to setup/add/edit medicines.
     */
    fun showSetupDialog(activity: Activity, onUpdated: () -> Unit = {}) {
        val isHi = AppLanguage.isHindi(activity)
        val dp = { n: Int -> (n * activity.resources.displayMetrics.density).toInt() }

        var currentDialog: AlertDialog? = null

        fun renderMedicineList(container: LinearLayout) {
            container.removeAllViews()
            val list = all(activity)

            val header = TextView(activity).apply {
                text = if (isHi) "दवाइयों का समय (Medicine Schedule)" else "Medicine Schedule Setup"
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(15, 119, 113))
                setPadding(0, 0, 0, dp(12))
            }
            container.addView(header)

            val hint = TextView(activity).apply {
                text = if (isHi) "यहाँ से आप दवा का नाम, लेने का समय और खाने के निर्देश बदल सकते हैं:"
                       else "Configure medicine names, scheduled times, and meal instructions:"
                textSize = 16f
                setTextColor(Color.rgb(75, 85, 99))
                setPadding(0, 0, 0, dp(16))
            }
            container.addView(hint)

            list.forEach { med ->
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(1), Color.rgb(229, 231, 235))
                    }
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                }

                val icon = ImageView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.rgb(240, 253, 250))
                    }
                    setImageDrawable(MaaIcon(activity, "pill", Color.rgb(15, 119, 113)))
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                }
                card.addView(icon)

                val col = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }

                val name = TextView(activity).apply {
                    text = med.name(activity)
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(17, 24, 39))
                }
                col.addView(name)

                val sub = TextView(activity).apply {
                    text = "${med.formattedTime(activity)} · ${med.instruction(activity)}"
                    textSize = 15f
                    setTextColor(Color.rgb(107, 114, 128))
                }
                col.addView(sub)
                card.addView(col)

                // Edit button
                val editBtn = Button(activity).apply {
                    text = "✏️"
                    textSize = 18f
                    minWidth = dp(44); minimumWidth = dp(44)
                    minHeight = dp(44); minimumHeight = dp(44)
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(243, 244, 246))
                        cornerRadius = dp(12).toFloat()
                    }
                    setOnClickListener {
                        showEditMedicineDialog(activity, med) {
                            renderMedicineList(container)
                            onUpdated()
                        }
                    }
                }
                card.addView(editBtn)

                // Delete button
                val deleteBtn = Button(activity).apply {
                    text = "🗑️"
                    textSize = 16f
                    minWidth = dp(44); minimumWidth = dp(44)
                    minHeight = dp(44); minimumHeight = dp(44)
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(254, 242, 242))
                        cornerRadius = dp(12).toFloat()
                    }
                    (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(6)
                    setOnClickListener {
                        delete(activity, med.id)
                        renderMedicineList(container)
                        onUpdated()
                    }
                }
                val lp = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) }
                card.addView(deleteBtn, lp)

                container.addView(card)
            }

            // Add Medicine button
            val addBtn = Button(activity).apply {
                text = "+ " + if (isHi) "नई दवा जोड़ें" else "Add New Medicine"
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(15, 119, 113))
                    cornerRadius = dp(18).toFloat()
                }
                minHeight = dp(54); minimumHeight = dp(54)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) }
                setOnClickListener {
                    showEditMedicineDialog(activity, null) {
                        renderMedicineList(container)
                        onUpdated()
                    }
                }
            }
            container.addView(addBtn)
        }

        val scrollContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            setBackgroundColor(Color.rgb(250, 249, 246))
        }
        renderMedicineList(scrollContainer)

        val dialog = AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(scrollContainer) })
            .setPositiveButton(if (isHi) "पूर्ण / Done" else "Done") { d, _ ->
                d.dismiss()
                onUpdated()
            }
            .create()
        currentDialog = dialog
        dialog.show()
    }

    /** Opens the normal add dialog with the scanned name filled in, so the family only sets the time. */
    fun addFromScan(activity: Activity, name: String, onSaved: () -> Unit) =
        showEditMedicineDialog(activity, null, name, onSaved)

    private fun showEditMedicineDialog(activity: Activity, existing: MedicineItem?, suggestedName: String = "", onSaved: () -> Unit) {
        val isHi = AppLanguage.isHindi(activity)
        val dp = { n: Int -> (n * activity.resources.displayMetrics.density).toInt() }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val title = TextView(activity).apply {
            text = if (existing == null) (if (isHi) "नई दवा जोड़ें" else "Add New Medicine") else (if (isHi) "दवा बदलें" else "Edit Medicine")
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(15, 119, 113))
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(title)

        // Medicine Name
        val nameLabel = TextView(activity).apply {
            text = if (isHi) "दवा का नाम:" else "Medicine Name:"
            textSize = 16f
            setTextColor(Color.rgb(55, 65, 81))
        }
        root.addView(nameLabel)

        val nameEdit = EditText(activity).apply {
            hint = if (isHi) "उदा. बीपी की गोली" else "e.g. Blood Pressure Tablet"
            setText(existing?.nameHi ?: suggestedName)
            textSize = 19f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(12) }
        }
        root.addView(nameEdit)

        // Time Picker
        val timeLabel = TextView(activity).apply {
            text = if (isHi) "दवा लेने का समय:" else "Scheduled Time:"
            textSize = 16f
            setTextColor(Color.rgb(55, 65, 81))
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(timeLabel)

        val timePicker = TimePicker(activity).apply {
            setIs24HourView(android.text.format.DateFormat.is24HourFormat(activity))
            hour = existing?.hour ?: 8
            minute = existing?.minute ?: 30
        }
        root.addView(timePicker)

        // Meal instruction selection
        val mealLabel = TextView(activity).apply {
            text = if (isHi) "खाने के निर्देश:" else "Meal Instructions:"
            textSize = 16f
            setTextColor(Color.rgb(55, 65, 81))
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(mealLabel)

        val instructions = if (isHi) listOf("नाश्ते के बाद", "दोपहर के खाने के बाद", "रात के खाने के बाद", "खाने से पहले", "खाली पेट")
                           else listOf("After breakfast", "After lunch", "After dinner", "Before food", "Empty stomach")

        var selectedInstruction = existing?.instructionHi ?: instructions.first()

        val chipGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(12) }
        }

        val scrollChips = HorizontalScrollView(activity).apply {
            isFillViewport = false
            addView(chipGroup)
        }

        val chipButtons = mutableListOf<Button>()
        instructions.forEach { instr ->
            val isSelected = (instr == selectedInstruction)
            val btn = Button(activity).apply {
                text = instr
                textSize = 14f
                isAllCaps = false
                setTextColor(if (isSelected) Color.WHITE else Color.rgb(55, 65, 81))
                background = GradientDrawable().apply {
                    setColor(if (isSelected) Color.rgb(15, 119, 113) else Color.rgb(243, 244, 246))
                    cornerRadius = dp(14).toFloat()
                }
                minHeight = dp(40); minimumHeight = dp(40)
                setPadding(dp(12), 0, dp(12), 0)
                layoutParams = LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(6) }
                setOnClickListener {
                    selectedInstruction = instr
                    chipButtons.forEach { b ->
                        val sel = (b.text == selectedInstruction)
                        b.setTextColor(if (sel) Color.WHITE else Color.rgb(55, 65, 81))
                        b.background = GradientDrawable().apply {
                            setColor(if (sel) Color.rgb(15, 119, 113) else Color.rgb(243, 244, 246))
                            cornerRadius = dp(14).toFloat()
                        }
                    }
                }
            }
            chipButtons.add(btn)
            chipGroup.addView(btn)
        }
        root.addView(scrollChips)

        AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton(if (isHi) "सहेजें / Save" else "Save") { dialog, _ ->
                val medName = nameEdit.text.toString().trim().ifBlank {
                    if (isHi) "दवा" else "Medicine"
                }
                val newItem = MedicineItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    nameHi = medName,
                    nameEn = medName,
                    hour = timePicker.hour,
                    minute = timePicker.minute,
                    instructionHi = selectedInstruction,
                    instructionEn = selectedInstruction,
                    enabled = true
                )
                addOrUpdate(activity, newItem)
                dialog.dismiss()
                onSaved()
            }
            .setNegativeButton(if (isHi) "रद्द करें" else "Cancel", null)
            .show()
    }
}
