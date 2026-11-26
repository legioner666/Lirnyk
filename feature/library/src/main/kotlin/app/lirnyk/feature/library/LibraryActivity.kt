package app.lirnyk.feature.library

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lirnyk.core.media.Lirnyk
import app.lirnyk.core.media.PlayerConnection
import app.lirnyk.core.model.Group
import app.lirnyk.core.model.Library
import app.lirnyk.core.model.Track
import app.lirnyk.core.ui.BaseActivity
import app.lirnyk.core.ui.Labels
import kotlinx.coroutines.flow.combine
import app.lirnyk.core.ui.R as UiR

/**
 * Library: folders (folders work as playlists), albums, artists, all tracks, favourites. Two levels at most; the keyboard is
 * a last resort, the alphabet bar on the right is the main way to jump.
 */
class LibraryActivity : BaseActivity() {
    private enum class Tab(val label: Int) { FOLDERS(UiR.string.lib_folders), ALBUMS(UiR.string.lib_albums), ARTISTS(UiR.string.lib_artists), TRACKS(UiR.string.lib_tracks), FAVORITES(UiR.string.lib_favorites) }

    private sealed interface Row {
        data class G(val group: Group) : Row
        data class T(val track: Track, val favorite: Boolean) : Row
    }

    private lateinit var connection: PlayerConnection
    private lateinit var list: RecyclerView
    private lateinit var index: LinearLayout
    private lateinit var empty: TextView
    private lateinit var headerTitle: TextView
    private lateinit var playAll: TextView
    private lateinit var searchRow: View
    private lateinit var search: EditText
    private lateinit var tabs: LinearLayout
    private val adapter = Adapter()

