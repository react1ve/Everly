package app.platform

import app.ui.text.Lang
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** null code = follow the phone. */
enum class LanguagePref(val code: String?) { SYSTEM(null), EN("en"), RU("ru"), UZ("uz") }

/** The phone's language, e.g. "ru". Platform-specific. */
expect fun systemLanguageCode(): String

/** Small per-device preferences (not part of the backup). */
class AppSettings(private val settings: Settings) {
    private val _theme = MutableStateFlow(enumOr(KEY_THEME, ThemeMode.SYSTEM))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _language = MutableStateFlow(enumOr(KEY_LANG, LanguagePref.SYSTEM))
    val language: StateFlow<LanguagePref> = _language.asStateFlow()

    private val _analytics = MutableStateFlow(settings.getBoolean(KEY_ANALYTICS, false))
    /** Opt-in only; off by default. */
    val analyticsEnabled: StateFlow<Boolean> = _analytics.asStateFlow()

    private val _onboarded = MutableStateFlow(settings.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    init {
        applyLanguage()
    }

    fun setTheme(mode: ThemeMode) {
        settings.putString(KEY_THEME, mode.name)
        _theme.value = mode
    }

    fun setLanguage(pref: LanguagePref) {
        settings.putString(KEY_LANG, pref.name)
        _language.value = pref
        applyLanguage()
    }

    fun setAnalytics(on: Boolean) {
        settings.putBoolean(KEY_ANALYTICS, on)
        _analytics.value = on
    }

    fun setOnboarded() {
        settings.putBoolean(KEY_ONBOARDED, true)
        _onboarded.value = true
    }

    /** Re-reads the phone language (call on app resume when following the phone). */
    fun applyLanguage() {
        Lang.select(_language.value.code ?: systemLanguageCode())
    }

    private inline fun <reified T : Enum<T>> enumOr(key: String, default: T): T =
        settings.getStringOrNull(key)?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    private companion object {
        const val KEY_THEME = "pref.theme"
        const val KEY_LANG = "pref.language"
        const val KEY_ANALYTICS = "pref.analytics"
        const val KEY_ONBOARDED = "pref.onboarded"
    }
}

/** Saving / opening backup files through the system's own pickers (SAF on Android, Files on iOS). */
interface BackupFiles {
    /** Lets the user choose where to save. Returns false if they cancelled. */
    suspend fun save(suggestedName: String, content: String): Boolean
    /** Lets the user pick a file. Returns its text, or null if they cancelled. */
    suspend fun open(): String?
}
