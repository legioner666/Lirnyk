package app.lirnyk.core.media

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import app.lirnyk.core.model.Library
import app.lirnyk.core.model.LibraryCodec
import app.lirnyk.core.model.LibraryMerge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * The music library. Starts from the saved index (music is listed at once after the head unit boots), then rescans in the
 * background. Drives coming and going only change availability: nothing is forgotten when a stick is pulled.
 */
class LibraryRepository(private val context: Context, private val prefs: Prefs, private val scope: CoroutineScope) {
    private val cacheFile = File(context.filesDir, "library.tsv")
    private val scanMutex = Mutex()
    private var pendingScan: Job? = null

    private val _library = MutableStateFlow(Library.EMPTY)
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** True once the saved index is read (the playback service waits for it before restoring the queue). */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _volumes = MutableStateFlow<List<VolumeInfo>>(emptyList())
    val volumes: StateFlow<List<VolumeInfo>> = _volumes.asStateFlow()

    /** Picked folders that answered on the last scan. */
    private var reachableTrees: Set<String> = emptySet()

    fun hasAudioPermission(): Boolean = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

    fun start() {
        scope.launch(Dispatchers.IO) {
            val cached = runCatching { LibraryCodec.decode(cacheFile.takeIf { it.exists() }?.readText()) }.getOrDefault(emptyList())
            val vols = runCatching { Volumes.list(context) }.getOrDefault(emptyList())
            _volumes.value = vols
            // Picked folders count as available until the scan says otherwise, so a resumed track is not held back.
            reachableTrees = prefs.pickedFolders.value.mapTo(HashSet()) { FolderScanner.volumeIdOf(it) }
            _library.value = Library(cached, availableIds(vols))
            _loaded.value = true
            rescan()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED); addAction(Intent.ACTION_MEDIA_UNMOUNTED); addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED); addAction(Intent.ACTION_MEDIA_BAD_REMOVAL); addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { onVolumesChanged() }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)   // system broadcasts still arrive
    }

    /** A drive came or went: update availability at once, rescan a moment later (the system indexes first). */
    fun onVolumesChanged() {
        scope.launch(Dispatchers.IO) {
            val vols = Volumes.list(context)
            _volumes.value = vols
            _library.value = Library(_library.value.tracks, availableIds(vols))
        }
        pendingScan?.cancel()
        pendingScan = scope.launch { delay(3_000); rescan() }
    }

    fun rescan() {
        scope.launch(Dispatchers.IO) {
            scanMutex.withLock {
                _scanning.value = true
                try {
                    val vols = Volumes.list(context)
                    _volumes.value = vols
                    val indexed = if (hasAudioPermission()) runCatching { MediaStoreScanner.scan(context, vols) }.getOrDefault(emptyList()) else emptyList()
                    val trees = prefs.pickedFolders.value
                    val reachable = HashSet<String>()
                    val picked = trees.map { tree ->
                        val uri = Uri.parse(tree)
                        val r = FolderScanner.scan(context, uri)
                        if (r.reachable) reachable += FolderScanner.volumeIdOf(tree)
                        LibraryMerge.Picked(r.tracks, FolderScanner.storageVolumeOf(uri))
                    }
                    reachableTrees = reachable
                    // Tracks from drives that are gone stay in the index (kept from the previous library), marked unavailable.
                    val fresh = LibraryMerge.merge(indexed, picked)
                    val freshIds = fresh.mapTo(HashSet()) { it.id }
                    val available = availableIds(vols)
                    val kept = _library.value.tracks.filter { it.id !in freshIds && it.volume !in available }
                    val all = fresh + kept
                    _library.value = Library(all, available)
                    runCatching {
                        val tmp = File(cacheFile.path + ".tmp")
                        tmp.writeText(LibraryCodec.encode(all))
                        tmp.renameTo(cacheFile)
                    }
                } finally {
                    _scanning.value = false
                }
            }
        }
    }

    fun addPickedFolder(tree: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.setPickedFolders(prefs.pickedFolders.value + tree.toString())
        rescan()
    }

    fun removePickedFolder(tree: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.setPickedFolders(prefs.pickedFolders.value - tree)
        val volume = FolderScanner.volumeIdOf(tree)
        _library.value = Library(_library.value.tracks.filter { it.volume != volume }, _library.value.availableVolumes - volume)
        rescan()
    }

    private fun availableIds(vols: List<VolumeInfo>): Set<String> = vols.filter { it.mounted }.mapTo(HashSet()) { it.id } + reachableTrees

    companion object {
        val audioPermission: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
