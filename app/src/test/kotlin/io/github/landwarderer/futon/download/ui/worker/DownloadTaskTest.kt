package io.github.landwarderer.futon.download.ui.worker

import androidx.work.Data
import io.github.landwarderer.futon.core.prefs.DownloadFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DownloadTaskTest {

    @Test
    fun `test DownloadTask serialization and deserialization`() {
        val originalTask = DownloadTask(
            mangaId = 123L,
            isPaused = true,
            isSilent = false,
            chaptersIds = longArrayOf(1L, 2L, 3L),
            destination = File("/tmp/manga"),
            format = DownloadFormat.SINGLE_CBZ,
            allowMeteredNetwork = false,
            requiresCharging = true
        )

        val data = originalTask.toData()
        val restoredTask = DownloadTask(data)

        assertEquals(originalTask.mangaId, restoredTask.mangaId)
        assertEquals(originalTask.isPaused, restoredTask.isPaused)
        assertEquals(originalTask.isSilent, restoredTask.isSilent)
        assertEquals(originalTask.chaptersIds?.toList(), restoredTask.chaptersIds?.toList())
        assertEquals(originalTask.destination, restoredTask.destination)
        assertEquals(originalTask.format, restoredTask.format)
        assertEquals(originalTask.allowMeteredNetwork, restoredTask.allowMeteredNetwork)
        assertEquals(originalTask.requiresCharging, restoredTask.requiresCharging)
        assertEquals(originalTask, restoredTask)
    }

    @Test
    fun `test DownloadTask default values in Data constructor`() {
        val emptyData = Data.EMPTY
        val task = DownloadTask(emptyData)

        assertEquals(0L, task.mangaId)
        assertEquals(false, task.isPaused)
        assertEquals(false, task.isSilent)
        assertEquals(null, task.chaptersIds)
        assertEquals(null, task.destination)
        assertEquals(null, task.format)
        assertEquals(true, task.allowMeteredNetwork) // Default is true in constructor
        assertEquals(false, task.requiresCharging)
    }
}
