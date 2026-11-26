package app.lirnyk.core.model

import java.text.Normalizer
import java.util.Locale

/** Case- and accent-insensitive matching. Ukrainian, Polish and Latin letters keep their identity apart from diacritics. */
object TextFold {
    private val marks = Regex("\\p{Mn}+")
    fun fold(s: String): String {
        val lower = s.lowercase(Locale.ROOT)
        // Polish ł has no decomposition; Ukrainian й/ї keep their letter (NFD would turn й into и + mark otherwise).
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            when (ch) {
                'й', 'ї', 'ё' -> sb.append(ch)
                'ł' -> sb.append('l')
                else -> sb.append(marks.replace(Normalizer.normalize(ch.toString(), Normalizer.Form.NFD), ""))
            }
        }
        return sb.toString()
    }

    /** Every word of [query] occurs in [text]. */
    fun matches(text: String, query: String): Boolean {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val t = fold(text)
        return words.all { it in t }
    }

    /** Letter for the alphabet index: first letter or digit, "#" for anything else. */
    fun indexLetter(s: String): String {
        val c = s.trimStart().firstOrNull { it.isLetterOrDigit() } ?: return "#"
        return if (c.isDigit()) "#" else c.uppercaseChar().toString()
    }
}

/** A browsable group: a folder, an album or an artist. */
data class Group(val key: String, val title: String, val subtitle: String?, val trackIds: List<String>)

/**
 * The music index. Tracks on a volume that is gone (stick pulled) stay in the index but are not available: playlists,
 * favourites and history keep pointing at them and work again when the stick comes back.
 */
class Library(tracks: List<Track>, val availableVolumes: Set<String>) {
    val tracks: List<Track> = tracks.sortedWith(compareBy({ TextFold.fold(it.folder) }, { it.trackNumber ?: Int.MAX_VALUE }, { TextFold.fold(it.fileName.ifEmpty { it.title }) }))
    private val byId = this.tracks.associateBy { it.id }

    operator fun get(id: String): Track? = byId[id]
    fun isAvailable(t: Track): Boolean = t.volume in availableVolumes
    fun isAvailable(id: String): Boolean = byId[id]?.let(::isAvailable) == true
    val availableTracks: List<Track> get() = tracks.filter(::isAvailable)

    fun folders(): List<Group> = availableTracks.groupBy { it.volume to it.folder }.map { (k, ts) ->
        Group("folder:${k.first}:${k.second}", ts.first().folderName.ifEmpty { "/" }, k.second.ifEmpty { null }, ts.map { it.id })
    }.sortedBy { TextFold.fold(it.title) }

    fun albums(): List<Group> = availableTracks.filter { it.displayAlbum != null }.groupBy { TextFold.fold(it.displayAlbum!!) to TextFold.fold(it.displayArtist ?: "") }
        .map { (k, ts) ->
            val sorted = ts.sortedWith(compareBy({ it.trackNumber ?: Int.MAX_VALUE }, { TextFold.fold(it.title) }))
            Group("album:${k.first}:${k.second}", sorted.first().displayAlbum!!, sorted.first().displayArtist, sorted.map { it.id })
        }.sortedBy { TextFold.fold(it.title) }

    fun artists(): List<Group> = availableTracks.filter { it.displayArtist != null }.groupBy { TextFold.fold(it.displayArtist!!) }
        .map { (k, ts) -> Group("artist:$k", ts.first().displayArtist!!, null, ts.map { it.id }) }
        .sortedBy { TextFold.fold(it.title) }

    fun search(query: String): List<Track> = availableTracks.filter {
        TextFold.matches(listOfNotNull(it.title, it.displayArtist, it.displayAlbum, it.fileName).joinToString(" "), query)
    }

    /** First position of every index letter in [titles], for the alphabet bar. */
    fun <T> alphabetIndex(items: List<T>, title: (T) -> String): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        items.forEachIndexed { i, item -> out.getOrPut(TextFold.indexLetter(title(item))) { i } }
        return out
    }

    companion object {
        val EMPTY = Library(emptyList(), emptySet())
    }
}
