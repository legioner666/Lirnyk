package app.lirnyk.core.model

/**
 * What is needed to continue after the head unit restarts: the queue, the track and the position, and whether music was
 * playing. Stored as a small versioned text record so a corrupt or old record is dropped instead of crashing the start.
 */
data class ResumeState(
    val queue: PlayQueue,
    val positionMs: Long,
    val wasPlaying: Boolean,
    val savedAtMillis: Long,
) {
    fun encode(): String = buildString {
        append(VERSION).append('\n')
        append(positionMs).append('\n')
        append(if (wasPlaying) 1 else 0).append('\n')
        append(savedAtMillis).append('\n')
        append(if (queue.shuffle) 1 else 0).append('\n')
        append(queue.repeat.name).append('\n')
        append(queue.position).append('\n')
        append(queue.order.joinToString(",")).append('\n')
        queue.ids.forEach { append(escape(it)).append('\n') }
    }

    companion object {
        private const val VERSION = "lirnyk-resume-1"

        /** Null for anything that is not a complete, consistent record. */
        fun decode(text: String?): ResumeState? = runCatching {
            if (text.isNullOrEmpty()) return null
            val lines = text.split('\n')
            if (lines.firstOrNull() != VERSION || lines.size < 8) return null
            val position = lines[1].toLong().coerceAtLeast(0)
            val playing = lines[2] == "1"
            val saved = lines[3].toLong()
            val shuffle = lines[4] == "1"
            val repeat = RepeatMode.valueOf(lines[5])
            val pos = lines[6].toInt()
            val order = if (lines[7].isEmpty()) emptyList() else lines[7].split(',').map { it.toInt() }
            val ids = lines.drop(8).filter { it.isNotEmpty() }.map(::unescape)
            if (order.sorted() != ids.indices.toList()) return null
            ResumeState(PlayQueue(ids, order, pos, shuffle, repeat), position, playing, saved)
        }.getOrNull()

        private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n")
        private fun unescape(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) { sb.append(if (s[i + 1] == 'n') '\n' else s[i + 1]); i += 2 } else { sb.append(c); i++ }
            }
            return sb.toString()
        }
    }
}

/** Whether to start playing by itself when the app or the head unit starts (concept: "after engine start, within 3 s"). */
object AutoResumePolicy {
    enum class Decision { RESUME, WAIT_FOR_MEDIA, PREPARE_PAUSED, NOTHING }

    /** How long a start waits for the USB stick or SD card that holds the saved track to be mounted. */
    const val WAIT_FOR_MEDIA_MILLIS = 30_000L

    /**
     * RESUME only when the user allows it, music was playing when the unit went off and the track is reachable now.
     * A track on a stick that is not mounted yet waits for it ([WAIT_FOR_MEDIA_MILLIS]); it never jumps to some other song.
     */
    fun decide(state: ResumeState?, autoResumeEnabled: Boolean, available: (String) -> Boolean): Decision {
        val id = state?.queue?.currentId ?: return Decision.NOTHING
        val wants = autoResumeEnabled && state.wasPlaying
        if (!available(id)) return if (wants) Decision.WAIT_FOR_MEDIA else Decision.PREPARE_PAUSED
        return if (wants) Decision.RESUME else Decision.PREPARE_PAUSED
    }
}

/**
 * What a start of the playback service restores (GPT audit, TASK 002 P0): the saved track exactly, or nothing. A saved track
 * that is no longer in the library is reported as missing; the queue is never moved to some other song in its place.
 */
object ResumePlanner {
    sealed interface Plan {
        data object Nothing : Plan
        /** The saved track is gone from the library (deleted, or a drive that was never indexed). Nothing plays. */
        data class MissingTrack(val id: String) : Plan
        data class Restore(val ids: List<String>, val startIndex: Int, val positionMs: Long, val decision: AutoResumePolicy.Decision) : Plan
    }

    fun plan(state: ResumeState?, autoResume: Boolean, known: (String) -> Boolean, available: (String) -> Boolean): Plan {
        val current = state?.queue?.currentId ?: return Plan.Nothing
        if (!known(current)) return Plan.MissingTrack(current)
        val ids = state.queue.orderedIds().filter(known)
        return Plan.Restore(ids, ids.indexOf(current), state.positionMs, AutoResumePolicy.decide(state, autoResume, available))
    }
}

/** Bounded skipping over files that fail to play (GPT audit P1): never loops forever, also with repeat-all. */
class FailureBudget {
    private var failures = 0
    /** A file failed. Returns true while it is still worth trying the next one. */
    fun onFailure(queueSize: Int): Boolean { failures++; return failures < queueSize.coerceAtLeast(1) }
    /** Something really played: the budget starts again. */
    fun onPlaying() { failures = 0 }
    val count: Int get() = failures
}
