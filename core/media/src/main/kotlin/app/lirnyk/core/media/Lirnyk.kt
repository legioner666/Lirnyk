package app.lirnyk.core.media

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import app.lirnyk.core.model.HeadUnitReport
import app.lirnyk.core.model.SteeringKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide objects, created once in Application.onCreate. No DI framework: fewer classes to load on a cold start. */
@android.annotation.SuppressLint("StaticFieldLeak")   // holds the application context only
object Lirnyk {
    lateinit var prefs: Prefs
        private set
    lateinit var library: LibraryRepository
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var versionLabel: String = "?"
        private set

    @Volatile private var started = false

    fun init(context: Context, versionLabel: String) {
        if (started) return
        started = true
        val app = context.applicationContext
        this.versionLabel = versionLabel
        prefs = Prefs(app)
        BootLog.prefsRef = prefs
        library = LibraryRepository(app, prefs, scope)
        library.start()
    }
}

/** Activity class names, so feature modules can open each other without depending on each other. */
object Routes {
    const val PLAYER = "app.lirnyk.feature.player.PlayerActivity"
    const val LIBRARY = "app.lirnyk.feature.library.LibraryActivity"
    const val SOURCES = "app.lirnyk.feature.settings.SourcesActivity"
    const val SETTINGS = "app.lirnyk.feature.settings.SettingsActivity"
    const val CHECK = "app.lirnyk.feature.diagnostics.HeadUnitCheckActivity"
}

/** Steering-wheel and media keys seen in this process (head-unit check). In memory only, bounded. */
object KeyLog {
    private const val MAX = 40
    private val _keys = MutableStateFlow<List<HeadUnitReport.KeySeen>>(emptyList())
    val keys: StateFlow<List<HeadUnitReport.KeySeen>> = _keys.asStateFlow()

    fun record(keyCode: Int, via: String) {
        val name = android.view.KeyEvent.keyCodeToString(keyCode)
        _keys.value = (_keys.value + HeadUnitReport.KeySeen(keyCode, name, via, SteeringKeys.actionFor(keyCode))).takeLast(MAX)
    }

    fun clear() { _keys.value = emptyList() }
}

/** Startup timing for the head-unit check (concept target: first frame ≤ 1.5 s, music ≤ 3 s after the unit starts). */
object StartupMetrics {
    @Volatile var firstFrameMs: Long? = null
        private set
    @Volatile var serviceAfterBootMs: Long? = null
        private set

    fun onFirstFrame() {
        if (firstFrameMs != null || Build.VERSION.SDK_INT < 24) return
        firstFrameMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
    }

    /** Elapsed time since boot when the playback service started, recorded only for starts within 5 minutes of boot. */
    fun onServiceCreated() {
        val sinceBoot = SystemClock.elapsedRealtime()
        if (sinceBoot < 5 * 60_000L) serviceAfterBootMs = sinceBoot
    }
}

/**
 * What happened at the last start after boot (GPT audit P1: boot start must not fail silently). A few lines, newest last,
 * kept in preferences so the head-unit check can show them after the fact. No track names.
 */
object BootLog {
    private const val MAX_LINES = 8

    fun note(event: String) {
        if (!::prefsRef.isInitialized) return
        val line = "${java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())} $event"
        prefsRef.bootLog = (prefsRef.bootLog.lines().filter { it.isNotBlank() } + line).takeLast(MAX_LINES).joinToString("\n")
    }

    val text: String get() = if (::prefsRef.isInitialized) prefsRef.bootLog else ""

    internal lateinit var prefsRef: Prefs
}
