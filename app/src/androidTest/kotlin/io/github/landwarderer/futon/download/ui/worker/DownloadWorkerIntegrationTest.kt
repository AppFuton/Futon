package io.github.landwarderer.futon.download.ui.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import io.github.landwarderer.futon.core.prefs.DownloadFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Method

@RunWith(AndroidJUnit4::class)
class DownloadWorkerIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDownloadWorkerConstraintsPropagation() {
        val task = DownloadTask(
            mangaId = 1L,
            isPaused = false,
            isSilent = false,
            chaptersIds = longArrayOf(1L),
            destination = File(context.cacheDir, "test"),
            format = DownloadFormat.SINGLE_CBZ,
            allowMeteredNetwork = false,
            requiresCharging = true
        )

        // Using reflection because createConstraints is private
        val constraints = callCreateConstraints(task.allowMeteredNetwork, task.requiresCharging)

        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresCharging())
    }

    @Test
    fun testDownloadWorkerConstraintsPropagationMetered() {
        val task = DownloadTask(
            mangaId = 1L,
            isPaused = false,
            isSilent = false,
            chaptersIds = longArrayOf(1L),
            destination = File(context.cacheDir, "test"),
            format = DownloadFormat.SINGLE_CBZ,
            allowMeteredNetwork = true,
            requiresCharging = false
        )

        val constraints = callCreateConstraints(task.allowMeteredNetwork, task.requiresCharging)

        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertFalse(constraints.requiresCharging())
    }

    private fun callCreateConstraints(allowMetered: Boolean, requiresCharging: Boolean): androidx.work.Constraints {
        val schedulerClass = DownloadWorker.Scheduler::class.java
        val method: Method = schedulerClass.getDeclaredMethod("createConstraints", Boolean::class.java, Boolean::class.java)
        method.isAccessible = true
        
        // Find a constructor for Scheduler. It's a regular class, so we need an instance if it's not static.
        // But in Kotlin, 'class' inside 'class' is static.
        // However, 'createConstraints' is a private method, so we still need an instance if it's not a companion object method.
        // It's a method of the Scheduler class.
        
        // Since we only want to test the logic of the method, and it doesn't use 'this', 
        // we can try to invoke it on a dummy instance if needed, or if it's static in Java.
        // In Kotlin, it's a member function of Scheduler.
        
        // Let's just create a mock or a dummy instance of Scheduler.
        // Scheduler has @Inject constructor(Context, MangaDataRepository, WorkManager)
        
        val constructor = schedulerClass.declaredConstructors[0]
        constructor.isAccessible = true
        val args = arrayOfNulls<Any>(constructor.parameterCount)
        val schedulerInstance = constructor.newInstance(*args)
        
        return method.invoke(schedulerInstance, allowMetered, requiresCharging) as androidx.work.Constraints
    }
}
