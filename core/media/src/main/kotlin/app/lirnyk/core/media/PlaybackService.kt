package app.lirnyk.core.media

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
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
import app.lirnyk.core.model.FailureBudget
import app.lirnyk.core.model.PlayQueue
import app.lirnyk.core.model.RepeatMode
import app.lirnyk.core.model.ResumePlanner
import app.lirnyk.core.model.ResumeState
import app.lirnyk.core.model.Track
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
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
    private lateinit var exo: ExoPlayer
    private lateinit var player: Player
    private lateinit var session: MediaSession
    private var saveJob: Job? = null
    private var waitJob: Job? = null
    private val failures = FailureBudget()
    private var restored = false

    override fun onCreate() {
        super.onCreate()
        StartupMetrics.onServiceCreated()
        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo.addListener(listener)
        // Every command from any controller (screen, notification, steering wheel) passes here first: an explicit play,
        // pause, stop or new queue is the user's decision and ends a pending "continue when the drive appears".
        player = object : ForwardingPlayer(exo) {
            override fun play() { cancelWait(); super.play() }
            override fun pause() { cancelWait(); super.pause() }
            override fun stop() { cancelWait(); super.stop() }
            override fun setPlayWhenReady(playWhenReady: Boolean) { cancelWait(); super.setPlayWhenReady(playWhenReady) }
            override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
                cancelWait(); setExtra(EXTRA_MISSING, false); super.setMediaItems(mediaItems, startIndex, startPositionMs)
            }
            override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
                cancelWait(); setExtra(EXTRA_MISSING, false); super.setMediaItems(mediaItems, resetPosition)
            }
        }
        session = MediaSession.Builder(this, player).setCallback(callback).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).setChannelName(R.string.lk_channel_playback).build())
        scope.launch { restore() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BOOT_START) {
            BootLog.note("service started after boot")
            // If the restore decides not to play, do not keep a service running for nothing.
            scope.launch {
                delay(AutoResumePolicy.WAIT_FOR_MEDIA_MILLIS + 5_000)
                if (!exo.playWhenReady) { BootLog.note("not playing after boot: service stopped"); stopSelf() }
            }
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // In a car the screen gets closed all the time; music keeps going. Stop only when nothing plays.
        save()
        if (!exo.playWhenReady || exo.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        save()
        scope.cancel()
        session.release()
        exo.release()
        super.onDestroy()
    }

    // ---------------- restore after a start of the head unit ----------------

    private suspend fun restore() {
        val lib = Lirnyk.library
        lib.loaded.first { it }
        restored = true
        if (exo.mediaItemCount > 0) return   // a controller already chose something
        val library = lib.library.value
        val state = ResumeState.decode(Lirnyk.prefs.resumeRecord)
        val plan = ResumePlanner.plan(state, Lirnyk.prefs.autoResume.value, known = { library[it] != null }, available = { library.isAvailable(it) })
        when (plan) {
            ResumePlanner.Plan.Nothing -> Unit
            is ResumePlanner.Plan.MissingTrack -> { setExtra(EXTRA_MISSING, true); BootLog.note("saved track missing: nothing restored") }
            is ResumePlanner.Plan.Restore -> {
                exo.setMediaItems(plan.ids.mapNotNull { library[it] }.map(::toItem), plan.startIndex, plan.positionMs)
                exo.shuffleModeEnabled = state?.queue?.shuffle == true
                exo.repeatMode = (state?.queue?.repeat ?: RepeatMode.OFF).toPlayer()
                val current = plan.ids[plan.startIndex]
                when (plan.decision) {
                    AutoResumePolicy.Decision.RESUME -> { exo.prepare(); exo.play(); BootLog.note("resumed") }
                    AutoResumePolicy.Decision.PREPARE_PAUSED -> if (library.isAvailable(current)) exo.prepare()
                    AutoResumePolicy.Decision.WAIT_FOR_MEDIA -> waitForMedia(current)
                    AutoResumePolicy.Decision.NOTHING -> Unit
                }
            }
        }
    }

    /** The saved track is on a drive that is not mounted yet (USB is often slower than the system): wait, then continue. */
    private fun waitForMedia(id: String) {
        setExtra(EXTRA_WAITING, true)
        BootLog.note("waiting for the drive")
        waitJob = scope.launch {
            val ok = withTimeoutOrNull(AutoResumePolicy.WAIT_FOR_MEDIA_MILLIS) { Lirnyk.library.library.first { it.isAvailable(id) } } != null
            setExtra(EXTRA_WAITING, false)
            waitJob = null
            if (ok && exo.currentMediaItem?.mediaId == id) { exo.prepare(); exo.play(); BootLog.note("drive appeared: resumed") }
            else if (!ok) BootLog.note("drive did not appear in time")
        }
    }

    private fun cancelWait() {
        if (waitJob == null) return
        waitJob?.cancel(); waitJob = null
        setExtra(EXTRA_WAITING, false)
    }

    private fun resolve(items: List<MediaItem>): MutableList<MediaItem> {
        val library = Lirnyk.library.library.value
        return items.mapNotNull { item -> library[item.mediaId]?.let(::toItem) }.toMutableList()
    }

    // ---------------- saving ----------------

    private fun save() {
        if (!restored || exo.mediaItemCount == 0) return
        val ids = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }
        val queue = PlayQueue(ids, ids.indices.toList(), exo.currentMediaItemIndex.coerceIn(0, ids.size - 1), exo.shuffleModeEnabled, exo.repeatMode.toModel())
        Lirnyk.prefs.resumeRecord = ResumeState(queue, exo.currentPosition.coerceAtLeast(0), exo.playWhenReady, System.currentTimeMillis()).encode()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) { failures.onPlaying(); setExtra(EXTRA_ERROR, false) }
            save()
            saveJob?.cancel()
            if (isPlaying) saveJob = scope.launch { while (isActive) { delay(SAVE_EVERY_MS); save() } }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = save()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = save()

        override fun onPlayerError(error: PlaybackException) {
            // A file that cannot be played (drive pulled, unsupported codec): say so and move on, at most once around
            // the queue (also with repeat-all), then pause and leave the error on screen.
            setExtra(EXTRA_ERROR, true)
            if (failures.onFailure(exo.mediaItemCount) && (exo.hasNextMediaItem() || exo.repeatMode == Player.REPEAT_MODE_ALL)) {
                exo.seekToNextMediaItem()
                exo.prepare()
            } else {
                exo.pause()
            }
        }
    }

    private fun setExtra(key: String, value: Boolean) {
        if (!::session.isInitialized) return
        val b = Bundle(session.sessionExtras)
        if (b.containsKey(key) && b.getBoolean(key) == value) return
        b.putBoolean(key, value)
        session.setSessionExtras(b)
    }

    // ---------------- session callback ----------------

    private val callback = object : MediaSession.Callback {
        /**
         * Lirnyk's own screens, the system (notification, media buttons) and trusted controllers get full control. Other apps
         * may play, pause, skip and seek, but cannot replace the queue (GPT audit P1).
         */
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.packageName == packageName || controller.isTrusted) return super.onConnect(session, controller)
            val limited = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .removeAll(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailablePlayerCommands(limited).build()
        }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(resolve(mediaItems))

        /** System "resume" (Android 11+): the exact saved track or nothing, after the saved library index is read. */
        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                Lirnyk.library.loaded.first { it }
                val library = Lirnyk.library.library.value
                val plan = ResumePlanner.plan(ResumeState.decode(Lirnyk.prefs.resumeRecord), true, { library[it] != null }, { library.isAvailable(it) })
                if (plan is ResumePlanner.Plan.Restore) {
                    result.set(MediaSession.MediaItemsWithStartPosition(plan.ids.mapNotNull { library[it] }.map(::toItem), plan.startIndex, plan.positionMs))
                } else {
                    result.setException(UnsupportedOperationException("nothing to resume"))
                }
            }
            return result
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
        const val EXTRA_MISSING = "lirnyk.saved_track_missing"
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
