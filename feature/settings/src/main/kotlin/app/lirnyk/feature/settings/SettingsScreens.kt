package app.lirnyk.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.LinearLayout
import android.widget.TextView
import app.lirnyk.core.media.FolderScanner
import app.lirnyk.core.media.LibraryRepository
import app.lirnyk.core.media.Lirnyk
import app.lirnyk.core.media.ThemeMode
import app.lirnyk.core.media.VolumeInfo
import app.lirnyk.core.model.Library
import app.lirnyk.core.ui.BaseActivity
import app.lirnyk.core.ui.Form
import app.lirnyk.core.ui.Labels
import kotlinx.coroutines.flow.combine
import app.lirnyk.core.ui.R as UiR

/** Where the music comes from: audio permission, drives, picked folders (for USB ports the system does not index). */
class SourcesActivity : BaseActivity() {
    private lateinit var form: Form
    private var noPicker = false
    private var last: Quad? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(UiR.layout.lk_form_screen)
        findViewById<TextView>(UiR.id.lk_header_title).setText(UiR.string.src_title)
        bindBack()
        form = Form(this, findViewById<LinearLayout>(UiR.id.lk_form))
    }

    override fun onStart() {
        super.onStart()
        val repo = Lirnyk.library
        combine(repo.library, repo.volumes, repo.scanning, Lirnyk.prefs.pickedFolders) { l, v, s, f -> Quad(l, v, s, f) }
            .collectWhileVisible { last = it; render(it.a, it.b, it.c, it.d) }
    }

    private data class Quad(val a: Library, val b: List<VolumeInfo>, val c: Boolean, val d: Set<String>)

    private fun render(library: Library, volumes: List<VolumeInfo>, scanning: Boolean, folders: Set<String>) {
        form.clear()
        val repo = Lirnyk.library
        if (!repo.hasAudioPermission()) {
            form.section(getString(UiR.string.src_permission))
            form.body(getString(UiR.string.src_permission_body))
            form.button(getString(UiR.string.src_permission)) { requestPermissions(arrayOf(LibraryRepository.audioPermission), REQ_AUDIO) }
        }

        form.section(getString(UiR.string.src_volumes))
        val counts = library.tracks.groupingBy { it.volume }.eachCount()
        val availableCounts = library.availableTracks.groupingBy { it.volume }.eachCount()
        for (v in volumes) {
            val state = getString(if (v.mounted) UiR.string.src_mounted else UiR.string.src_unmounted)
            form.body("${Labels.source(this, v.kind)}${v.label?.let { " ($it)" } ?: ""} · $state · " +
                getString(UiR.string.src_tracks, counts[v.id] ?: 0, availableCounts[v.id] ?: 0), secondary = false)
        }

        form.section(getString(UiR.string.src_folders))
        if (folders.isEmpty()) form.body(getString(UiR.string.src_no_folders))
        for (tree in folders.sorted()) {
            val vol = FolderScanner.volumeIdOf(tree)
            form.rowWithAction(folderLabel(tree), getString(UiR.string.src_tracks, counts[vol] ?: 0, availableCounts[vol] ?: 0), getString(UiR.string.src_remove)) {
                repo.removePickedFolder(tree)
            }
        }
        form.body(getString(UiR.string.src_add_folder_body))
        form.button(getString(UiR.string.src_add_folder), UiR.drawable.ic_lk_folder) { pickFolder() }
        if (noPicker) form.body(getString(UiR.string.src_no_picker))
        form.button(getString(if (scanning) UiR.string.lib_scanning else UiR.string.src_rescan), UiR.drawable.ic_lk_refresh) { repo.rescan() }

        form.section(getString(UiR.string.src_other_players))
        form.body(getString(UiR.string.src_other_players_body))
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_TREE)
        } catch (_: ActivityNotFoundException) {
            noPicker = true
            last?.let { render(it.a, it.b, it.c, it.d) }   // shows the explanation
        }
    }

    @Deprecated("Platform API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_TREE && resultCode == RESULT_OK) data?.data?.let { Lirnyk.library.addPickedFolder(it) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) Lirnyk.library.rescan()
    }

    private fun folderLabel(tree: String): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(tree)) }.getOrNull() ?: return tree
        val path = id.substringAfter(':', id)
        return path.ifEmpty { id.substringBefore(':') }.let { if (it == "primary") "/" else it }
    }

    private companion object {
        const val REQ_TREE = 10
        const val REQ_AUDIO = 11
    }
}

/** Behaviour settings: auto-resume, theme, screen, notifications, about. */
class SettingsActivity : BaseActivity() {
    private lateinit var form: Form

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(UiR.layout.lk_form_screen)
        findViewById<TextView>(UiR.id.lk_header_title).setText(UiR.string.set_title)
        bindBack()
        form = Form(this, findViewById<LinearLayout>(UiR.id.lk_form))
    }

    override fun onStart() {
        super.onStart()
        render()
    }

    private fun render() {
        val prefs = Lirnyk.prefs
        form.clear()
        form.toggle(getString(UiR.string.set_autoresume), prefs.autoResume.value) { prefs.setAutoResume(it) }
        form.body(getString(UiR.string.set_autoresume_body))

        form.section(getString(UiR.string.set_theme))
        form.choice(listOf(ThemeMode.AUTO to getString(UiR.string.set_theme_auto), ThemeMode.NIGHT to getString(UiR.string.set_theme_night), ThemeMode.DAY to getString(UiR.string.set_theme_day)), prefs.theme.value) {
            if (it != prefs.theme.value) { prefs.setTheme(it); recreate() }
        }
        form.toggle(getString(UiR.string.set_keep_screen), prefs.keepScreenOn.value) { prefs.setKeepScreenOn(it) }

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            form.button(getString(UiR.string.set_notifications)) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20) }
        }

        form.section(getString(UiR.string.set_about))
        form.body(getString(UiR.string.set_about_body, Lirnyk.versionLabel))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }
}
