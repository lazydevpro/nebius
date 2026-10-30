package `in`.family.maa

import android.content.Context
import android.telephony.PhoneNumberUtils
import java.util.Locale

/** Reads the original family preferences. No implicit migration or contact upload. */
object FamilyDirectory {
    enum class Issue { MISSING_NUMBER, INVALID_NUMBER, DUPLICATE_NUMBER }
    data class Person(
        val slot: Int,
        val name: String,
        val rawNumber: String,
        val canonicalNumber: String?,
        val photoUri: String,
        val issue: Issue?
    ) {
        val actionable: Boolean get() = issue == null && canonicalNumber != null
    }

    fun read(context: Context): List<Person> {
        val snapshot = context.getSharedPreferences("family", Context.MODE_PRIVATE).all
        val local = AppLanguage.localizedContext(context)
        val entries = (1..3).map { slot ->
            val raw = (snapshot["phone$slot"] as? String).orEmpty().trim()
            val canonical = canonicalNumber(raw)
            Person(
                slot,
                (snapshot["name$slot"] as? String).orEmpty().trim().ifBlank {
                    local.getString(R.string.communication_family_member, slot)
                },
                raw,
                canonical,
                (snapshot["photo$slot"] as? String).orEmpty(),
                when {
                    raw.isBlank() -> Issue.MISSING_NUMBER
                    canonical == null -> Issue.INVALID_NUMBER
                    else -> null
                }
            )
        }
        val duplicates = entries.mapNotNull { it.canonicalNumber }.groupingBy { it }.eachCount()
        return entries.map {
            if (it.canonicalNumber != null && (duplicates[it.canonicalNumber] ?: 0) > 1)
                it.copy(issue = Issue.DUPLICATE_NUMBER) else it
        }
    }

    /** Number formatting is permissive; dial sequences, extensions, URLs and partial numbers are not. */
    fun canonicalNumber(input: String, region: String = "IN"): String? {
        if (input.isBlank() || input.length > 64) return null
        val normalized = StringBuilder()
        for (char in input) {
            val digit = Character.digit(char, 10)
            when {
                digit >= 0 -> normalized.append(digit)
                char == '+' && normalized.isEmpty() -> normalized.append(char)
                char == ' ' || char == '\u00a0' || char == '\u202f' || char == '(' || char == ')' || char == '-' -> Unit
                else -> return null
            }
        }
        val value = normalized.toString()
        if (!value.matches(Regex("\\+?[0-9]{7,15}"))) return null
        val country = region.uppercase(Locale.ROOT).takeIf { it.matches(Regex("[A-Z]{2}")) } ?: return null
        val e164 = PhoneNumberUtils.formatNumberToE164(value, country) ?: return null
        return e164.takeIf { it.matches(Regex("\\+[1-9][0-9]{6,14}")) }
    }

    /** Confirmation is invalid when a caregiver edits a recipient while the screen is open. */
    fun stillMatches(context: Context, expected: Person): Boolean =
        read(context).firstOrNull { it.slot == expected.slot }?.let {
            it.actionable && expected.actionable && it.name == expected.name &&
                it.canonicalNumber == expected.canonicalNumber
        } == true

    /** Resolves an entity query (name, relationship alias, slot or phone number) to an actionable person. */
    fun findMatch(context: Context, query: String?): Person? {
        if (query.isNullOrBlank()) return null
        val trimmed = query.trim().lowercase(Locale.ROOT)
        val family = read(context).filter { it.actionable }

        // 1. Exact match on family member name
        family.firstOrNull { it.name.trim().lowercase(Locale.ROOT) == trimmed }?.let { return it }

        // 2. Substring match
        family.firstOrNull {
            val nameLower = it.name.trim().lowercase(Locale.ROOT)
            nameLower.isNotEmpty() && (nameLower.contains(trimmed) || trimmed.contains(nameLower))
        }?.let { return it }

        // 3. Spoken digit or slot number
        val slot = when {
            trimmed.contains("1") || trimmed.contains("one") || trimmed.contains("first") || trimmed.contains("पहला") -> 1
            trimmed.contains("2") || trimmed.contains("two") || trimmed.contains("second") || trimmed.contains("दूसरा") -> 2
            trimmed.contains("3") || trimmed.contains("three") || trimmed.contains("third") || trimmed.contains("तीसरा") -> 3
            else -> null
        }
        if (slot != null) {
            family.firstOrNull { it.slot == slot }?.let { return it }
        }

        // 4. Common relationship aliases
        val aliases = listOf(
            listOf("son", "beta", "बेटा", "लड़का"),
            listOf("daughter", "beti", "बेटी", "लड़की"),
            listOf("brother", "bhai", "भैया", "भाई"),
            listOf("sister", "didi", "दीदी", "बहन"),
            listOf("father", "papa", "dad", "पिता", "पापा"),
            listOf("mother", "mummy", "mom", "माँ", "मम्मी", "माता"),
            listOf("doctor", "डॉक्टर")
        )
        for (person in family) {
            val nameLower = person.name.trim().lowercase(Locale.ROOT)
            for (synonyms in aliases) {
                if (synonyms.any { it in trimmed } && synonyms.any { it in nameLower }) {
                    return person
                }
            }
        }

        // 5. If query itself is a valid phone number
        val canonical = canonicalNumber(query)
        if (canonical != null) {
            return Person(
                slot = 0,
                name = query,
                rawNumber = query,
                canonicalNumber = canonical,
                photoUri = "",
                issue = null
            )
        }

        return null
    }
}

object CommunicationDrafts {
    const val MAX_LENGTH = 2000
    private fun key(number: String, channel: String): String {
        require(number.matches(Regex("\\+[1-9][0-9]{6,14}")))
        require(channel == "sms" || channel == "whatsapp")
        return "$channel:$number"
    }
    fun read(context: Context, number: String, channel: String): String =
        context.getSharedPreferences("communication_drafts", Context.MODE_PRIVATE)
            .getString(key(number, channel), "").orEmpty().take(MAX_LENGTH)
    fun save(context: Context, number: String, channel: String, text: String) {
        context.getSharedPreferences("communication_drafts", Context.MODE_PRIVATE).edit()
            .putString(key(number, channel), text.take(MAX_LENGTH)).apply()
    }
    fun clear(context: Context, number: String, channel: String) {
        context.getSharedPreferences("communication_drafts", Context.MODE_PRIVATE).edit()
            .remove(key(number, channel)).apply()
    }
}
