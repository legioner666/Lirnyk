package app.lirnyk.spotify.model

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class UserNotAuthorizedException : RuntimeException("not authorized")
private class SpotifyConnectionTerminatedException : RuntimeException("terminated")
private class AuthenticationFailedException : RuntimeException("bad fingerprint")

private class FakePort : RemotePort {
    var installed = true
    val connects = mutableListOf<Boolean>()
    var disconnects = 0
    val commands = mutableListOf<String>()
    private var onConnected: (() -> Unit)? = null
    private var onFailure: ((Throwable) -> Unit)? = null
    private var onState: ((RemoteSnapshot) -> Unit)? = null
    private var onSubError: ((Throwable) -> Unit)? = null

    override fun isSpotifyInstalled() = installed
    override fun connect(showAuthView: Boolean, onConnected: () -> Unit, onFailure: (Throwable) -> Unit) {
        connects += showAuthView; this.onConnected = onConnected; this.onFailure = onFailure
    }
    override fun subscribe(onState: (RemoteSnapshot) -> Unit, onError: (Throwable) -> Unit) { this.onState = onState; onSubError = onError }
    override fun disconnect() { disconnects++ }
    override fun resume() { commands += "resume" }
    override fun pause() { commands += "pause" }
    override fun skipNext() { commands += "next" }
    override fun skipPrevious() { commands += "prev" }
    override fun seekTo(positionMs: Long) { commands += "seek $positionMs" }
    override fun setShuffle(on: Boolean) { commands += "shuffle $on" }
    override fun setRepeat(repeat: Repeat) { commands += "repeat $repeat" }

    fun succeed() = onConnected!!.invoke()
    fun fail(t: Throwable) = onFailure!!.invoke(t)
    fun push(s: RemoteSnapshot) = onState!!.invoke(s)
    fun drop(t: Throwable) = onSubError!!.invoke(t)
}

private class MemoryAccount : AccountStore {
    override var authorizedAtMillis: Long? = null
    var cleared = 0
    override fun clear() { authorizedAtMillis = null; cleared++ }
}

private fun snap(paused: Boolean = false, pos: Long = 10_000, at: Long = 0, restrictions: Boolean = true, repeat: Repeat = Repeat.OFF) =
    RemoteSnapshot("spotify:track:1", "Song", "Artist", "Album", 200_000, pos, at, paused, 1f, false, repeat,
        restrictions, restrictions, restrictions, restrictions, restrictions, restrictions, "spotify:image:abc", false)

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControllerTest {
    @Test fun firstConnectShowsAuthorizationAndRemembersOnlyTheFact() = runTest {
        val port = FakePort(); val acc = MemoryAccount()
        val c = RemoteController(port, acc, backgroundScope) { 1234 }
        assertEquals(RemoteStatus.NotSetUp, c.status.value)
        c.connectIfAuthorized()
        assertTrue("never authorized: no silent connect", port.connects.isEmpty())
        c.connectInteractive()
        assertEquals(listOf(true), port.connects)
        assertEquals(RemoteStatus.Connecting, c.status.value)
        port.succeed()
        assertEquals(RemoteStatus.Connected, c.status.value)
        assertEquals(1234L, acc.authorizedAtMillis)
        port.push(snap())
        assertEquals("Song", c.snapshot.value?.title)
    }

    @Test fun laterStartsReconnectSilently() = runTest {
        val port = FakePort(); val acc = MemoryAccount().apply { authorizedAtMillis = 1 }
        val c = RemoteController(port, acc, backgroundScope) { 0 }
        assertEquals(RemoteStatus.Disconnected, c.status.value)
        c.connectIfAuthorized()
        assertEquals("no authorization screen on a silent reconnect", listOf(false), port.connects)
    }

    @Test fun revokedAuthorizationAsksTheUserAgainAndIsNotRetried() = runTest {
        val port = FakePort(); val acc = MemoryAccount().apply { authorizedAtMillis = 1 }
        val c = RemoteController(port, acc, backgroundScope) { 0 }
        c.connectIfAuthorized()
        port.fail(UserNotAuthorizedException())
        assertEquals(RemoteProblem.NOT_AUTHORIZED, (c.status.value as RemoteStatus.Problem).problem)
        assertNull(acc.authorizedAtMillis)
        advanceTimeBy(120_000); runCurrent()
        assertEquals("no automatic retry", 1, port.connects.size)
    }

    @Test fun wrongFingerprintIsReportedNotRetried() = runTest {
        val port = FakePort(); val acc = MemoryAccount()
        val c = RemoteController(port, acc, backgroundScope) { 0 }
        c.connectInteractive()
        port.fail(AuthenticationFailedException())
        assertEquals(RemoteProblem.AUTHENTICATION_FAILED, (c.status.value as RemoteStatus.Problem).problem)
        advanceTimeBy(120_000); runCurrent()
        assertEquals(1, port.connects.size)
    }

    @Test fun droppedConnectionIsRetriedWithBackoffAndStopsWhenTheUserDisconnects() = runTest {
        val port = FakePort(); val acc = MemoryAccount()
        val c = RemoteController(port, acc, backgroundScope) { 0 }
        c.connectInteractive(); port.succeed()
        port.drop(SpotifyConnectionTerminatedException())
        assertEquals(RemoteProblem.CONNECTION_LOST, (c.status.value as RemoteStatus.Problem).problem)
        advanceTimeBy(1_999); runCurrent()
        assertEquals(1, port.connects.size)
        advanceTimeBy(1); runCurrent()
        assertEquals("retry after 2 s, silently", listOf(true, false), port.connects)
        c.disconnect()
        advanceTimeBy(600_000); runCurrent()
        assertEquals(2, port.connects.size)
        assertEquals(RemoteStatus.Disconnected, c.status.value)
    }

    @Test fun lateCallbackOfAnAbandonedAttemptChangesNothing() = runTest {
        val port = FakePort(); val acc = MemoryAccount()
        val c = RemoteController(port, acc, backgroundScope) { 0 }
        c.connectInteractive()
        c.disconnect()
        port.succeed()
        assertEquals(RemoteStatus.NotSetUp, c.status.value)
        assertNull(acc.authorizedAtMillis)
    }

    @Test fun forgetAccountDeletesLocalDataAndNeedsAuthorizationAgain() = runTest {
        val port = FakePort(); val acc = MemoryAccount()
        val c = RemoteController(port, acc, backgroundScope) { 5 }
        c.connectInteractive(); port.succeed(); port.push(snap())
        c.forgetAccount()
        assertEquals(1, acc.cleared)
        assertNull(c.snapshot.value)
        assertEquals(RemoteStatus.NotSetUp, c.status.value)
        c.connectIfAuthorized()
        assertEquals("no silent reconnect after forgetting", 1, port.connects.size)
        c.connectInteractive()
        assertEquals(listOf(true, true), port.connects)
    }

    @Test fun commandsRespectConnectionAndSpotifyRestrictions() = runTest {
        val port = FakePort(); val acc = MemoryAccount()
        val c = RemoteController(port, acc, backgroundScope) { 0 }
        c.togglePlay()
        assertTrue("not connected: nothing sent", port.commands.isEmpty())
        c.connectInteractive(); port.succeed()
        port.push(snap(paused = true))
        c.togglePlay(); c.next(); c.seekTo(500_000); c.toggleShuffle(); c.cycleRepeat()
        assertEquals(listOf("resume", "next", "seek 200000", "shuffle true", "repeat CONTEXT"), port.commands)
        port.commands.clear()
        port.push(snap(restrictions = false))
        c.next(); c.previous(); c.seekTo(1); c.toggleShuffle(); c.cycleRepeat(); c.togglePlay()
        assertEquals("restricted: only play/pause goes through", listOf("pause"), port.commands)
    }

    @Test fun notInstalled() = runTest {
        val port = FakePort().apply { installed = false }
        val c = RemoteController(port, MemoryAccount(), backgroundScope) { 0 }
        c.connectInteractive()
        assertEquals(RemoteProblem.SPOTIFY_NOT_INSTALLED, (c.status.value as RemoteStatus.Problem).problem)
        assertTrue(port.connects.isEmpty())
    }
}

