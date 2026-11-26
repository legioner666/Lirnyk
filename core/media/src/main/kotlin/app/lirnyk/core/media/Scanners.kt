package app.lirnyk.core.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import app.lirnyk.core.model.AudioFormats
import app.lirnyk.core.model.SourceKind
import app.lirnyk.core.model.Track
import java.util.Locale

/** Music Android has indexed: internal memory, and SD cards / USB drives where the head unit's system indexes them. */
object MediaStoreScanner {
    @Suppress("DEPRECATION")
    fun scan(context: Context, volumes: List<VolumeInfo>): List<Track> {
        val kinds = volumes.associate { it.id to it.kind }
        val q = Build.VERSION.SDK_INT >= 29
        val cols = buildList {
            add(MediaStore.Audio.Media._ID); add(MediaStore.Audio.Media.TITLE); add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM); add(MediaStore.Audio.Media.DURATION); add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.TRACK); add(MediaStore.Audio.Media.DATA)
            if (q) { add(MediaStore.Audio.Media.RELATIVE_PATH); add(MediaStore.Audio.Media.VOLUME_NAME) }
        }.toTypedArray()
        val out = ArrayList<Track>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(base, cols, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val name = c.str(MediaStore.Audio.Media.DISPLAY_NAME) ?: continue
                val data = c.str(MediaStore.Audio.Media.DATA)
                val volume = if (q) c.str(MediaStore.Audio.Media.VOLUME_NAME)?.lowercase(Locale.ROOT) ?: Volumes.PRIMARY
                    else data?.let(Volumes::idFromPath) ?: Volumes.PRIMARY
                val folder = if (q) c.str(MediaStore.Audio.Media.RELATIVE_PATH)?.trim('/') ?: ""
                    else data?.let(Volumes::relativeFolder) ?: ""
                val uri = ContentUris.withAppendedId(base, id).toString()
                val rawTrack = c.int(MediaStore.Audio.Media.TRACK)
                out += Track(
                    id = uri, uri = uri,
                    title = c.str(MediaStore.Audio.Media.TITLE)?.takeIf { it.isNotBlank() } ?: AudioFormats.titleFromFileName(name),
                    artist = c.str(MediaStore.Audio.Media.ARTIST), album = c.str(MediaStore.Audio.Media.ALBUM),
                    durationMs = c.long(MediaStore.Audio.Media.DURATION) ?: 0L, folder = folder,
                    source = kinds[volume] ?: if (volume == Volumes.PRIMARY) SourceKind.INTERNAL else SourceKind.REMOVABLE,
                    volume = volume, trackNumber = rawTrack?.takeIf { it > 0 }?.rem(1000), fileName = name,
                )
            }
        }
        return out
    }

    private fun Cursor.str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(col: String): Long? = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.int(col: String): Int? = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getInt(it) }
}

/**
 * Folders the user picked with the system folder picker (Storage Access Framework). Needed because many head units never
 * index their USB ports. Reads names only: tags and duration come from the player when a track plays.
 */
object FolderScanner {
    const val MAX_FILES = 20_000
    const val MAX_DEPTH = 12
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    data class Result(val tracks: List<Track>, val reachable: Boolean)

    fun volumeIdOf(treeUri: String) = "tree:$treeUri"

    fun scan(context: Context, treeUri: Uri): Result {
        val rootDoc = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return Result(emptyList(), false)
        val volume = volumeIdOf(treeUri.toString())
        // For the system storage provider the document id is "<UUID>:<path>", which gives the real folder for de-duplication.
        val storagePrefix = if (treeUri.authority == EXTERNAL_STORAGE_AUTHORITY) rootDoc.substringBefore(':') + ":" else null
        val out = ArrayList<Track>()
        var reachable = false
        fun walk(docId: String, rel: String, depth: Int) {
            if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
            val c = runCatching { context.contentResolver.query(children, cols, null, null, null) }.getOrNull() ?: return
            reachable = true
            val dirs = ArrayList<Pair<String, String>>()
            c.use {
                while (it.moveToNext() && out.size < MAX_FILES) {
                    val id = it.getString(0) ?: continue
                    val name = it.getString(1) ?: continue
                    val mime = it.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!name.startsWith(".")) dirs += id to (if (rel.isEmpty()) name else "$rel/$name")
                    } else if (AudioFormats.isAudio(name) || mime?.startsWith("audio/") == true) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString()
                        val folder = if (storagePrefix != null && id.startsWith(storagePrefix)) id.removePrefix(storagePrefix).substringBeforeLast('/', "") else rel
                        out += Track(uri, uri, AudioFormats.titleFromFileName(name), null, null, 0, folder, SourceKind.PICKED_FOLDER, volume, null, name)
                    }
                }
            }
            for ((id, path) in dirs.sortedBy { it.second.lowercase(Locale.ROOT) }) walk(id, path, depth + 1)
        }
        walk(rootDoc, "", 0)
        return Result(out, reachable)
    }

    /** Volume UUID behind a picked folder of the system storage provider ("primary" for the internal memory), if known. */
    fun storageVolumeOf(treeUri: Uri): String? {
        if (treeUri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val root = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val v = root.substringBefore(':')
        return if (v == "primary") Volumes.PRIMARY else v.lowercase(Locale.ROOT)
    }
}