    private var tab = Tab.FOLDERS
    private var group: Group? = null
    private var query: String? = null
    private var library: Library = Library.EMPTY
    private var favorites: Set<String> = emptySet()
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.library)
        list = findViewById(R.id.list); index = findViewById(R.id.index); empty = findViewById(R.id.empty)
        headerTitle = findViewById(UiR.id.lk_header_title); playAll = findViewById(R.id.play_all)
        searchRow = findViewById(R.id.search_row); search = findViewById(R.id.search); tabs = findViewById(R.id.tabs)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.setHasFixedSize(true)

        bindBack()
        addSearchButton()
        Tab.entries.forEach { t ->
            val chip = layoutInflater.inflate(R.layout.library_tab, tabs, false) as TextView
            chip.setText(t.label)
            chip.setOnClickListener { tab = t; group = null; closeSearch(); refresh(scrollTop = true) }
            tabs.addView(chip)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) { if (query != null) { query = s.toString(); refresh(scrollTop = true) } }
        })
        findViewById<View>(R.id.search_close).setOnClickListener { closeSearch(); refresh(scrollTop = true) }
        playAll.setOnClickListener { currentTracks().takeIf { it.isNotEmpty() }?.let { play(it, null) } }
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        index.setOnTouchListener { v, ev -> onIndexTouch(v, ev) }
        savedInstanceState?.getString(STATE_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name }?.let { tab = it } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, tab.name)
    }

    override fun onStart() {
        super.onStart()
        connection = PlayerConnection(applicationContext, uiScope)
        connection.connect()
        combine(Lirnyk.library.library, Lirnyk.prefs.favorites, Lirnyk.library.scanning) { l, f, s -> Triple(l, f, s) }.collectWhileVisible { (l, f, s) ->
            library = l; favorites = f; scanning = s
            group = group?.let { g -> groupsOf(tab).firstOrNull { it.key == g.key } }
            refresh(scrollTop = false)
        }
    }

    override fun onStop() {
        connection.release()
        super.onStop()
    }

    override fun onBack() {
        when {
            query != null -> { closeSearch(); refresh(scrollTop = true) }
            group != null -> { group = null; refresh(scrollTop = true) }
            else -> finish()
        }
    }

    private fun addSearchButton() {
        val actions = findViewById<LinearLayout>(UiR.id.lk_header_actions)
        val b = layoutInflater.inflate(R.layout.library_action, actions, false) as ImageView
        b.setImageResource(UiR.drawable.ic_lk_search)
        b.contentDescription = getString(UiR.string.lib_search)
        b.setOnClickListener {
            query = ""; search.setText(""); searchRow.visibility = View.VISIBLE; search.requestFocus()
            idleReturnSuspended = true
            getSystemService(InputMethodManager::class.java)?.showSoftInput(search, 0)
            refresh(scrollTop = true)
        }
        actions.addView(b)
    }

    private fun closeSearch() {
        if (query == null) return
        query = null
        idleReturnSuspended = false
        searchRow.visibility = View.GONE
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
    }

    // ---------------- content ----------------

    private fun groupsOf(t: Tab): List<Group> = when (t) {
        Tab.FOLDERS -> library.folders()
        Tab.ALBUMS -> library.albums()
        Tab.ARTISTS -> library.artists()
        Tab.TRACKS, Tab.FAVORITES -> emptyList()
    }

    private fun currentTracks(): List<Track> {
        query?.let { q -> return if (q.isBlank()) emptyList() else library.search(q) }
        group?.let { g -> return g.trackIds.mapNotNull { library[it] }.filter(library::isAvailable) }
        return when (tab) {
            Tab.TRACKS -> library.availableTracks.sortedBy { app.lirnyk.core.model.TextFold.fold(it.title) }
            Tab.FAVORITES -> library.availableTracks.filter { it.id in favorites }
            else -> emptyList()
        }
    }

    private fun refresh(scrollTop: Boolean) {
        val showsGroups = query == null && group == null && tab in setOf(Tab.FOLDERS, Tab.ALBUMS, Tab.ARTISTS)
        val rows: List<Row> = if (showsGroups) groupsOf(tab).map { Row.G(it) } else currentTracks().map { Row.T(it, it.id in favorites) }
        adapter.submit(rows)
        if (scrollTop) list.scrollToPosition(0)

        headerTitle.text = group?.title ?: getString(UiR.string.tile_library)
        playAll.visibility = if (!showsGroups && rows.isNotEmpty()) View.VISIBLE else View.GONE
        tabs.visibility = if (group == null && query == null) View.VISIBLE else View.GONE
        for (i in 0 until tabs.childCount) {
            (tabs.getChildAt(i) as TextView).setTextColor(getColor(if (Tab.entries[i] == tab) UiR.color.lk_accent else UiR.color.lk_text))
        }
        empty.visibility = if (rows.isEmpty() && query.isNullOrBlank()) View.VISIBLE else View.GONE
        empty.setText(if (scanning && library.tracks.isEmpty()) UiR.string.lib_scanning else UiR.string.lib_empty)
        buildIndex(rows)
    }

    private var indexPositions: List<Pair<String, Int>> = emptyList()

    private fun buildIndex(rows: List<Row>) {
        val titles = rows.map { (it as? Row.G)?.group?.title ?: (it as Row.T).track.title }
        indexPositions = if (rows.size < 25) emptyList() else library.alphabetIndex(titles) { it }.toList()
        index.removeAllViews()
        index.visibility = if (indexPositions.isEmpty()) View.GONE else View.VISIBLE
        for ((letter, _) in indexPositions) {
            val tv = TextView(this)
            tv.text = letter
            tv.gravity = Gravity.CENTER
            tv.setTextColor(getColor(UiR.color.lk_text2))
            tv.textSize = 14f
            index.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun onIndexTouch(v: View, ev: MotionEvent): Boolean {
        if (indexPositions.isEmpty()) return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_MOVE) {
            val i = ((ev.y / v.height) * indexPositions.size).toInt().coerceIn(0, indexPositions.size - 1)
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(indexPositions[i].second, 0)
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP) v.performClick()   // accessibility: the bar also reacts to clicks
        return true
    }

    private fun play(tracks: List<Track>, start: Track?) {
        // Back to the player once the command is sent: the result of the choice is on the main screen.
        connection.play(tracks.map { it.id }, start?.id) { finish() }
    }

    // ---------------- list ----------------

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        private var rows: List<Row> = emptyList()

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun submit(next: List<Row>) {
            if (next == rows) return
            rows = next
            notifyDataSetChanged()
        }

        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.library_row, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            when (val r = rows[position]) {
                is Row.G -> {
                    holder.icon.setImageResource(if (tab == Tab.FOLDERS) UiR.drawable.ic_lk_folder else UiR.drawable.ic_lk_library)
                    holder.title.text = r.group.title
                    holder.subtitle.text = listOfNotNull(r.group.subtitle, getString(UiR.string.lib_tracks_count, r.group.trackIds.size)).joinToString(" · ")
                    holder.badge.visibility = View.GONE
                    holder.itemView.setOnClickListener { group = r.group; refresh(scrollTop = true) }
                    holder.itemView.setOnLongClickListener(null)
                }
                is Row.T -> {
                    holder.icon.setImageResource(UiR.drawable.ic_lk_note)
                    holder.title.text = r.track.title
                    holder.subtitle.text = listOfNotNull(r.track.displayArtist, r.track.durationMs.takeIf { it > 0 }?.let(Labels::time)).joinToString(" · ")
                        .ifEmpty { r.track.folderName }
                    holder.badge.visibility = if (r.favorite) View.VISIBLE else View.GONE
                    holder.itemView.setOnClickListener { play(currentTracks(), r.track) }
                    holder.itemView.setOnLongClickListener { Lirnyk.prefs.toggleFavorite(r.track.id); true }
                }
            }
        }
    }

    private class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val title: TextView = v.findViewById(R.id.title)
        val subtitle: TextView = v.findViewById(R.id.subtitle)
        val badge: ImageView = v.findViewById(R.id.badge)
    }

    private companion object { const val STATE_TAB = "tab" }
}
