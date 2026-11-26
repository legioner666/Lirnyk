package app.lirnyk.core.media

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import app.lirnyk.core.model.SourceKind
import java.io.File
import java.util.Locale

/**
 * Storage volumes as the app sees them. A volume id matches MediaStore's VOLUME_NAME: "external_primary" for the internal
 * memory, the lowercase UUID ("1a2b-3c4d") for SD cards and USB drives.
 */
data class VolumeInfo(val id: String, val kind: SourceKind, val mounted: Boolean, val removable: Boolean, val primary: Boolean, val label: String?)

object Volumes {
    const val PRIMARY = "external_primary"

    fun list(context: Context): List<VolumeInfo> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        if (Build.VERSION.SDK_INT >= 24) {
            return sm.storageVolumes.map { v ->
                val id = if (v.isPrimary) PRIMARY else (v.uuid ?: "unknown").lowercase(Locale.ROOT)
                val state = v.state
                VolumeInfo(
                    id = id,
                    kind = kindOf(v.isPrimary, v.isRemovable, v.getDescription(context)),
                    mounted = state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY,
                    removable = v.isRemovable,
                    primary = v.isPrimary,
                    label = v.getDescription(context),
                )
            }
        }
        // Android 6: derive volumes from the app's external directories ("/storage/<uuid>/Android/data/<pkg>/files").
        return context.getExternalFilesDirs(null).filterNotNull().map { dir ->
            val root = rootOf(dir)
            val primary = root?.path == Environment.getExternalStorageDirectory().path
            val removable = runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(!primary)
            val state = runCatching { Environment.getExternalStorageState(dir) }.getOrDefault(Environment.MEDIA_UNKNOWN)
            VolumeInfo(
                id = if (primary) PRIMARY else (root?.name ?: "unknown").lowercase(Locale.ROOT),
                kind = kindOf(primary, removable, null),
                mounted = state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY,
                removable = removable,
                primary = primary,
                label = null,
            )
        }
    }

    /** Volume id from a file path on Android < 10 ("/storage/1A2B-3C4D/Music/x.mp3" → "1a2b-3c4d"). */
    fun idFromPath(path: String, primaryRoot: String = Environment.getExternalStorageDirectory().path): String {
        if (path.startsWith(primaryRoot)) return PRIMARY
        val m = Regex("^/(?:storage|mnt/media_rw|mnt/usb_storage|mnt/usbhost\\d*)/([^/]+)/").find(path)
        return m?.groupValues?.get(1)?.lowercase(Locale.ROOT) ?: PRIMARY
    }

    /** Folder of a file relative to its volume root, for Android < 10. */
    fun relativeFolder(path: String, primaryRoot: String = Environment.getExternalStorageDirectory().path): String {
        val rel = if (path.startsWith(primaryRoot)) path.removePrefix(primaryRoot) else path.replaceFirst(Regex("^/(?:storage|mnt/media_rw|mnt/usb_storage|mnt/usbhost\\d*)/[^/]+"), "")
        return rel.trim('/').substringBeforeLast('/', "")
    }

    /** USB and SD cannot be told apart by any public API; the volume description is the best hint and may be missing. */
    fun kindOf(primary: Boolean, removable: Boolean, description: String?): SourceKind {
        if (primary || !removable) return SourceKind.INTERNAL
        val d = description?.lowercase(Locale.ROOT) ?: return SourceKind.REMOVABLE
        return when {
            "usb" in d -> SourceKind.USB
            "sd" in d -> SourceKind.SD_CARD
            else -> SourceKind.REMOVABLE
        }
    }

    private fun rootOf(appDir: File): File? {
        var f: File? = appDir
        while (f != null && f.name != "Android") f = f.parentFile
        return f?.parentFile
    }
}
