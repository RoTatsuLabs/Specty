package com.rotatsulabs.specty.ui

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rotatsulabs.specty.R
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The app's own language. From Android 13 the system keeps it (the same setting as in system Settings, listed by
 * `locales_config.xml`). Before that the choice is kept in preferences and applied by wrapping each context.
 */
object AppLanguage {
    /** [tag] is null for "follow the system". Tags match `res/xml/locales_config.xml`. */
    enum class Choice(val tag: String?, @StringRes val labelRes: Int) {
        System(null, R.string.language_system),
        English("en", R.string.language_en),
        Indonesian("id", R.string.language_id),
        Chinese("zh-CN", R.string.language_zh),
    }

    private const val PREFS = "specty"
    private const val KEY = "language"

    /** True while the screen is faded out to hide the switch; the activity lifts it once the new language is in. */
    var veil by mutableStateOf(false)
        private set

    /** Bumped when Android 12 and older switch language without recreating the activity. */
    var revision by mutableIntStateOf(0)
        private set

    fun lift() { veil = false }

    /** Fades the screen out, switches to [choice], and leaves the lift to the activity. */
    suspend fun change(context: Context, choice: Choice) {
        if (choice == current(context)) return
        veil = true
        delay(220)
        set(context, choice)
        delay(1500)
        veil = false
    }

    /** For Android 12 and older: [base] with resources in the saved language, still reaching the activity. */
    fun localized(base: Context): Context {
        val resources = wrap(base).resources
        return object : ContextWrapper(base) {
            override fun getResources(): Resources = resources
        }
    }

    fun current(context: Context): Choice {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.let { if (it.isEmpty) null else it[0].toLanguageTag() }
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        } ?: return Choice.System
        val language = Locale.forLanguageTag(tag).language
        return Choice.entries.firstOrNull { it.tag != null && Locale.forLanguageTag(it.tag).language == language } ?: Choice.System
    }

    /** Applies [choice]. The activity handles the locale change itself, so nothing is rebuilt. */
    fun set(context: Context, choice: Choice) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (choice.tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(choice.tag)
            return
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (choice.tag == null) remove(KEY) else putString(KEY, choice.tag)
        }.apply()
        val locale = localeOf(choice)
        Locale.setDefault(locale)
        // Strings read through the application context (toasts, notifications) follow at once.
        updateResources(context.applicationContext.resources, locale)
        revision++
    }

    /** For Android 12 and older: a context whose resources use the saved language. Other versions are left alone. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private fun localeOf(choice: Choice): Locale =
        choice.tag?.let { Locale.forLanguageTag(it) } ?: Resources.getSystem().configuration.locales[0]

    @Suppress("DEPRECATION")
    private fun updateResources(resources: Resources, locale: Locale) {
        val config = Configuration(resources.configuration).apply { setLocale(locale) }
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
