package app.lirnyk.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ModelTest {
    private fun t(id: String, folder: String = "Music", title: String = id, artist: String? = "A", album: String? = "Alb", volume: String = "usb1", n: Int? = null) =
        Track(id, "content://$id", title, artist, album, 1000, folder, SourceKind.USB, volume, n, "$title.mp3")

    // ---------------- queue ----------------

    @Test fun nextAndPreviousRespectRepeatModes() {
        val q = PlayQueue.of(listOf("a", "b", "c"), "c")
        assertNull("end of queue with repeat off", q.next())
        assertEquals("a", q.withRepeat(RepeatMode.ALL).next()!!.currentId)
        assertEquals("b", q.previous()!!.currentId)
        assertNull(PlayQueue.of(listOf("a", "b"), "a").previous())
        val one = q.withRepeat(RepeatMode.ONE)
        assertEquals("track end with repeat one stays", "c", one.next(userAction = false)!!.currentId)
        assertEquals("the next button still moves on", "a", one.next(userAction = true)!!.currentId)
    }

    @Test fun unavailableTracksAreSkippedNotRemoved() {
        val q = PlayQueue.of(listOf("a", "b", "c", "d"), "a")
        val n = q.next { it != "b" && it != "c" }!!
        assertEquals("d", n.currentId)
        assertEquals(4, n.ids.size)
        assertNull("nothing playable", q.next { false })
    }

    @Test fun shuffleKeepsTheCurrentTrackAndEveryTrackOnce() {
        val ids = (1..50).map { "t$it" }
        val q = PlayQueue.of(ids, "t20").withShuffle(true, Random(7))
        assertEquals("t20", q.currentId)
        assertEquals(ids.toSet(), q.orderedIds().toSet())
        assertEquals(50, q.orderedIds().size)
        val back = q.next()!!.withShuffle(false)
        assertEquals("turning shuffle off keeps the playing track", q.next()!!.currentId, back.currentId)
        assertEquals(ids, back.orderedIds())
    }

    @Test fun jumpToFindsTheTrackInPlayOrder() {
        val q = PlayQueue.of(listOf("a", "b", "c"), "a").withShuffle(true, Random(3))
        assertEquals("c", q.jumpTo("c").currentId)
        assertEquals(q, q.jumpTo("zzz"))
    }

    // ---------------- resume ----------------

    @Test fun resumeStateRoundTripsAndRejectsDamage() {
        val q = PlayQueue.of(listOf("content://x/1", "weird\nid\\with", "c"), "c").withShuffle(true, Random(1)).withRepeat(RepeatMode.ALL)
        val s = ResumeState(q, 123_456, wasPlaying = true, savedAtMillis = 99)
        assertEquals(s, ResumeState.decode(s.encode()))
        assertNull(ResumeState.decode(null))
        assertNull(ResumeState.decode("garbage"))
        assertNull("truncated record", ResumeState.decode(s.encode().lines().take(5).joinToString("\n")))
        assertNull("inconsistent order", ResumeState.decode(s.encode().replace(q.order.joinToString(","), "0,0,1")))
        assertNull("future version", ResumeState.decode(s.encode().replace("lirnyk-resume-1", "lirnyk-resume-9")))
    }

    @Test fun autoResumeNeverJumpsToAnotherSong() {
        val s = ResumeState(PlayQueue.of(listOf("a", "b"), "b"), 5_000, wasPlaying = true, savedAtMillis = 0)
        assertEquals(AutoResumePolicy.Decision.RESUME, AutoResumePolicy.decide(s, true) { true })
        assertEquals("setting off", AutoResumePolicy.Decision.PREPARE_PAUSED, AutoResumePolicy.decide(s, false) { true })
        assertEquals("was paused", AutoResumePolicy.Decision.PREPARE_PAUSED, AutoResumePolicy.decide(s.copy(wasPlaying = false), true) { true })
        assertEquals("stick not mounted yet", AutoResumePolicy.Decision.WAIT_FOR_MEDIA, AutoResumePolicy.decide(s, true) { it != "b" })
        assertEquals(AutoResumePolicy.Decision.NOTHING, AutoResumePolicy.decide(null, true) { true })
    }

    // ---------------- library ----------------

    @Test fun pulledStickKeepsTracksButHidesThem() {
        val tracks = listOf(t("1", volume = "usb1"), t("2", volume = "internal"))
        val lib = Library(tracks, setOf("internal"))
        assertEquals(2, lib.tracks.size)
        assertEquals(listOf("2"), lib.availableTracks.map { it.id })
        assertFalse(lib.isAvailable("1"))
        assertTrue(Library(tracks, setOf("internal", "usb1")).isAvailable("1"))
    }

    @Test fun groupsFoldersAlbumsArtists() {
        val lib = Library(listOf(
            t("1", "Music/Rock", album = "Ōkami", artist = "Ünal", n = 2), t("2", "Music/Rock", album = "okami", artist = "unal", n = 1),
            t("3", "Music/Pop", album = Track.UNKNOWN_TAG, artist = Track.UNKNOWN_TAG),
        ), setOf("usb1"))
        assertEquals(listOf("Pop", "Rock"), lib.folders().map { it.title })
        val albums = lib.albums()
        assertEquals("accent/case variants form one album; <unknown> is not an album", 1, albums.size)
        assertEquals("album order follows track numbers", listOf("2", "1"), albums.single().trackIds)
        assertEquals(1, lib.artists().size)
    }

    @Test fun searchIgnoresCaseAndAccentsAcrossScripts() {
        val lib = Library(listOf(
            t("1", title = "Червона рута", artist = "Софія Ротару"), t("2", title = "Łódź nocą", artist = "Kult"),
            t("3", title = "Café del Mar", artist = "Energy 52"),
        ), setOf("usb1"))
        assertEquals(listOf("1"), lib.search("червона РУТА").map { it.id })
        assertEquals(listOf("2"), lib.search("lodz").map { it.id })
        assertEquals(listOf("3"), lib.search("cafe 52").map { it.id })
        assertEquals(3, lib.search("  ").size)
        assertEquals("й stays й", "Й", TextFold.indexLetter("йода"))
        assertEquals("#", TextFold.indexLetter("2Pac"))
    }

    @Test fun alphabetIndexPointsAtFirstItemOfEachLetter() {
        val names = listOf("ABBA", "Aqua", "Beatles", "Бумбокс", "Вопли", "Воплі")
        val idx = Library.EMPTY.alphabetIndex(names) { it }
        assertEquals(mapOf("A" to 0, "B" to 2, "Б" to 3, "В" to 4), idx)
    }

    @Test fun libraryCodecRoundTripsAndSkipsDamagedLines() {
        val tracks = listOf(t("1", title = "Tab\there"), t("2", artist = null, album = null, n = 4))
        val decoded = LibraryCodec.decode(LibraryCodec.encode(tracks))
        assertEquals(2, decoded.size)
        assertEquals("Tab here", decoded[0].title)
        assertEquals(tracks[1], decoded[1])
        assertEquals("a damaged line is skipped", 1, LibraryCodec.decode(LibraryCodec.encode(tracks.take(1)) + "broken\tline\n").size)
        assertTrue(LibraryCodec.decode("other-format\n1\t2").isEmpty())
    }

    @Test fun formatsAndTitles() {
        assertTrue(AudioFormats.isAudio("a.FLAC"))
        assertFalse(AudioFormats.isAudio("cover.jpg"))
        assertEquals("Song name", AudioFormats.titleFromFileName("03 - Song name.mp3"))
        assertEquals("1999", AudioFormats.titleFromFileName("1999.mp3"))
        assertEquals("Intro", AudioFormats.titleFromFileName("01.Intro.ogg"))
    }

    // ---------------- head-unit report ----------------

    @Test fun reportHasNoTrackNamesAndValidShape() {
        val r = HeadUnitReport(
            "0.1.0 (1, abc1234)", "FYT", "UIS7862", "10", 29, 2048, false,
            HeadUnitReport.Screen(1024, 600, 160, 600, "auto"),
            listOf(HeadUnitReport.Volume(SourceKind.USB, "mounted", true, false)),
            AudioFormats.decoderMimes.keys.associateWith { true },
            HeadUnitReport.LibraryCounts(120, 100, 9, 1),
            listOf(HeadUnitReport.KeySeen(87, "KEYCODE_MEDIA_NEXT", "player", SteeringKeys.actionFor(87))),
            listOf("com.example.player"),
            "granted", true, true, HeadUnitReport.Startup(820, null),
        )
        val json = r.toJson()
        assertTrue(json.startsWith("{") && json.endsWith("}"))
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertTrue("\"action\":\"NEXT\"" in json)
        assertTrue("UIS7862" in r.toText())
        assertTrue("\"externalPlayers\":[\"com.example.player\"]" in json)
        assertEquals(SteeringKeys.Action.PLAY_PAUSE, SteeringKeys.actionFor(79))
        assertNull(SteeringKeys.actionFor(4))
    }
}

class LibraryMergeTest {
    private fun tr(id: String, volume: String, folder: String, name: String, source: SourceKind) =
        Track(id, id, name, null, null, 0, folder, source, volume, null, name)

    @Test fun filesIndexedByAndroidAreNotListedTwice() {
        val indexed = listOf(tr("ms:1", "1a2b-3c4d", "Music/Rock", "Song.mp3", SourceKind.USB))
        val picked = LibraryMerge.Picked(listOf(
            tr("tree:1", "tree:x", "Music/Rock", "song.MP3", SourceKind.PICKED_FOLDER),
            tr("tree:2", "tree:x", "Music/Rock", "Other.mp3", SourceKind.PICKED_FOLDER),
        ), storageVolume = "1A2B-3C4D")
        val merged = LibraryMerge.merge(indexed, listOf(picked, picked))
        assertEquals(listOf("ms:1", "tree:2"), merged.map { it.id })
        assertEquals("unknown provider: nothing can be matched, nothing is dropped", 3,
            LibraryMerge.merge(indexed, listOf(picked.copy(storageVolume = null))).size)
    }
}
