package app.lirnyk.core.media

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { AUTO, NIGHT, DAY }

/** Small settings and state. SharedPreferences: available from the first millisecond of a cold start, no extra library. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("lirnyk", Context.MODE_PRIVATE)

    private val _autoResume = MutableStateFlow(sp.getBoolean(K_AUTO_RESUME, true))
    val autoResume: StateFlow<Boolean> = _autoResume.asStateFlow()
    fun setAutoResume(v: Boolean) { sp.edit().putBoolean(K_AUTO_RESUME, v).apply(); _autoResume.value = v }

    private val _theme = MutableStateFlow(runCatching { ThemeMode.valueOf(sp.getString(K_THEME, null) ?: "") }.getOrDefault(ThemeMode.AUTO))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    fun setTheme(v: ThemeMode) { sp.edit().putString(K_THEME, v.name).apply(); _theme.value = v }

    private val _keepScreenOn = MutableStateFlow(sp.getBoolean(K_KEEP_SCREEN, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()
    fun setKeepScreenOn(v: Boolean) { sp.edit().putBoolean(K_KEEP_SCREEN, v).apply(); _keepScreenOn.value = v }

    private val _pickedFolders = MutableStateFlow(sp.getStringSet(K_FOLDERS, emptySet())!!.toSet())
    /** Tree URIs the user picked with the system folder picker (persisted read permission). */
    val pickedFolders: StateFlow<Set<String>> = _pickedFolders.asStateFlow()
    fun setPickedFolders(v: Set<String>) { sp.edit().putStringSet(K_FOLDERS, v).apply(); _pickedFolders.value = v }

    private val _favorites = MutableStateFlow(sp.getStringSet(K_FAVORITES, emptySet())!!.toSet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
    fun toggleFavorite(id: String) {
        val next = _favorites.value.let { if (id in it) it - id else it + id }
        sp.edit().putStringSet(K_FAVORITES, next).apply()
        _favorites.value = next
    }

    var bootLog: String
        get() = sp.getString(K_BOOT_LOG, "") ?: ""
        set(v) { sp.edit().putString(K_BOOT_LOG, v).apply() }

    var resumeRecord: String?
        get() = sp.getString(K_RESUME, null)
        set(v) { sp.edit().putString(K_RESUME, v).apply() }

    private companion object {
        const val K_AUTO_RESUME = "auto_resume"
        const val K_THEME = "theme"
        const val K_KEEP_SCREEN = "keep_screen_on"
        const val K_FOLDERS = "picked_folders"
        const val K_FAVORITES = "favorites"
        const val K_RESUME = "resume"
        const val K_BOOT_LOG = "boot_log"
    }
}
