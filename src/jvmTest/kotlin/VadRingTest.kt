package dev.rizukirr.audx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VadRingTest {

    @Test
    fun emptyRingReportsSilence() {
        val ring = VadRing()
        assertEquals(0f, ring.last)
        assertFalse(ring.anyAbove(threshold = 0f, frames = 20))
    }

    @Test
    fun lastTracksNewestPush() {
        val ring = VadRing()
        ring.push(0.9f)
        assertEquals(0.9f, ring.last)
        ring.push(0.2f)
        assertEquals(0.2f, ring.last)
    }

    @Test
    fun thresholdComparisonIsStrict() {
        val ring = VadRing()
        ring.push(0.5f)
        assertFalse(ring.anyAbove(threshold = 0.5f, frames = 1))
        assertTrue(ring.anyAbove(threshold = 0.49f, frames = 1))
    }

    @Test
    fun windowExcludesFramesOlderThanRequested() {
        val ring = VadRing()
        ring.push(0.9f)
        repeat(20) { ring.push(0.1f) }
        // the 0.9 frame is now 21 frames back
        assertFalse(ring.anyAbove(threshold = 0.5f, frames = 20))
        assertTrue(ring.anyAbove(threshold = 0.5f, frames = 21))
    }

    @Test
    fun ringWrapsAtCapacity() {
        val ring = VadRing(capacity = 4)
        ring.push(0.9f)
        repeat(4) { ring.push(0.1f) } // overwrites the 0.9 slot
        assertFalse(ring.anyAbove(threshold = 0.5f, frames = 100))
        assertEquals(0.1f, ring.last)
    }

    @Test
    fun windowLargerThanRecordedClampsToRecorded() {
        val ring = VadRing()
        ring.push(0.9f)
        assertTrue(ring.anyAbove(threshold = 0.5f, frames = 100))
    }
}
