package app.lirnyk.core.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.os.Build
import android.window.OnBackInvokedDispatcher
import app.lirnyk.core.media.KeyLog
import app.lirnyk.core.media.Lirnyk
import app.lirnyk.core.media.ThemeMode
import app.lirnyk.core.model.SteeringKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Base for every Lirnyk screen: plain platform Activity (no AppCompat: smaller and faster on head units), forced day or
 * night theme, and the concept's "back to the player after 10 s without touches" rule for secondary screens.
 */
abstract class BaseActivity : Activity() {
    /** Seconds without a touch before a secondary screen closes back to the player; null = never. */
    protected open val idleReturnSeconds: Int? = 10

    private val handler = Handler(Looper.getMainLooper())
    private val idleReturn = Runnable { finish() }
    private var appliedTheme: ThemeMode? = null

    /** Scope bound to the visible screen (onStart … onStop). */
    protected var uiScope: CoroutineScope = newScope()
        private set

    override fun attachBaseContext(newBase: Context) {
        val mode = Lirnyk.prefs.theme.value
        appliedTheme = mode
        val night = when (mode) {
            ThemeMode.AUTO -> null
            ThemeMode.NIGHT -> Configuration.UI_MODE_NIGHT_YES
            ThemeMode.DAY -> Configuration.UI_MODE_NIGHT_NO
        }
        if (night == null) { super.attachBaseContext(newBase); return }
        val cfg = Configuration(newBase.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Lirnyk)
        super.onCreate(savedInstanceState)
        // Android 13+: back (button or gesture) arrives here; older versions use the BACK key below.
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { onBack() }
        }
    }

    /** Back: closes the screen; screens with inner levels go up one level first. */
    protected open fun onBack() = finish()

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && Build.VERSION.SDK_INT < 33 && event.isTracking && !event.isCanceled) { onBack(); return true }
        return super.onKeyUp(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        if (appliedTheme != Lirnyk.prefs.theme.value) { recreate(); return }
        uiScope = newScope()
    }

    override fun onResume() {
        super.onResume()
        scheduleIdleReturn()
    }

    override fun onPause() {
        handler.removeCallbacks(idleReturn)
        super.onPause()
    }

    override fun onStop() {
        uiScope.cancel()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        scheduleIdleReturn()
    }

    /** Set while the user is typing or editing: the screen is never closed under their hands. */
    protected var idleReturnSuspended = false
        set(v) { field = v; scheduleIdleReturn() }

    private fun scheduleIdleReturn() {
        handler.removeCallbacks(idleReturn)
        if (idleReturnSuspended) return
        idleReturnSeconds?.let { handler.postDelayed(idleReturn, it * 1000L) }
    }

    /** Steering-wheel keys are only noted for the head-unit check; the system still routes them to the playing session. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && Build.VERSION.SDK_INT < 33) { event.startTracking(); return true }
        if (event.repeatCount == 0 && SteeringKeys.actionFor(keyCode) != null) KeyLog.record(keyCode, "screen")
        return super.onKeyDown(keyCode, event)
    }

    protected fun open(route: String) {
        startActivity(Intent().setClassName(this, route))
    }

    protected fun <T> Flow<T>.collectWhileVisible(block: (T) -> Unit) {
        uiScope.launch { collect { block(it) } }
    }

    /** Every secondary screen has its back button in the same place (top left). */
    protected fun bindBack(view: View = findViewById(R.id.lk_back)) {
        view.setOnClickListener { onBack() }
    }

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
