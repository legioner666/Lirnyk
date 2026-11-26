package app.lirnyk.core.model

/**
 * The library index on disk, so the app opens with the music already listed (no rescan on every start of the head unit).
 * Tab-separated, one track per line, versioned; a damaged line is skipped, a wrong version drops the whole file.
 */
object LibraryCodec {
    private const val HEADER = "lirnyk-library-1"

    fun encode(tracks: List<Track>): String = buildString {
        append(HEADER).append('\n')
        for (t in tracks) {
            listOf(t.id, t.uri, t.title, t.artist ?: "", t.album ?: "", t.durationMs.toString(), t.folder, t.source.name, t.volume,
                t.trackNumber?.toString() ?: "", t.fileName).joinToString("\t") { clean(it) }.let(::append)
            append('\n')
        }
    }

    fun decode(text: String?): List<Track> {
        if (text == null) return emptyList()
        val lines = text.split('\n')
        if (lines.firstOrNull() != HEADER) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val f = line.split('\t')
            if (f.size != 11) return@mapNotNull null
            runCatching {
                Track(
                    id = f[0], uri = f[1], title = f[2], artist = f[3].ifEmpty { null }, album = f[4].ifEmpty { null },
                    durationMs = f[5].toLong(), folder = f[6], source = SourceKind.valueOf(f[7]), volume = f[8],
                    trackNumber = f[9].toIntOrNull(), fileName = f[10],
                )
            }.getOrNull()?.takeIf { it.id.isNotEmpty() && it.uri.isNotEmpty() }
        }
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
