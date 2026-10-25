package app.platform

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Small per-device preferences (not part of the backup). */
class AppSettings(private val settings: Settings) {
    private val _theme = MutableStateFlow(
        settings.getStringOrNull(KEY_THEME)?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        settings.putString(KEY_THEME, mode.name)
        _theme.value = mode
    }

    private companion object {
        const val KEY_THEME = "pref.theme"
    }
}

/** Saving / opening backup files through the system's own pickers (SAF on Android, Files on iOS). */
interface BackupFiles {
    /** Lets the user choose where to save. Returns false if they cancelled. */
    suspend fun save(suggestedName: String, content: String): Boolean
    /** Lets the user pick a file. Returns its text, or null if they cancelled. */
    suspend fun open(): String?
}
