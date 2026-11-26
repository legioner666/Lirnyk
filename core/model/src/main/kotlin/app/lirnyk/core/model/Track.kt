package app.lirnyk.core.model

/** Where a track lives. USB and SD cannot always be told apart on head units, so [REMOVABLE] is an honest middle ground. */
enum class SourceKind { INTERNAL, SD_CARD, USB, REMOVABLE, PICKED_FOLDER }

/**
 * One audio file. [id] is stable across rescans (the content URI), so queues, favourites and the resume position survive
 * a USB stick being pulled and put back. Titles come from tags when the system indexed them, otherwise from the file name.
 */
data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    /** Folder path relative to its volume ("Music/Rock"), "" for the volume root. */
    val folder: String,
    val source: SourceKind,
    /** Volume the file is on ("external_primary", a UUID, or the picked tree); used to mark tracks unavailable. */
    val volume: String,
    val trackNumber: Int? = null,
    val fileName: String = "",
) {
    val folderName: String get() = folder.trimEnd('/').substringAfterLast('/').ifEmpty { folder }
    val displayArtist: String? get() = artist?.takeUnless { it.isBlank() || it == UNKNOWN_TAG }
    val displayAlbum: String? get() = album?.takeUnless { it.isBlank() || it == UNKNOWN_TAG }

    companion object {
        /** What Android writes into tag columns it could not read. */
        const val UNKNOWN_TAG = "<unknown>"
    }
}

/** Audio formats that Android decodes with its own codecs: no native libraries, one APK for ARM32 and ARM64. */
object AudioFormats {
    private val byExtension = mapOf(
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4", "aac" to "audio/aac", "mp4" to "audio/mp4",
        "flac" to "audio/flac",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg", "oga" to "audio/ogg",
        "opus" to "audio/opus",
    )

    /** Codec MIME types checked by the head-unit check, with the formats that need them. */
    val decoderMimes: Map<String, String> = linkedMapOf(
        "MP3" to "audio/mpeg",
        "AAC / M4A" to "audio/mp4a-latm",
        "FLAC" to "audio/flac",
        "WAV (PCM)" to "audio/raw",
        "OGG Vorbis" to "audio/vorbis",
        "Opus" to "audio/opus",
    )

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()
    fun isAudio(name: String): Boolean = extensionOf(name) in byExtension
    fun mimeOf(name: String): String? = byExtension[extensionOf(name)]

    /** "03 - Song name.mp3" → "Song name"; keeps names that are only digits. */
    fun titleFromFileName(name: String): String {
        val base = name.substringBeforeLast('.')
        val stripped = base.replace(Regex("^\\s*\\d{1,3}\\s*[-._)]\\s*"), "").trim()
        return stripped.ifEmpty { base }
    }
}
