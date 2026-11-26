package app.lirnyk.remote

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import app.lirnyk.spotify.model.AccountStore
import app.lirnyk.spotify.model.RemotePort
import app.lirnyk.spotify.model.RemoteSnapshot
import app.lirnyk.spotify.model.Repeat
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerState
import java.security.MessageDigest

/**
 * The official Spotify App Remote SDK behind [RemotePort]. Authorization is the SDK's built-in one (README of
 * spotify/android-sdk, "Use built-in authorization mechanism in Spotify App Remote"): the Spotify app shows its own consent
 * screen for the app-remote-control scope; no token reaches this app, so there is nothing secret to store.
 */
class SpotifyPort(private val context: Context, private val clock: () -> Long) : RemotePort {
    private var remote: SpotifyAppRemote? = null
    private var subscription: Subscription<PlayerState>? = null

    override fun isSpotifyInstalled(): Boolean = SpotifyAppRemote.isSpotifyInstalled(context)

    override fun connect(showAuthView: Boolean, onConnected: () -> Unit, onFailure: (Throwable) -> Unit) {
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(showAuthView)
            .build()
        SpotifyAppRemote.connect(context.applicationContext, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) { remote = appRemote; onConnected() }
            override fun onFailure(error: Throwable) { onFailure(error) }
        })
    }

    override fun subscribe(onState: (RemoteSnapshot) -> Unit, onError: (Throwable) -> Unit) {
        val r = remote ?: return
        subscription?.cancel()
        val sub = r.playerApi.subscribeToPlayerState()
        sub.setEventCallback { s -> onState(s.toSnapshot(clock())) }
        sub.setErrorCallback { t -> onError(t) }
        subscription = sub
    }

    override fun disconnect() {
        subscription?.let { if (!it.isCanceled) it.cancel() }
        subscription = null
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
    }

    override fun resume() { remote?.playerApi?.resume() }
    override fun pause() { remote?.playerApi?.pause() }
    override fun skipNext() { remote?.playerApi?.skipNext() }
    override fun skipPrevious() { remote?.playerApi?.skipPrevious() }
    override fun seekTo(positionMs: Long) { remote?.playerApi?.seekTo(positionMs) }
    override fun setShuffle(on: Boolean) { remote?.playerApi?.setShuffle(on) }
    override fun setRepeat(repeat: Repeat) { remote?.playerApi?.setRepeat(when (repeat) { Repeat.OFF -> 0; Repeat.TRACK -> 1; Repeat.CONTEXT -> 2 }) }

    /** Cover art through App Remote's ImagesApi (the Spotify app delivers it; this app makes no network request). */
    fun loadImage(imageUri: String, onBitmap: (Bitmap?) -> Unit) {
        val r = remote ?: return onBitmap(null)
        r.imagesApi.getImage(ImageUri(imageUri), Image.Dimension.MEDIUM)
            .setResultCallback { onBitmap(it) }
            .setErrorCallback { onBitmap(null) }
    }

    private fun PlayerState.toSnapshot(now: Long): RemoteSnapshot {
        val t = track
        val r = playbackRestrictions
        return RemoteSnapshot(
            trackUri = t?.uri, title = t?.name, artist = t?.artist?.name, album = t?.album?.name,
            durationMs = t?.duration ?: 0, positionMs = playbackPosition, receivedAtMillis = now,
            paused = isPaused, speed = playbackSpeed,
            shuffle = playbackOptions?.isShuffling == true, repeat = Repeat.of(playbackOptions?.repeatMode ?: 0),
            canSkipNext = r?.canSkipNext == true, canSkipPrev = r?.canSkipPrev == true, canSeek = r?.canSeek == true,
            canToggleShuffle = r?.canToggleShuffle == true, canRepeatTrack = r?.canRepeatTrack == true, canRepeatContext = r?.canRepeatContext == true,
            imageUri = t?.imageUri?.raw, isEpisode = t?.isEpisode == true,
        )
    }
}

/** The only account data kept: when this device was authorized. "Delete data" removes it with the cover cache. */
class PrefsAccountStore(context: Context, private val covers: LruCache<String, Bitmap>) : AccountStore {
    private val sp = context.getSharedPreferences("spotify_account", Context.MODE_PRIVATE)

    override var authorizedAtMillis: Long?
        get() = sp.getLong(KEY, -1).takeIf { it >= 0 }
        set(v) { sp.edit().apply { if (v == null) remove(KEY) else putLong(KEY, v) }.apply() }

    override fun clear() {
        sp.edit().clear().commit()
        covers.evictAll()
    }

    private companion object { const val KEY = "authorized_at" }
}

/** This APK's signing certificate SHA-1: the value the Spotify dashboard must contain (TASK 003 item 9). */
object SigningInfo {
    fun sha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val cert = certs?.firstOrNull() ?: return null
        MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString(":") { "%02X".format(it) }
    }.getOrNull()
}
