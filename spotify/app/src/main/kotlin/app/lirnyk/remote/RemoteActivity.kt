package app.lirnyk.remote

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import app.lirnyk.spotify.model.Fingerprint
import app.lirnyk.spotify.model.RemoteController
import app.lirnyk.spotify.model.RemoteProblem
import app.lirnyk.spotify.model.RemoteSnapshot
import app.lirnyk.spotify.model.RemoteStatus
import app.lirnyk.spotify.model.Repeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Lirnyk Remote — TASK 003 prototype screen: Spotify's now playing (cover, title, artist, progress) and its controls,
 * plus the account actions (connect / disconnect / delete data / revoke) and the app identity for the Spotify dashboard.
 * Connected only while visible, as the App Remote documentation recommends (connect in onStart, disconnect in onStop).
 */
class RemoteActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var visible: CoroutineScope? = null
    private val covers = LruCache<String, Bitmap>(4)
    private lateinit var port: SpotifyPort
    private lateinit var controller: RemoteController

    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var cover: ImageView
    private lateinit var progress: SeekBar
    private lateinit var elapsed: TextView
    private lateinit var duration: TextView
    private lateinit var play: ImageButton
    private lateinit var shuffle: ImageButton
    private lateinit var repeat: ImageButton
    private lateinit var connect: TextView
    private var dragging = false
    private var shownImage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.remote)
        port = SpotifyPort(applicationContext) { System.currentTimeMillis() }
        controller = RemoteController(port, PrefsAccountStore(applicationContext, covers), scope) { System.currentTimeMillis() }

        status = findViewById(R.id.status); title = findViewById(R.id.title); artist = findViewById(R.id.artist)
        cover = findViewById(R.id.cover); progress = findViewById(R.id.progress); elapsed = findViewById(R.id.elapsed)
        duration = findViewById(R.id.duration); play = findViewById(R.id.play); shuffle = findViewById(R.id.shuffle)
        repeat = findViewById(R.id.repeat); connect = findViewById(R.id.connect)

        play.setOnClickListener { controller.togglePlay() }
        findViewById<View>(R.id.next).setOnClickListener { controller.next() }
        findViewById<View>(R.id.prev).setOnClickListener { controller.previous() }
        shuffle.setOnClickListener { controller.toggleShuffle() }
        repeat.setOnClickListener { controller.cycleRepeat() }
        connect.setOnClickListener { controller.connectInteractive() }
        findViewById<View>(R.id.disconnect).setOnClickListener { controller.disconnect() }
        findViewById<View>(R.id.forget).setOnClickListener {
            controller.forgetAccount()
            shownImage = null
            cover.setImageResource(R.drawable.ic_lk_note)
            Toast.makeText(this, R.string.remote_forget_done, Toast.LENGTH_LONG).show()
        }
        findViewById<View>(R.id.revoke).setOnClickListener { openUri(ACCOUNT_APPS_URL) }
        findViewById<View>(R.id.open_spotify).setOnClickListener {
            packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)?.let { startActivity(it) } ?: openUri("market://details?id=$SPOTIFY_PACKAGE")
        }
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                val d = controller.snapshot.value?.durationMs ?: 0
                if (fromUser && d > 0) elapsed.text = time(d * value / 1000)
            }
            override fun onStartTrackingTouch(sb: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                dragging = false
                controller.snapshot.value?.let { controller.seekTo(it.durationMs * sb.progress / 1000) }
            }
        })
        renderIdentity()
    }

    override fun onStart() {
        super.onStart()
        val v = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { visible = it }
        v.launch { combine(controller.status, controller.snapshot) { s, p -> s to p }.collect { (s, p) -> render(s, p) } }
        // Progress moves locally between Spotify's updates (no polling of Spotify).
        v.launch { while (isActive) { delay(500); renderProgress(controller.snapshot.value) } }
        controller.connectIfAuthorized()
    }

    override fun onStop() {
        controller.disconnect()
        visible?.cancel(); visible = null
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(s: RemoteStatus, p: RemoteSnapshot?) {
        status.text = statusText(s)
        val connected = s == RemoteStatus.Connected
        connect.visibility = if (connected) View.GONE else View.VISIBLE
        connect.setText(when {
            s is RemoteStatus.Problem && s.problem == RemoteProblem.NOT_AUTHORIZED -> R.string.remote_authorize
            s == RemoteStatus.Disconnected || s is RemoteStatus.Problem -> R.string.remote_reconnect
            else -> R.string.remote_connect
        })
        connect.isEnabled = s != RemoteStatus.Connecting

        if (p == null || !connected) {
            title.text = if (connected) getString(R.string.remote_nothing) else ""
            artist.text = ""
        } else {
            title.text = p.title ?: getString(R.string.remote_nothing)
            artist.text = listOfNotNull(p.artist, p.album).joinToString(" · ")
        }
        val playing = connected && p != null && !p.paused
        play.setImageResource(if (playing) R.drawable.ic_lk_pause_accent else R.drawable.ic_lk_play_accent)
        play.contentDescription = getString(if (playing) R.string.remote_pause else R.string.remote_play)
        play.isEnabled = connected
        enable(findViewById(R.id.next), connected && p?.canSkipNext == true)
        enable(findViewById(R.id.prev), connected && p?.canSkipPrev == true)
        enable(shuffle, connected && p?.canToggleShuffle == true, highlighted = p?.shuffle == true)
        enable(repeat, connected && p?.nextRepeat() != null, highlighted = p?.repeat != Repeat.OFF)
        repeat.setImageResource(if (p?.repeat == Repeat.TRACK) R.drawable.ic_lk_repeat_one else R.drawable.ic_lk_repeat)
        progress.isEnabled = connected && p?.canSeek == true
        renderCover(if (connected) p?.imageUri else null)
        renderProgress(if (connected) p else null)
    }

    private fun enable(v: View, on: Boolean, highlighted: Boolean = true) {
        v.isEnabled = on
        v.alpha = when { !on -> 0.25f; highlighted -> 1f; else -> 0.5f }
    }

    private fun renderProgress(p: RemoteSnapshot?) {
        if (dragging) return
        if (p == null || p.durationMs <= 0) { progress.progress = 0; elapsed.text = ""; duration.text = ""; return }
        val pos = p.positionAt(System.currentTimeMillis())
        progress.progress = (pos * 1000 / p.durationMs).toInt()
        elapsed.text = time(pos)
        duration.text = time(p.durationMs)
    }

    private fun renderCover(imageUri: String?) {
        if (imageUri == shownImage) return
        shownImage = imageUri
        if (imageUri == null) { cover.setImageResource(R.drawable.ic_lk_note); return }
        covers.get(imageUri)?.let { cover.setImageBitmap(it); return }
        port.loadImage(imageUri) { bmp ->
            runOnUiThread {
                if (bmp != null) covers.put(imageUri, bmp)
                if (shownImage == imageUri) { if (bmp != null) cover.setImageBitmap(bmp) else cover.setImageResource(R.drawable.ic_lk_note) }
            }
        }
    }

    private fun statusText(s: RemoteStatus): String = when (s) {
        RemoteStatus.NotSetUp -> getString(R.string.remote_status_not_set_up)
        RemoteStatus.Connecting -> getString(R.string.remote_status_connecting)
        RemoteStatus.Connected -> getString(R.string.remote_status_connected)
        RemoteStatus.Disconnected -> getString(R.string.remote_status_disconnected)
        is RemoteStatus.Problem -> when (s.problem) {
            RemoteProblem.SPOTIFY_NOT_INSTALLED -> getString(R.string.remote_p_not_installed)
            RemoteProblem.NOT_AUTHORIZED -> getString(R.string.remote_p_not_authorized)
            RemoteProblem.AUTHENTICATION_FAILED -> getString(R.string.remote_p_auth_failed)
            RemoteProblem.NOT_LOGGED_IN -> getString(R.string.remote_p_not_logged_in)
            RemoteProblem.OFFLINE -> getString(R.string.remote_p_offline)
            RemoteProblem.SPOTIFY_TOO_OLD -> getString(R.string.remote_p_too_old)
            RemoteProblem.CONNECTION_LOST -> getString(R.string.remote_p_lost)
            RemoteProblem.SERVICE_START_REFUSED -> getString(R.string.remote_p_service)
            RemoteProblem.UNKNOWN -> getString(R.string.remote_p_unknown, s.detail ?: "?")
        }
    }

    /** Package, signing SHA-1 and redirect URI exactly as Spotify must have them, with the check against the expected SHA-1. */
    private fun renderIdentity() {
        val actual = SigningInfo.sha1(this)
        val expected = BuildConfig.EXPECTED_CERT_SHA1.takeIf { it.isNotBlank() }
        val check = when (Fingerprint.matches(actual, expected)) {
            true -> getString(R.string.remote_check_match)
            false -> getString(R.string.remote_check_mismatch, Fingerprint.pretty(expected))
            null -> getString(R.string.remote_check_unknown)
        }
        findViewById<TextView>(R.id.identity).text = listOf(
            getString(R.string.remote_check_package, packageName),
            getString(R.string.remote_check_sha1, actual ?: "?"),
            check,
            getString(R.string.remote_check_redirect, BuildConfig.SPOTIFY_REDIRECT_URI),
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.GIT_COMMIT})",
        ).joinToString("\n")
    }

    private fun openUri(uri: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) } catch (_: ActivityNotFoundException) { }
    }

    private fun time(ms: Long): String {
        val t = ms.coerceAtLeast(0) / 1000
        val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    private companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        /** Spotify account page where the user revokes third-party app access. */
        const val ACCOUNT_APPS_URL = "https://www.spotify.com/account/apps/"
    }
}
