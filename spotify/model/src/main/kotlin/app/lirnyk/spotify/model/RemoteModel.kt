package app.lirnyk.spotify.model

/** Repeat as the Spotify app reports it (PlayerOptions.repeatMode: 0 off, 1 track, 2 context). */
enum class Repeat { OFF, TRACK, CONTEXT;
    companion object { fun of(code: Int) = when (code) { 1 -> TRACK; 2 -> CONTEXT; else -> OFF } }
}

/**
 * The Spotify app's player state, as last received over App Remote. Only what Spotify sent: nothing is invented, and
 * actions the Spotify app forbids (restrictions) are reported so the screen can disable them.
 */
data class RemoteSnapshot(
    val trackUri: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val positionMs: Long,
    /** Local clock when [positionMs] was received: the progress bar moves between updates without polling Spotify. */
    val receivedAtMillis: Long,
    val paused: Boolean,
    val speed: Float,
    val shuffle: Boolean,
    val repeat: Repeat,
    val canSkipNext: Boolean,
    val canSkipPrev: Boolean,
    val canSeek: Boolean,
    val canToggleShuffle: Boolean,
    val canRepeatTrack: Boolean,
    val canRepeatContext: Boolean,
    /** Spotify image reference ("spotify:image:…"), fetched through App Remote's ImagesApi, never from the internet by us. */
    val imageUri: String?,
    val isEpisode: Boolean,
) {
    /** Position now, moving at the playback speed while playing, never past the end. */
    fun positionAt(nowMillis: Long): Long {
        val moved = if (paused || speed <= 0f) positionMs else positionMs + ((nowMillis - receivedAtMillis).coerceAtLeast(0) * speed).toLong()
        return if (durationMs > 0) moved.coerceIn(0, durationMs) else moved.coerceAtLeast(0)
    }

    /** Repeat after one tap, skipping modes the Spotify app does not allow right now. */
    fun nextRepeat(): Repeat? {
        val order = listOf(Repeat.OFF, Repeat.CONTEXT, Repeat.TRACK)
        var i = order.indexOf(repeat)
        repeat(order.size) {
            i = (i + 1) % order.size
            val candidate = order[i]
            val allowed = when (candidate) { Repeat.OFF -> true; Repeat.CONTEXT -> canRepeatContext; Repeat.TRACK -> canRepeatTrack }
            if (allowed && candidate != repeat) return candidate
        }
        return null
    }
}

/** Why App Remote is not connected, mapped from the SDK's exceptions (by class name, so this module stays JVM-only). */
enum class RemoteProblem {
    /** The Spotify app is not installed on this device. */
    SPOTIFY_NOT_INSTALLED,
    /** The user has not allowed Lirnyk Remote yet (or revoked it): authorization is needed. */
    NOT_AUTHORIZED,
    /** Spotify refused this app: package name, SHA-1 or redirect URI do not match the dashboard, or the user is not on the allowlist. */
    AUTHENTICATION_FAILED,
    /** Nobody is logged in to the Spotify app. */
    NOT_LOGGED_IN,
    /** The Spotify app is offline; App Remote can only connect while Spotify is online. */
    OFFLINE,
    /** The installed Spotify app is too old for a requested feature. */
    SPOTIFY_TOO_OLD,
    /** The Spotify app closed the connection (it was stopped, updated or killed). */
    CONNECTION_LOST,
    /** Android refused to start Spotify's remote service (background limits). */
    SERVICE_START_REFUSED,
    UNKNOWN;

    companion object {
        fun fromThrowable(t: Throwable): RemoteProblem = fromClassName(t.javaClass.simpleName)

        fun fromClassName(name: String): RemoteProblem = when (name) {
            "CouldNotFindSpotifyApp" -> SPOTIFY_NOT_INSTALLED
            "UserNotAuthorizedException" -> NOT_AUTHORIZED
            "AuthenticationFailedException" -> AUTHENTICATION_FAILED
            "NotLoggedInException", "LoggedOutException" -> NOT_LOGGED_IN
            "OfflineModeException" -> OFFLINE
            "UnsupportedFeatureVersionException" -> SPOTIFY_TOO_OLD
            "SpotifyConnectionTerminatedException", "SpotifyDisconnectedException" -> CONNECTION_LOST
            "SpotifyRemoteServiceException" -> SERVICE_START_REFUSED
            else -> UNKNOWN
        }
    }
}

/** What the screen shows about the connection. */
sealed interface RemoteStatus {
    /** Never connected on this device, or the user disconnected and deleted the data. */
    data object NotSetUp : RemoteStatus
    data object Connecting : RemoteStatus
    data object Connected : RemoteStatus
    /** Was connected and authorized; the user paused the connection. Reconnecting needs no new authorization. */
    data object Disconnected : RemoteStatus
    data class Problem(val problem: RemoteProblem, val detail: String?) : RemoteStatus
}

/**
 * When to try again after the Spotify app dropped the connection. Only for drops of an authorized connection, never with
 * the authorization view (that is always the user's tap), and never for problems a retry cannot fix.
 */
object ReconnectPolicy {
    private val delays = listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)

    fun shouldRetry(problem: RemoteProblem): Boolean = problem == RemoteProblem.CONNECTION_LOST || problem == RemoteProblem.SERVICE_START_REFUSED || problem == RemoteProblem.OFFLINE

    /** Delay before attempt [attempt] (0-based), or null to stop trying. */
    fun delayFor(attempt: Int): Long? = delays.getOrNull(attempt)
}

/** Compares a signing certificate fingerprint with the one registered in the Spotify dashboard, ignoring format. */
object Fingerprint {
    fun normalize(s: String?): String? = s?.uppercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() }

    fun matches(actual: String?, expected: String?): Boolean? {
        val a = normalize(actual) ?: return null
        val e = normalize(expected) ?: return null
        return a == e
    }

    /** "AB:CD:…" form for display. */
    fun pretty(hex: String?): String? = normalize(hex)?.chunked(2)?.joinToString(":")
}
