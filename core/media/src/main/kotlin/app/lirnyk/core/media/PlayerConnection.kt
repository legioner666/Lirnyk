package app.lirnyk.core.media

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.lirnyk.core.media.PlaybackService.Companion.toModel
import app.lirnyk.core.media.PlaybackService.Companion.toPlayer
import app.lirnyk.core.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the player screen shows. Only real player state: nothing is filled in for show. */
data class NowPlaying(
    val connected: Boolean = false,
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val index: Int = 0,
    val count: Int = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val waitingForMedia: Boolean = false,
    val error: Boolean = false,
)

/**
 * A screen's link to [PlaybackService]. Connect in onStart, release in onStop: the service keeps playing without it.
 * Position is polled only while connected (twice a second), so a closed screen costs nothing.
 */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {
    private var controller: MediaController? = null
    private var pending: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var ticker: Job? = null
    /** Commands given before the connection is up (a tap right after the screen opened) run once it is. */
    private val pendingActions = ArrayList<(MediaController) -> Unit>()
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    fun connect() {
        if (controller != null || pending != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) = publish()
        }).buildAsync()
        pending = f
        f.addListener({
            pending = null
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            pendingActions.toList().also { pendingActions.clear() }.forEach { it(c) }
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()
            })
            publish()
            ticker = scope.launch { while (isActive) { delay(500); if (controller?.isPlaying == true) publish() } }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        ticker?.cancel(); ticker = null
        pending?.let { MediaController.releaseFuture(it) }; pending = null
        pendingActions.clear()
        controller?.release(); controller = null
        _state.value = _state.value.copy(connected = false)
    }

    private fun publish() {
        val c = controller ?: return
        val md = c.mediaMetadata
        val extras = c.sessionExtras
        _state.value = NowPlaying(
            connected = true,
            mediaId = c.currentMediaItem?.mediaId,
            title = md.title?.toString(),
            artist = md.artist?.toString(),
            album = md.albumTitle?.toString(),
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            index = c.currentMediaItemIndex,
            count = c.mediaItemCount,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode.toModel(),
            waitingForMedia = extras.getBoolean(PlaybackService.EXTRA_WAITING),
            error = extras.getBoolean(PlaybackService.EXTRA_ERROR),
        )
    }

    // ---------------- commands ----------------

    /** Plays [ids] (a folder, an album, the search result) from [startId]. Only ids go over: the service resolves them. */
    fun play(ids: List<String>, startId: String?, then: () -> Unit = {}) = withController { c ->
        val start = startId?.let { ids.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        c.setMediaItems(ids.map { MediaItem.Builder().setMediaId(it).build() }, start, 0)
        c.prepare(); c.play()
        then()
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: pendingActions.add(block)
    }

    fun togglePlay() = withController { c ->
        if (c.playWhenReady) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
            c.play()
        }
    }

    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    /** Like a car stereo: after 3 s into a track "previous" restarts it, otherwise it goes to the previous track. */
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode.toModel()) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }.toPlayer()
    }
}
