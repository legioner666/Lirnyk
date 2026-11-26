package app.lirnyk.spotify.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The Spotify App Remote SDK behind an interface: the real one in the app, a fake in tests. */
interface RemotePort {
    fun isSpotifyInstalled(): Boolean

    /** Connects; [showAuthView] lets Spotify show its authorization screen. Exactly one of the callbacks is called. */
    fun connect(showAuthView: Boolean, onConnected: () -> Unit, onFailure: (Throwable) -> Unit)

    /** Player state updates until [disconnect]; [onError] when the subscription breaks. */
    fun subscribe(onState: (RemoteSnapshot) -> Unit, onError: (Throwable) -> Unit)

    fun disconnect()

    fun resume(); fun pause(); fun skipNext(); fun skipPrevious()
    fun seekTo(positionMs: Long); fun setShuffle(on: Boolean); fun setRepeat(repeat: Repeat)
}

/** What Lirnyk Remote keeps about the account: only the fact that the user authorized it once, and when. */
interface AccountStore {
    var authorizedAtMillis: Long?
    /** Deletes everything this app stored about the Spotify account. */
    fun clear()
}

/**
 * Connection and player state for the screen (TASK 003). One connection at a time; reconnects by itself only after Spotify
 * dropped an authorized connection; authorization is shown only when the user taps "Connect".
 */
class RemoteController(
    private val port: RemotePort,
    private val account: AccountStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val _status = MutableStateFlow<RemoteStatus>(if (account.authorizedAtMillis != null) RemoteStatus.Disconnected else RemoteStatus.NotSetUp)
    val status: StateFlow<RemoteStatus> = _status.asStateFlow()

    private val _snapshot = MutableStateFlow<RemoteSnapshot?>(null)
    val snapshot: StateFlow<RemoteSnapshot?> = _snapshot.asStateFlow()

    /** Bumped on every connect/disconnect, so a late SDK callback of an abandoned attempt changes nothing. */
    private var generation = 0
    private var retryJob: Job? = null
    private var wantConnected = false

    /** The user tapped "Connect" (authorization screen allowed). */
    fun connectInteractive() = connect(showAuthView = true, attempt = 0)

    /** Screen opened: reconnect silently if this device was authorized before; otherwise wait for the user's tap. */
    fun connectIfAuthorized() {
        if (account.authorizedAtMillis != null && _status.value !is RemoteStatus.Connected && _status.value !is RemoteStatus.Connecting) {
            connect(showAuthView = false, attempt = 0)
        }
    }

    private fun connect(showAuthView: Boolean, attempt: Int) {
        if (!port.isSpotifyInstalled()) { _status.value = RemoteStatus.Problem(RemoteProblem.SPOTIFY_NOT_INSTALLED, null); return }
        retryJob?.cancel()
        port.disconnect()
        val gen = ++generation
        wantConnected = true
        _status.value = RemoteStatus.Connecting
        port.connect(showAuthView,
            onConnected = {
                if (gen != generation) { return@connect }
                if (account.authorizedAtMillis == null) account.authorizedAtMillis = clock()
                _status.value = RemoteStatus.Connected
                port.subscribe(
                    onState = { if (gen == generation) _snapshot.value = it },
                    onError = { if (gen == generation) onDropped(it, attempt) },
                )
            },
            onFailure = { if (gen == generation) onDropped(it, attempt) },
        )
    }

    private fun onDropped(t: Throwable, attempt: Int) {
        val problem = RemoteProblem.fromThrowable(t)
        _status.value = RemoteStatus.Problem(problem, t.message?.take(200))
        if (problem == RemoteProblem.NOT_AUTHORIZED) account.authorizedAtMillis = null   // revoked in the Spotify account
        if (!wantConnected || account.authorizedAtMillis == null || !ReconnectPolicy.shouldRetry(problem)) return
        val wait = ReconnectPolicy.delayFor(attempt) ?: return
        retryJob = scope.launch {
            delay(wait)
            if (wantConnected) connect(showAuthView = false, attempt = attempt + 1)
        }
    }

    /** Screen closed or the user paused the connection: authorization stays, the connection ends. */
    fun disconnect() {
        wantConnected = false
        retryJob?.cancel()
        generation++
        port.disconnect()
        _snapshot.value = null
        _status.value = if (account.authorizedAtMillis != null) RemoteStatus.Disconnected else RemoteStatus.NotSetUp
    }

    /**
     * "Disconnect and delete data": ends the connection and deletes everything this app stored (authorization mark, cached
     * state, cover images held by the caller). Revoking the authorization itself happens in the Spotify account.
     */
    fun forgetAccount() {
        disconnect()
        account.clear()
        _status.value = RemoteStatus.NotSetUp
    }

    // ---------------- commands: only when connected and allowed by Spotify ----------------

    private inline fun whenConnected(block: (RemoteSnapshot?) -> Unit) { if (_status.value == RemoteStatus.Connected) block(_snapshot.value) }

    fun togglePlay() = whenConnected { s -> if (s == null || s.paused) port.resume() else port.pause() }
    fun next() = whenConnected { s -> if (s?.canSkipNext != false) port.skipNext() }
    fun previous() = whenConnected { s -> if (s?.canSkipPrev != false) port.skipPrevious() }
    fun seekTo(ms: Long) = whenConnected { s -> if (s?.canSeek == true) port.seekTo(ms.coerceIn(0, s.durationMs)) }
    fun toggleShuffle() = whenConnected { s -> if (s?.canToggleShuffle == true) port.setShuffle(!s.shuffle) }
    fun cycleRepeat() = whenConnected { s -> s?.nextRepeat()?.let(port::setRepeat) }
}
