package app.lirnyk.core.model

/** Android key codes the steering wheel may send (values from android.view.KeyEvent, kept here so this module stays JVM). */
object SteeringKeys {
    enum class Action { PLAY_PAUSE, PLAY, PAUSE, NEXT, PREVIOUS, STOP, FAST_FORWARD, REWIND }

    private val map = mapOf(
        85 to Action.PLAY_PAUSE,   // KEYCODE_MEDIA_PLAY_PAUSE
        79 to Action.PLAY_PAUSE,   // KEYCODE_HEADSETHOOK
        126 to Action.PLAY,        // KEYCODE_MEDIA_PLAY
        127 to Action.PAUSE,       // KEYCODE_MEDIA_PAUSE
        87 to Action.NEXT,         // KEYCODE_MEDIA_NEXT
        88 to Action.PREVIOUS,     // KEYCODE_MEDIA_PREVIOUS
        86 to Action.STOP,         // KEYCODE_MEDIA_STOP
        90 to Action.FAST_FORWARD, // KEYCODE_MEDIA_FAST_FORWARD
        89 to Action.REWIND,       // KEYCODE_MEDIA_REWIND
    )

    fun actionFor(keyCode: Int): Action? = map[keyCode]
    val mediaKeyCodes: Set<Int> get() = map.keys
}

/**
 * "Перевірка магнітоли": what this head unit offers. Contains no track names, file paths, account names or identifiers:
 * the owner sends it by hand to GPT and Claude.
 */
data class HeadUnitReport(
    val appVersion: String,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdk: Int,
    val totalRamMb: Long,
    val lowRamDevice: Boolean,
    val screen: Screen,
    val volumes: List<Volume>,
    val decoders: Map<String, Boolean>,
    val library: LibraryCounts,
    val keys: List<KeySeen>,
    /** Packages that offer Android's public media connection (MediaSessionService / MediaBrowserService). */
    val externalPlayers: List<String>,
    val notificationPermission: String,
    val audioReadPermission: Boolean,
    val bootReceiverAllowed: Boolean,
    val startup: Startup?,
) {
    data class Screen(val widthPx: Int, val heightPx: Int, val densityDpi: Int, val smallestWidthDp: Int, val nightMode: String)
    data class Volume(val kind: SourceKind, val state: String, val removable: Boolean, val primary: Boolean)
    data class LibraryCounts(val tracks: Int, val available: Int, val folders: Int, val pickedFolders: Int)
    data class KeySeen(val keyCode: Int, val name: String, val via: String, val action: SteeringKeys.Action?)
    data class Startup(val processToFirstFrameMs: Long?, val bootToServiceMs: Long?)

    fun toText(): String = buildString {
        appendLine("Lirnyk — head unit check · $appVersion")
        appendLine("Device: $manufacturer $model · Android $androidRelease (API $sdk)")
        appendLine("RAM: $totalRamMb MB${if (lowRamDevice) " · low-RAM device" else ""}")
        appendLine("Screen: ${screen.widthPx}×${screen.heightPx} px · ${screen.densityDpi} dpi · sw${screen.smallestWidthDp}dp · night=${screen.nightMode}")
        appendLine("Volumes:")
        if (volumes.isEmpty()) appendLine("  none reported")
        volumes.forEach { appendLine("  ${it.kind} · ${it.state}${if (it.removable) " · removable" else ""}${if (it.primary) " · primary" else ""}") }
        appendLine("Decoders: " + decoders.entries.joinToString(" · ") { "${it.key} ${if (it.value) "yes" else "NO"}" })
        appendLine("Library: ${library.tracks} tracks, ${library.available} available, ${library.folders} folders, ${library.pickedFolders} picked folders")
        appendLine("Audio permission: ${if (audioReadPermission) "granted" else "not granted"} · notifications: $notificationPermission · start after boot: ${if (bootReceiverAllowed) "enabled" else "disabled"}")
        appendLine("Keys pressed during the check:")
        if (keys.isEmpty()) appendLine("  none (press the steering-wheel buttons while this screen is open)")
        keys.forEach { appendLine("  ${it.name} (${it.keyCode}) via ${it.via}${it.action?.let { a -> " → $a" } ?: " → not a media key"}") }
        appendLine("Players with a public media connection: ${if (externalPlayers.isEmpty()) "none found" else ""}")
        externalPlayers.forEach { appendLine("  $it") }
        startup?.let { appendLine("Startup: first frame ${it.processToFirstFrameMs ?: "?"} ms after process start; service ${it.bootToServiceMs ?: "?"} ms after boot") }
    }

    fun toJson(): String {
        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        fun list(items: List<String>) = items.joinToString(",", "[", "]")
        return buildString {
            append('{')
            append("\"format\":\"lirnyk-headunit-2\",")
            append("\"appVersion\":").append(q(appVersion)).append(',')
            append("\"device\":{\"manufacturer\":").append(q(manufacturer)).append(",\"model\":").append(q(model))
            append(",\"android\":").append(q(androidRelease)).append(",\"sdk\":").append(sdk)
            append(",\"ramMb\":").append(totalRamMb).append(",\"lowRam\":").append(lowRamDevice).append("},")
            append("\"screen\":{\"width\":").append(screen.widthPx).append(",\"height\":").append(screen.heightPx)
            append(",\"dpi\":").append(screen.densityDpi).append(",\"swDp\":").append(screen.smallestWidthDp).append(",\"night\":").append(q(screen.nightMode)).append("},")
            append("\"volumes\":").append(list(volumes.map { "{\"kind\":${q(it.kind.name)},\"state\":${q(it.state)},\"removable\":${it.removable},\"primary\":${it.primary}}" })).append(',')
            append("\"decoders\":{").append(decoders.entries.joinToString(",") { "${q(it.key)}:${it.value}" }).append("},")
            append("\"library\":{\"tracks\":${library.tracks},\"available\":${library.available},\"folders\":${library.folders},\"pickedFolders\":${library.pickedFolders}},")
            append("\"permissions\":{\"audio\":$audioReadPermission,\"notifications\":${q(notificationPermission)},\"bootReceiver\":$bootReceiverAllowed},")
            append("\"keys\":").append(list(keys.map { "{\"code\":${it.keyCode},\"name\":${q(it.name)},\"via\":${q(it.via)},\"action\":${it.action?.let { a -> q(a.name) } ?: "null"}}" })).append(',')
            append("\"externalPlayers\":").append(list(externalPlayers.map(::q))).append(',')
            append("\"startup\":").append(startup?.let { "{\"firstFrameMs\":${it.processToFirstFrameMs ?: "null"},\"bootToServiceMs\":${it.bootToServiceMs ?: "null"}}" } ?: "null")
            append('}')
        }
    }
}
