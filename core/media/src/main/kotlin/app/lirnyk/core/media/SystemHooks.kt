package app.lirnyk.core.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.lirnyk.core.model.AutoResumePolicy
import app.lirnyk.core.model.ResumeState

/**
 * Start after the head unit boots. Only when the user allows it and music was playing when the car was switched off;
 * the playback service then decides with [AutoResumePolicy] and stops itself if there is nothing to play.
 * Android 15+ no longer lets a boot receiver start media playback, so there the music continues when Lirnyk is opened.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        if (Build.VERSION.SDK_INT >= 35) { BootLog.note("boot: Android 15+ forbids playback start from boot; continues when Lirnyk is opened"); return }
        val prefs = Lirnyk.prefs
        if (!prefs.autoResume.value) { BootLog.note("boot: auto-resume off"); return }
        val state = ResumeState.decode(prefs.resumeRecord)
        if (state == null || !state.wasPlaying) { BootLog.note("boot: music was not playing"); return }
        runCatching { context.startService(Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_BOOT_START)) }
            .onSuccess { BootLog.note("boot: playback service requested") }
            .onFailure { BootLog.note("boot: service start refused (${it.javaClass.simpleName})") }
    }

    private companion object {
        val BOOT_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON")
    }
}

/**
 * Installed players that offer Android's public media connection (the same one Android Auto-style hosts use). Lirnyk has
 * no notification access (Play Protect blocks sideloaded apps that declare it), so this is the official route to other
 * players; whether each app accepts a connection from Lirnyk is that app's decision and is checked in a later stage.
 */
object ExternalPlayersProbe {
    private val ACTIONS = listOf(
        "androidx.media3.session.MediaSessionService",
        "androidx.media3.session.MediaLibraryService",
        "android.media.browse.MediaBrowserService",
    )

    fun list(context: Context): List<String> = ACTIONS.flatMap { action ->
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(Intent(action), 0).map { it.serviceInfo.packageName }
        }.getOrDefault(emptyList())
    }.filter { it != context.packageName }.distinct().sorted()
}
