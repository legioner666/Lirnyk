package app.lirnyk.feature.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import app.lirnyk.core.media.LibraryRepository
import app.lirnyk.core.media.Lirnyk
import app.lirnyk.core.media.NowPlaying
import app.lirnyk.core.media.PlayerConnection
import app.lirnyk.core.media.Routes
import app.lirnyk.core.media.StartupMetrics
import app.lirnyk.core.model.RepeatMode
import app.lirnyk.core.ui.BaseActivity
import app.lirnyk.core.ui.Labels
import kotlinx.coroutines.flow.combine
import kotlin.math.abs
import app.lirnyk.core.ui.R as UiR

/** Main screen = the player (concept). Swipe left/right anywhere changes the track; the steering wheel does the rest. */
class PlayerActivity : BaseActivity() {
    override val idleReturnSeconds: Int? = null

    private lateinit var connection: PlayerConnection
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var note: TextView
    private lateinit var status: TextView
    private lateinit var elapsed: TextView
    private lateinit var duration: TextView
    private lateinit var progress: SeekBar
    private lateinit var play: ImageButton
    private lateinit var shuffle: ImageButton
    private lateinit var repeat: ImageButton
    private lateinit var favorite: ImageButton
    private var dragging = false
    private var last = NowPlaying()
    private lateinit var gestures: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player)
        title = findViewById(R.id.title); artist = findViewById(R.id.artist); note = findViewById(R.id.note)
        status = findViewById(R.id.status); elapsed = findViewById(R.id.elapsed); duration = findViewById(R.id.duration)
        progress = findViewById(R.id.progress); play = findViewById(R.id.play)
        shuffle = findViewById(R.id.shuffle); repeat = findViewById(R.id.repeat); favorite = findViewById(R.id.favorite)

        play.setOnClickListener { if (last.count == 0) open(Routes.LIBRARY) else connection.togglePlay() }
        findViewById<View>(R.id.next).setOnClickListener { connection.next() }
        findViewById<View>(R.id.prev).setOnClickListener { connection.previous() }
        shuffle.setOnClickListener { connection.toggleShuffle() }
        repeat.setOnClickListener { connection.cycleRepeat() }
        favorite.setOnClickListener { last.mediaId?.let(Lirnyk.prefs::toggleFavorite) }
        findViewById<View>(R.id.tile_library).setOnClickListener { open(Routes.LIBRARY) }
        findViewById<View>(R.id.tile_sources).setOnClickListener { open(Routes.SOURCES) }
        findViewById<View>(R.id.tile_check).setOnClickListener { open(Routes.CHECK) }
        findViewById<View>(R.id.tile_settings).setOnClickListener { open(Routes.SETTINGS) }

        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) elapsed.text = Labels.time(last.durationMs * value / 1000)
            }
            override fun onStartTrackingTouch(sb: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                dragging = false
                if (last.durationMs > 0) connection.seekTo(last.durationMs * sb.progress / 1000)
            }
        })

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                if (abs(dx) < resources.displayMetrics.density * 80 || abs(vx) < abs(vy) * 1.5f) return false
                if (dx < 0) connection.next() else connection.previous()
                return true
            }
        })

        val root = findViewById<View>(R.id.player_root)
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                StartupMetrics.onFirstFrame()
                return true
            }
        })
        askPermissionsOnce()
        render(NowPlaying())
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Swipes on the progress bar belong to the bar.
        if (!dragging && !isOver(progress, ev)) gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onStart() {
        super.onStart()
        connection = PlayerConnection(applicationContext, uiScope)
        connection.connect()
        combine(connection.state, Lirnyk.prefs.favorites, Lirnyk.library.library) { s, _, _ -> s }.collectWhileVisible(::render)
    }

    override fun onStop() {
        connection.release()
        super.onStop()
    }

    private fun render(s: NowPlaying) {
        last = s
        val track = s.mediaId?.let { Lirnyk.library.library.value[it] }
        if (s.count == 0) {
            title.setText(UiR.string.nothing_playing)
            artist.setText(UiR.string.pick_music_hint)
        } else {
            title.text = s.title ?: track?.title ?: ""
            artist.text = listOfNotNull(s.artist ?: track?.displayArtist, s.album ?: track?.displayAlbum).joinToString(" · ")
                .ifEmpty { getString(UiR.string.unknown_artist) }
        }
        val noteText = when {
            s.waitingForMedia -> getString(UiR.string.waiting_for_media)
            s.error -> getString(UiR.string.playback_error)
            track != null && !Lirnyk.library.library.value.isAvailable(track) -> getString(UiR.string.track_unavailable)
            else -> null
        }
        note.visibility = if (noteText == null) View.GONE else View.VISIBLE
        note.text = noteText
        status.text = if (s.count == 0) "" else listOfNotNull(
            track?.let { Labels.source(this, it.source) }, track?.folderName?.takeIf { it.isNotEmpty() }, "${s.index + 1}/${s.count}",
        ).joinToString(" · ")

        play.setImageResource(if (s.playWhenReady) UiR.drawable.ic_lk_pause_accent else UiR.drawable.ic_lk_play_accent)
        play.contentDescription = getString(if (s.playWhenReady) UiR.string.pause else UiR.string.play)
        shuffle.alpha = if (s.shuffle) 1f else 0.4f
        repeat.alpha = if (s.repeat == RepeatMode.OFF) 0.4f else 1f
        repeat.setImageResource(if (s.repeat == RepeatMode.ONE) UiR.drawable.ic_lk_repeat_one else UiR.drawable.ic_lk_repeat)
        val fav = s.mediaId != null && s.mediaId in Lirnyk.prefs.favorites.value
        favorite.setImageResource(if (fav) UiR.drawable.ic_lk_star else UiR.drawable.ic_lk_star_border)
        favorite.visibility = if (s.mediaId == null) View.INVISIBLE else View.VISIBLE

        if (!dragging) {
            progress.progress = if (s.durationMs > 0) (s.positionMs * 1000 / s.durationMs).toInt() else 0
            elapsed.text = Labels.time(s.positionMs)
        }
        duration.text = if (s.durationMs > 0) Labels.time(s.durationMs) else ""
        progress.isEnabled = s.durationMs > 0

        if (Lirnyk.prefs.keepScreenOn.value && s.playWhenReady) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun askPermissionsOnce() {
        if (asked) return
        asked = true
        val wanted = buildList {
            if (!Lirnyk.library.hasAudioPermission()) add(LibraryRepository.audioPermission)
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), REQ_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS && Lirnyk.library.hasAudioPermission()) Lirnyk.library.rescan()
    }

    private fun isOver(v: View, ev: MotionEvent): Boolean {
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        return ev.rawX >= loc[0] && ev.rawX <= loc[0] + v.width && ev.rawY >= loc[1] && ev.rawY <= loc[1] + v.height
    }

    private companion object {
        const val REQ_PERMISSIONS = 1
        var asked = false
    }
}
