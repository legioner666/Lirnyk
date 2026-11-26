package app.lirnyk.core.media

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import app.lirnyk.core.model.AutoResumePolicy
import app.lirnyk.core.model.HeadUnitReport
import app.lirnyk.core.model.ResumeState

/**
 * Start after the head unit boots. Only when the user allows it and music was playing when the car was switched off;
 * the playback service then decides with [AutoResumePolicy] and stops itself if there is nothing to play.
 * Android 15+ no longer lets a boot receiver start media playback, so there the music continues when Lirnyk is opened.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS || Build.VERSION.SDK_INT >= 35) return
        val prefs = Lirnyk.prefs
        if (!prefs.autoResume.value) return
        val state = ResumeState.decode(prefs.resumeRecord) ?: return
        if (!state.wasPlaying) return
        runCatching { context.startService(Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_BOOT_START)) }
    }

    private companion object {
        val BOOT_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON")
    }
}

/**
 * Exists only so Android shows Lirnyk other players' media sessions after the user grants "notification access".
 * It deliberately overrides nothing: no notification is read, stored or sent anywhere. CI fails if that changes.
 */
class SessionAccessListener : NotificationListenerService()

/** Other players visible through [SessionAccessListener] (head-unit check; the remote control comes in the next stage). */
object SessionsProbe {
    fun accessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, SessionAccessListener::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun probe(context: Context): HeadUnitReport.SessionsInfo {
        if (!accessGranted(context)) return HeadUnitReport.SessionsInfo("not granted", emptyList())
        return runCatching {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(ComponentName(context, SessionAccessListener::class.java))
            HeadUnitReport.SessionsInfo("granted", sessions.filter { it.packageName != context.packageName }.map { c ->
                val st = c.playbackState
                HeadUnitReport.Player(c.packageName, stateName(st?.state), actionNames(st?.actions ?: 0))
            })
        }.getOrElse { HeadUnitReport.SessionsInfo("error: ${it.javaClass.simpleName}", emptyList()) }
    }

    private fun stateName(s: Int?): String = when (s) {
        PlaybackState.STATE_PLAYING -> "PLAYING"; PlaybackState.STATE_PAUSED -> "PAUSED"; PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"; PlaybackState.STATE_ERROR -> "ERROR"; PlaybackState.STATE_NONE, null -> "NONE"
        else -> "OTHER($s)"
    }

    private fun actionNames(a: Long): List<String> = listOfNotNull(
        "PLAY_PAUSE".takeIf { a and PlaybackState.ACTION_PLAY_PAUSE != 0L || (a and PlaybackState.ACTION_PLAY != 0L && a and PlaybackState.ACTION_PAUSE != 0L) },
        "NEXT".takeIf { a and PlaybackState.ACTION_SKIP_TO_NEXT != 0L },
        "PREVIOUS".takeIf { a and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L },
        "SEEK".takeIf { a and PlaybackState.ACTION_SEEK_TO != 0L },
    )
}
