package `in`.family.maa

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** App-only language preference; does not change the phone's system language. */
object AppLanguage {
    const val KEY = "language"
    fun code(context: Context): String =
        if(context.getSharedPreferences("family",Context.MODE_PRIVATE).getString(KEY,"en")=="hi") "hi" else "en"
    fun isHindi(context: Context): Boolean = code(context) == "hi"
    fun locale(context: Context): Locale = Locale.forLanguageTag("${code(context)}-IN")
    fun localizedContext(context: Context): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(locale(context)) }
    )
    fun text(context: Context, id: Int): String = localizedContext(context).getString(id)
}
