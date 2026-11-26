package app.lirnyk.core.media

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.lirnyk.core.model.AutoResumePolicy
import app.lirnyk.core.model.PlayQueue
import app.lirnyk.core.model.RepeatMode
import app.lirnyk.core.model.ResumeState
import app.lirnyk.core.model.Track
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays the local library. Lives independently of the screens: the UI can be closed or killed and music goes on.
 * Audio focus is handled by ExoPlayer, so calls and navigation prompts pause or duck the music and give it back.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private var saveJob: Job? = null
    private var waitJob: Job? = null
    private var consecutiveErrors = 0
    private var restored = false

    override fun onCreate() {
        super.onCreate()
        StartupMetrics.onServiceCreated()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(listener)
        session = MediaSession.Builder(this, player).setCallback(callback).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).setChannelName(R.string.lk_channel_playback).build())
        scope.launch { restore() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BOOT_START) {
            // Started by BootReceiver: if the restore decides not to play, do not keep a service running for nothing.
            scope.launch {
                delay(AutoResumePolicy.WAIT_FOR_MEDIA_MILLIS + 5_000)
                if (!player.playWhenReady) stopSelf()
            }
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // In a car the screen gets closed all the time; music keeps going. Stop only when nothing plays.
        save()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        save()
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    // ---------------- restore after a start of the head unit ----------------

    private suspend fun restore() {
        val lib = Lirnyk.library
        lib.loaded.first { it }
        val state = ResumeState.decode(Lirnyk.prefs.resumeRecord)
        restored = true
        if (state == null || player.mediaItemCount > 0) return
        val library = lib.library.value
        val ids = state.queue.ids.filter { library[it] != null }
        if (ids.isEmpty()) return
        val current = state.queue.currentId?.takeIf { it in ids } ?: ids.first()
        setQueue(ids.mapNotNull { library[it] }, current, state.positionMs, play = false)
        player.shuffleModeEnabled = state.queue.shuffle
        player.repeatMode = state.queue.repeat.toPlayer()
        when (AutoResumePolicy.decide(state, Lirnyk.prefs.autoResume.value) { library.isAvailable(it) }) {
            AutoResumePolicy.Decision.RESUME -> { player.prepare(); player.play() }
            AutoResumePolicy.Decision.PREPARE_PAUSED -> if (library.isAvailable(current)) player.prepare()
            AutoResumePolicy.Decision.WAIT_FOR_MEDIA -> waitForMedia(current)
            AutoResumePolicy.Decision.NOTHING -> Unit
        }
    }

    /** The saved track is on a drive that is not mounted yet (USB is often slower than the system): wait, then continue. */
    private fun waitForMedia(id: String) {
        setExtra(EXTRA_WAITING, true)
        waitJob = scope.launch {
            val ok = withTimeoutOrNull(AutoResumePolicy.WAIT_FOR_MEDIA_MILLIS) { Lirnyk.library.library.first { it.isAvailable(id) } } != null
            setExtra(EXTRA_WAITING, false)
            if (ok && player.currentMediaItem?.mediaId == id) { player.prepare(); player.play() }
        }
    }

    // ---------------- queue ----------------

    private fun setQueue(tracks: List<Track>, startId: String, positionMs: Long, play: Boolean) {
        val items = tracks.map(::toItem)
        val start = tracks.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        player.setMediaItems(items, start, positionMs)
        if (play) { player.prepare(); player.play() }
    }

    private fun resolve(items: List<MediaItem>): MutableList<MediaItem> {
        val library = Lirnyk.library.library.value
        return items.mapNotNull { item -> library[item.mediaId]?.let(::toItem) }.toMutableList()
    }

    // ---------------- saving ----------------

    private fun save() {
        if (!restored || player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val queue = PlayQueue(ids, ids.indices.toList(), player.currentMediaItemIndex.coerceIn(0, ids.size - 1), player.shuffleModeEnabled, player.repeatMode.toModel())
        Lirnyk.prefs.resumeRecord = ResumeState(queue, player.currentPosition.coerceAtLeast(0), player.playWhenReady, System.currentTimeMillis()).encode()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            save()
            saveJob?.cancel()
            if (isPlaying) saveJob = scope.launch { while (isActive) { delay(SAVE_EVERY_MS); save() } }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) consecutiveErrors = 0
            setExtra(EXTRA_ERROR, false)
            save()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) { waitJob?.cancel(); setExtra(EXTRA_WAITING, false) }
            save()
        }

        override fun onPlayerError(error: PlaybackException) {
            // A file that cannot be played (drive pulled, unsupported codec): say so and move on, never loop forever.
            setExtra(EXTRA_ERROR, true)
            consecutiveErrors++
            if (consecutiveErrors < player.mediaItemCount && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
            }
        }
    }

    private fun setExtra(key: String, value: Boolean) {
        val b = Bundle(session.sessionExtras)
        if (b.getBoolean(key) == value && b.containsKey(key)) return
        b.putBoolean(key, value)
        session.setSessionExtras(b)
    }

    // ---------------- session callback ----------------

    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
            waitJob?.cancel(); setExtra(EXTRA_WAITING, false)
            return Futures.immediateFuture(resolve(mediaItems))
        }

        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val state = ResumeState.decode(Lirnyk.prefs.resumeRecord)
            val library = Lirnyk.library.library.value
            val tracks = state?.queue?.ids.orEmpty().mapNotNull { library[it] }
            if (state == null || tracks.isEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException("nothing to resume"))
            val start = tracks.indexOfFirst { it.id == state.queue.currentId }.coerceAtLeast(0)
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(tracks.map(::toItem), start, state.positionMs))
        }

        override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
            @Suppress("DEPRECATION")
            val key = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (key != null && key.action == KeyEvent.ACTION_DOWN) KeyLog.record(key.keyCode, "media button")
            return false   // the session handles it as usual
        }
    }

    companion object {
        const val ACTION_BOOT_START = "app.lirnyk.action.BOOT_START"
        const val EXTRA_WAITING = "lirnyk.waiting_for_media"
        const val EXTRA_ERROR = "lirnyk.error"
        private const val SAVE_EVERY_MS = 10_000L

        fun toItem(t: Track): MediaItem = MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(t.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.displayArtist)
                    .setAlbumTitle(t.displayAlbum)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build(),
            )
            .build()

        fun RepeatMode.toPlayer(): Int = when (this) { RepeatMode.OFF -> Player.REPEAT_MODE_OFF; RepeatMode.ALL -> Player.REPEAT_MODE_ALL; RepeatMode.ONE -> Player.REPEAT_MODE_ONE }
        fun Int.toModel(): RepeatMode = when (this) { Player.REPEAT_MODE_ALL -> RepeatMode.ALL; Player.REPEAT_MODE_ONE -> RepeatMode.ONE; else -> RepeatMode.OFF }
    }
}
