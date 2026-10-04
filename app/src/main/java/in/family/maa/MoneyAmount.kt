package `in`.family.maa

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

/** Whole-expression money parser. Unknown or conflicting speech is a request to ask again. */
object MoneyAmount {
    private val maximum = BigDecimal("100000")
    private val rupee = "(?:rupees?|रुपये|रुपए|रुपया|रुपयों)"
    private val paise = "(?:paise|paisa|पैसे|पैसा)"
    private val englishUnits = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        .mapIndexed { index, word -> word to index }.toMap()
    private val englishTens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    private val hindiUnits: Map<String, Int> = buildMap {
        val words = listOf(
            "शून्य", "एक", "दो", "तीन", "चार", "पांच", "छह", "सात", "आठ", "नौ",
            "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस",
            "बीस", "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस",
            "तीस", "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस",
            "चालीस", "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास",
            "पचास", "इक्यावन", "बावन", "तिरपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ",
            "साठ", "इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनहत्तर",
            "सत्तर", "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्नासी",
            "अस्सी", "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सतासी", "अट्ठासी", "नवासी",
            "नब्बे", "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पंचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे")
        words.forEachIndexed { value, word -> put(Normalizer.normalize(word, Normalizer.Form.NFKC), value) }
        mapOf("पाँच" to 5, "छः" to 6, "पन्द्रह" to 15, "बाइस" to 22, "अट्ठाइस" to 28,
            "चौवालीस" to 44, "जीरो" to 0, "ज़ीरो" to 0).forEach { (word, value) ->
            put(Normalizer.normalize(word, Normalizer.Form.NFKC), value)
        }
    }
    private val englishScales = mapOf("thousand" to 1000, "lakh" to 100000, "lac" to 100000, "million" to 1000000, "crore" to 10000000)
    private val hindiScales = mapOf("हजार" to 1000, "हज़ार" to 1000, "लाख" to 100000, "करोड़" to 10000000)

    /** Range is greater than zero through ₹100,000; precision is at most two places. */
    fun parse(raw: String): BigDecimal? {
        if (raw.isBlank() || raw.length > 160 || raw.any { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() }) return null
        var text = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .map { char -> Character.digit(char, 10).takeIf { it >= 0 }?.let { ('0'.code + it).toChar() } ?: char }.joinToString("")
            .replace(Regex("(?<=[a-z])-(?=[a-z])"), " ")
            .trim().replace(Regex("[\\s\\u00a0\\u202f]+"), " ")
        if (text.isBlank() || text.any { it == '+' || it == '-' || it == '−' || it == '–' || it == '—' }) return null
        // Currency labels may surround one expression; they never permit skipping other prose.
        text = text.replaceFirst(Regex("^(?:₹|rs\\.?|inr)\\s*"), "")
        text = text.replaceFirst(Regex("^$rupee\\s+"), "")
        if (text.isBlank()) return null
        val split = Regex("^(.+?)\\s+$rupee(?:\\s+(?:and|और))?\\s+(.+?)\\s+$paise$").matchEntire(text)
        val amount = if (split != null) {
            val rupees = integer(split.groupValues[1]) ?: return null
            val remainder = integer(split.groupValues[2]) ?: return null
            if (remainder !in 0..99) return null
            BigDecimal(rupees).add(BigDecimal(remainder).movePointLeft(2))
        } else if (Regex("^.+\\s+$paise$").matches(text)) {
            val units = integer(text.replace(Regex("\\s+$paise$"), "")) ?: return null
            if (units !in 0..99) return null
            BigDecimal(units).movePointLeft(2)
        } else {
            text = text.replace(Regex("\\s*$rupee$"), "").trim()
            number(text) ?: return null
        }
        if (amount <= BigDecimal.ZERO || amount > maximum || amount.scale() > 2) return null
        return amount.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
    }

