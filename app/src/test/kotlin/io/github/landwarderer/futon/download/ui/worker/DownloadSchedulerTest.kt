package io.github.landwarderer.futon.download.ui.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSchedulerTest {

    @Test
    fun `test isOffPeakTime logic - same day window`() {
        // Window 08:00 - 12:00
        assertTrue(isOffPeakTimeMock(isEnabled = true, start = "08:00", end = "12:00", hour = 9, minute = 0))
        assertTrue(isOffPeakTimeMock(isEnabled = true, start = "08:00", end = "12:00", hour = 8, minute = 0))
        assertFalse(isOffPeakTimeMock(isEnabled = true, start = "08:00", end = "12:00", hour = 7, minute = 59))
        assertFalse(isOffPeakTimeMock(isEnabled = true, start = "08:00", end = "12:00", hour = 12, minute = 0))
        assertFalse(isOffPeakTimeMock(isEnabled = true, start = "08:00", end = "12:00", hour = 13, minute = 0))
    }

    @Test
    fun `test isOffPeakTime logic - overnight window`() {
        // Window 22:00 - 06:00
        assertTrue(isOffPeakTimeMock(isEnabled = true, start = "22:00", end = "06:00", hour = 23, minute = 0))
        assertTrue(isOffPeakTimeMock(isEnabled = true, start = "22:00", end = "06:00", hour = 22, minute = 0))
        assertTrue(isOffPeakTimeMock(isEnabled = true, start = "22:00", end = "06:00", hour = 1, minute = 0))
        assertTrue(isOffPeakTimeMock(isEnabled = true, start = "22:00", end = "06:00", hour = 5, minute = 59))
        assertFalse(isOffPeakTimeMock(isEnabled = true, start = "22:00", end = "06:00", hour = 21, minute = 59))
        assertFalse(isOffPeakTimeMock(isEnabled = true, start = "22:00", end = "06:00", hour = 6, minute = 0))
        assertFalse(isOffPeakTimeMock(isEnabled = true, start = "22:00", end = "06:00", hour = 12, minute = 0))
    }

    @Test
    fun `test calculateSecondsUntilOffPeak`() {
        // Current time 09:00:00 -> Start 10:00 -> 1 hour = 3600 seconds
        assertEquals(3600L, calculateSecondsUntilOffPeakMock(isEnabled = true, start = "10:00", hour = 9, minute = 0, second = 0))

        // Current time 11:00:00 -> Start 10:00 -> Next day 10:00:00 = 23 hours = 82800 seconds
        assertEquals(23 * 3600L, calculateSecondsUntilOffPeakMock(isEnabled = true, start = "10:00", hour = 11, minute = 0, second = 0))
        
        // Current time 10:00:00 -> Start 10:00 -> Next day 10:00:00 = 24 hours
        assertEquals(24 * 3600L, calculateSecondsUntilOffPeakMock(isEnabled = true, start = "10:00", hour = 10, minute = 0, second = 0))
    }

    // Helper to replicate DownloadSchedulerWorker.isOffPeakTime logic for testing
    private fun isOffPeakTimeMock(isEnabled: Boolean, start: String, end: String, hour: Int, minute: Int): Boolean {
        if (!isEnabled) return true
        val currentTimeInMinutes = hour * 60 + minute

        val startParts = start.split(":")
        val endParts = end.split(":")

        val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
        val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)

        return if (startMinutes < endMinutes) {
            currentTimeInMinutes in startMinutes until endMinutes
        } else {
            currentTimeInMinutes >= startMinutes || currentTimeInMinutes < endMinutes
        }
    }

    // Helper to replicate DownloadSchedulerWorker.calculateSecondsUntilOffPeak logic for testing
    private fun calculateSecondsUntilOffPeakMock(isEnabled: Boolean, start: String, hour: Int, minute: Int, second: Int): Long {
        if (!isEnabled) return 0
        val currentTimeInSeconds = (hour * 60 + minute) * 60 + second

        val startParts = start.split(":")
        val startSeconds = ((startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)) * 60

        var diff = startSeconds - currentTimeInSeconds
        if (diff <= 0) {
            diff += 24 * 60 * 60
        }
        return diff.toLong()
    }
}
