package app.lirnyk.feature.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaCodecList
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import app.lirnyk.core.media.ExternalPlayersProbe
import app.lirnyk.core.media.KeyLog
import app.lirnyk.core.media.Lirnyk
import app.lirnyk.core.media.StartupMetrics
import app.lirnyk.core.media.Volumes
import app.lirnyk.core.model.AudioFormats
import app.lirnyk.core.model.HeadUnitReport
import app.lirnyk.core.ui.BaseActivity
import app.lirnyk.core.ui.Form
import java.util.Locale
import app.lirnyk.core.ui.R as UiR

/**
 * Stage 0 "Перевірка магнітоли": what this head unit really offers (keys, drives, codecs, other players' sessions).
 * The owner sends the report by hand; nothing leaves the device otherwise (Lirnyk has no internet permission).
 */
class HeadUnitCheckActivity : BaseActivity() {
    override val idleReturnSeconds: Int? = null   // pressing steering-wheel buttons is not a touch

    private lateinit var form: Form
    private var report: HeadUnitReport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(UiR.layout.lk_form_screen)
        findViewById<TextView>(UiR.id.lk_header_title).setText(UiR.string.chk_title)
        bindBack()
        form = Form(this, findViewById<LinearLayout>(UiR.id.lk_form))
    }

    override fun onStart() {
        super.onStart()
        KeyLog.keys.collectWhileVisible { render() }
        Lirnyk.library.library.collectWhileVisible { render() }
    }

    /** Every key is noted here, not only media keys: the point is to learn what this wheel sends. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0 && keyCode != KeyEvent.KEYCODE_BACK) KeyLog.record(keyCode, "check screen")
        return super.onKeyDown(keyCode, event)
    }

    private fun render() {
        val r = collect().also { report = it }
        form.clear()
        form.body(getString(UiR.string.chk_intro))
        form.section(getString(UiR.string.chk_keys))
        if (r.keys.isEmpty()) form.body(getString(UiR.string.chk_no_keys))
        r.keys.takeLast(12).forEach { form.body("${it.name} (${it.keyCode}) · ${it.via}${it.action?.let { a -> " → $a" } ?: ""}", secondary = false) }

        form.button(getString(UiR.string.chk_share)) { share(r) }
        form.button(getString(UiR.string.chk_copy)) { copy(r) }

        form.section(getString(UiR.string.chk_players))
        form.body(getString(UiR.string.chk_players_body))
        if (r.externalPlayers.isEmpty()) form.body(getString(UiR.string.chk_players_none), secondary = false)
        r.externalPlayers.forEach { form.body(it, secondary = false) }

        form.section(getString(UiR.string.chk_title))
        form.body(r.toText())
    }

    private fun collect(): HeadUnitReport {
        val am = getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(dm)
        val cfg = resources.configuration
        val night = if (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) "yes" else "no"
        val library = Lirnyk.library.library.value
        val notif = when {
            Build.VERSION.SDK_INT < 33 -> "not needed"
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> "granted"
            else -> "not granted"
        }
        return HeadUnitReport(
            appVersion = Lirnyk.versionLabel,
            manufacturer = Build.MANUFACTURER, model = Build.MODEL, androidRelease = Build.VERSION.RELEASE, sdk = Build.VERSION.SDK_INT,
            totalRamMb = mem.totalMem / (1024 * 1024), lowRamDevice = am.isLowRamDevice,
            screen = HeadUnitReport.Screen(dm.widthPixels, dm.heightPixels, dm.densityDpi, cfg.smallestScreenWidthDp, "$night (theme ${Lirnyk.prefs.theme.value.name.lowercase(Locale.ROOT)})"),
            volumes = runCatching { Volumes.list(this) }.getOrDefault(emptyList()).map {
                HeadUnitReport.Volume(it.kind, if (it.mounted) "mounted" else "not mounted", it.removable, it.primary)
            },
            decoders = decoders,
            library = HeadUnitReport.LibraryCounts(library.tracks.size, library.availableTracks.size, library.folders().size, Lirnyk.prefs.pickedFolders.value.size),
            keys = KeyLog.keys.value,
            externalPlayers = externalPlayers,
            notificationPermission = notif,
            audioReadPermission = Lirnyk.library.hasAudioPermission(),
            bootReceiverAllowed = Build.VERSION.SDK_INT < 35 && Lirnyk.prefs.autoResume.value,
            startup = HeadUnitReport.Startup(StartupMetrics.firstFrameMs, StartupMetrics.serviceAfterBootMs),
        )
    }

    private val externalPlayers: List<String> by lazy { ExternalPlayersProbe.list(this) }

    /** Queried once per screen: MediaCodecList is slow on head-unit processors. */
    private val decoders: Map<String, Boolean> by lazy {
        val types = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }.flatMap { it.supportedTypes.map { t -> t.lowercase(Locale.ROOT) } }.toSet()
        }.getOrDefault(emptySet())
        AudioFormats.decoderMimes.mapValues { (_, mime) -> mime in types }
    }

    private fun share(r: HeadUnitReport) {
        val text = r.toText() + "\n\n" + r.toJson()
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Lirnyk head unit check").putExtra(Intent.EXTRA_TEXT, text)
        try { startActivity(Intent.createChooser(send, getString(UiR.string.chk_share))) } catch (_: ActivityNotFoundException) { copy(r) }
    }

    private fun copy(r: HeadUnitReport) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Lirnyk", r.toText() + "\n\n" + r.toJson()))
        Toast.makeText(this, UiR.string.chk_copied, Toast.LENGTH_SHORT).show()
    }
}
