package `in`.family.maa

import android.content.Context
import java.text.Normalizer

/** Assistant identity is independent of the Maa application/package name. */
object AssistantIdentity {
    const val KEY_NAME = "assistant_name"
    const val DEFAULT_NAME = "Asha"
    const val MAX_CODE_POINTS = 24

    fun normalizeName(raw: String): String {
        val source = Normalizer.normalize(raw.take(512), Normalizer.Form.NFKC)
        val output = StringBuilder()
        var count = 0
        var gap = false
        for (codePoint in source.codePoints().toArray()) {
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || Character.isISOControl(codePoint)) {
                gap = output.isNotEmpty()
                continue
            }
            if (Character.getType(codePoint) == Character.FORMAT.toInt() || Character.getType(codePoint) == Character.SURROGATE.toInt()) continue
            if (gap && count < MAX_CODE_POINTS) { output.append(' '); count++ }
            gap = false
            if (count >= MAX_CODE_POINTS) break
            output.appendCodePoint(codePoint); count++
        }
        return output.toString().trim().ifBlank { DEFAULT_NAME }
    }

    fun displayName(context: Context): String = normalizeName(
        context.getSharedPreferences("family", Context.MODE_PRIVATE).all[KEY_NAME] as? String ?: DEFAULT_NAME
    )

    fun askLabel(context: Context): String = AppLanguage.localizedContext(context)
        .getString(R.string.assistant_ask_named, displayName(context))
}
