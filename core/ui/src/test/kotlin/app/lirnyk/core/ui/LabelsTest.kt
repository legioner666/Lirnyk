package app.lirnyk.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {
    @Test fun timesAreCarStereoStyle() {
        assertEquals("0:00", Labels.time(-5))
        assertEquals("3:07", Labels.time(187_400))
        assertEquals("1:02:45", Labels.time(3_765_000))
    }
}
