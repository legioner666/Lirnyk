package app.lirnyk.core.model

import java.util.Locale

/**
 * Joins what Android indexed with folders the user picked. A file on a picked folder that Android also indexed would be
 * listed twice; it is kept once, as the indexed track (which has tags and duration).
 */
object LibraryMerge {
    /** One picked folder's scan; [storageVolume] is the MediaStore volume id behind it when the provider tells it. */
    data class Picked(val tracks: List<Track>, val storageVolume: String?)

    fun merge(indexed: List<Track>, picked: List<Picked>): List<Track> {
        fun key(volume: String, folder: String, name: String) = "${volume.lowercase(Locale.ROOT)}|${folder.trim('/').lowercase(Locale.ROOT)}|${name.lowercase(Locale.ROOT)}"
        val known = indexed.mapTo(HashSet()) { key(it.volume, it.folder, it.fileName) }
        val out = ArrayList<Track>(indexed)
        val seen = indexed.mapTo(HashSet()) { it.id }
        for (p in picked) for (t in p.tracks) {
            if (!seen.add(t.id)) continue
            if (p.storageVolume != null && key(p.storageVolume, t.folder, t.fileName) in known) continue
            out += t
        }
        return out
    }
}