    private fun number(text: String): BigDecimal? {
        if (text.matches(Regex("[0-9.,]+"))) return numeric(text)
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        val points = tokens.indices.filter { tokens[it] in setOf("point", "decimal", "दशमलव", "पॉइंट") }
        if (points.isEmpty()) return integer(text)?.let { BigDecimal(it) }
        if (points.size != 1 || points[0] == 0 || points[0] == tokens.lastIndex) return null
        val point = points[0]
        val whole = integer(tokens.take(point).joinToString(" ")) ?: return null
        val fractionTokens = tokens.drop(point + 1)
        val hindi = tokens.any { token -> token.any { it in '\u0900'..'\u097f' } }
        val units = if (hindi) hindiUnits else englishUnits
        val digits = fractionTokens.map { word ->
            word.toIntOrNull()?.takeIf { word.length == 1 } ?: units[word]?.takeIf { it in 0..9 }
        }
        val fraction = if (digits.all { it != null } && digits.size in 1..2) {
            digits.joinToString("")
        } else {
            small(fractionTokens, hindi)?.takeIf { it in 10..99 }?.toString() ?: return null
        }
        return BigDecimal("$whole.$fraction")
    }
    private fun numeric(text: String): BigDecimal? {
        val components = text.split('.')
        if (components.size > 2 || (components.size == 2 && !components[1].matches(Regex("[0-9]{1,2}")))) return null
        val whole = components[0]
        if (whole.isEmpty() || !whole.matches(Regex("[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+|[1-9][0-9]?(?:,[0-9]{2})*,[0-9]{3}"))) return null
        val digits = whole.replace(",", "")
        if (digits.length > 6) return null
        return try { BigDecimal(digits + if (components.size == 2) ".${components[1]}" else "") } catch (_: NumberFormatException) { null }
    }
    private fun integer(text: String): Int? {
        if (text.matches(Regex("[0-9,]+"))) return numeric(text)?.let { try { it.intValueExact() } catch (_: ArithmeticException) { null } }
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val hindi = tokens.any { token -> token.any { it in '\u0900'..'\u097f' } }
        val scales = if (hindi) hindiScales else englishScales
        var total = 0L
        var previousScale = Int.MAX_VALUE
        var groupStart = 0
        for ((position, word) in tokens.withIndex()) {
            val scale = scales[word] ?: continue
            if (scale >= previousScale || position == groupStart) return null
            val group = group(tokens.subList(groupStart, position), hindi) ?: return null
            if (group <= 0) return null
            total += group.toLong() * scale
            if (total > 100000) return null
            previousScale = scale; groupStart = position + 1
        }
        if (groupStart < tokens.size) {
            var tail = tokens.drop(groupStart)
            if (groupStart > 0 && tail.first() == if (hindi) "और" else "and") tail = tail.drop(1)
            if (tail.isEmpty()) return null
            val remainder = group(tail, hindi) ?: return null
            if (groupStart > 0 && remainder >= previousScale) return null
            total += remainder
        }
        return total.takeIf { it in 0..100000 }?.toInt()
    }
    private fun group(tokens: List<String>, hindi: Boolean): Int? {
        if (tokens.isEmpty()) return null
        val hundred = if (hindi) "सौ" else "hundred"
        val count = tokens.count { it == hundred }
        if (count == 0) return small(tokens, hindi)
        if (count != 1) return null
        val position = tokens.indexOf(hundred)
        val multiplier = if (position == 0) 1 else small(tokens.take(position), hindi) ?: return null
        if (multiplier !in 1..(if (hindi) 99 else 9)) return null
        var tail = tokens.drop(position + 1)
        if (tail.firstOrNull() == if (hindi) "और" else "and") {
            tail = tail.drop(1)
            if (tail.isEmpty()) return null
        }
        val remainder = if (tail.isEmpty()) 0 else small(tail, hindi) ?: return null
        return multiplier * 100 + remainder
    }
    private fun small(tokens: List<String>, hindi: Boolean): Int? {
        if (tokens.size == 1) {
            val word = tokens[0]
            if (word.matches(Regex("[0-9]{1,2}"))) return word.toInt()
            return if (hindi) hindiUnits[word] else englishUnits[word] ?: englishTens[word]
        }
        if (!hindi && tokens.size == 2) {
            val tens = englishTens[tokens[0]] ?: return null
            val ones = englishUnits[tokens[1]]?.takeIf { it in 1..9 } ?: return null
            return tens + ones
        }
        return null
    }
}
