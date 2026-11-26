package app.lirnyk.core.media

import app.lirnyk.core.model.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumesTest {
    private val primary = "/storage/emulated/0"

    @Test fun volumeIdsMatchMediaStoreNames() {
        assertEquals(Volumes.PRIMARY, Volumes.idFromPath("/storage/emulated/0/Music/a.mp3", primary))
        assertEquals("1a2b-3c4d", Volumes.idFromPath("/storage/1A2B-3C4D/Music/a.mp3", primary))
        assertEquals("udisk", Volumes.idFromPath("/mnt/usb_storage/UDISK/a.mp3", primary))
        assertEquals("usb", Volumes.idFromPath("/mnt/usbhost1/USB/a.mp3", primary))
    }

    @Test fun foldersAreRelativeToTheVolume() {
        assertEquals("Music/Rock", Volumes.relativeFolder("/storage/emulated/0/Music/Rock/a.mp3", primary))
        assertEquals("Albums", Volumes.relativeFolder("/storage/1A2B-3C4D/Albums/a.mp3", primary))
        assertEquals("", Volumes.relativeFolder("/storage/1A2B-3C4D/a.mp3", primary))
    }

    @Test fun usbAndSdAreOnlyNamedWhenTheSystemSaysSo() {
        assertEquals(SourceKind.INTERNAL, Volumes.kindOf(primary = true, removable = false, description = "Internal"))
        assertEquals(SourceKind.USB, Volumes.kindOf(false, true, "USB drive"))
        assertEquals(SourceKind.SD_CARD, Volumes.kindOf(false, true, "SanDisk SD card"))
        assertEquals("no description: no guess", SourceKind.REMOVABLE, Volumes.kindOf(false, true, null))
        assertEquals(SourceKind.REMOVABLE, Volumes.kindOf(false, true, "Generic"))
    }
}