class RemoteModelTest {
    @Test fun progressMovesLocallyBetweenUpdates() {
        val s = snap(pos = 10_000, at = 1_000)
        assertEquals(15_000, s.positionAt(6_000))
        assertEquals("never past the end", 200_000, s.positionAt(10_000_000))
        assertEquals("paused stays", 10_000, snap(paused = true, pos = 10_000, at = 0).positionAt(99_999))
        assertEquals("clock going back does not rewind", 10_000, s.positionAt(0))
    }

    @Test fun repeatCycleSkipsForbiddenModes() {
        assertEquals(Repeat.CONTEXT, snap().nextRepeat())
        assertEquals(Repeat.TRACK, snap(repeat = Repeat.CONTEXT).nextRepeat())
        assertEquals(Repeat.OFF, snap(repeat = Repeat.TRACK).nextRepeat())
        assertNull("nothing allowed", snap(restrictions = false).nextRepeat())
        assertEquals(Repeat.TRACK, Repeat.of(1))
    }

    @Test fun exceptionsMapToProblems() {
        assertEquals(RemoteProblem.SPOTIFY_NOT_INSTALLED, RemoteProblem.fromClassName("CouldNotFindSpotifyApp"))
        assertEquals(RemoteProblem.OFFLINE, RemoteProblem.fromClassName("OfflineModeException"))
        assertEquals(RemoteProblem.UNKNOWN, RemoteProblem.fromClassName("Whatever"))
        assertFalse(ReconnectPolicy.shouldRetry(RemoteProblem.NOT_AUTHORIZED))
        assertEquals(2_000L, ReconnectPolicy.delayFor(0))
        assertNull(ReconnectPolicy.delayFor(99))
    }

    @Test fun fingerprintsCompareIgnoringFormat() {
        assertEquals(true, Fingerprint.matches("42:b8:98", "42B898"))
        assertEquals(false, Fingerprint.matches("42:B8:98", "42:B8:99"))
        assertNull("unknown expected value", Fingerprint.matches("42:B8", ""))
        assertEquals("42:B8:98", Fingerprint.pretty("42b898"))
    }
}
