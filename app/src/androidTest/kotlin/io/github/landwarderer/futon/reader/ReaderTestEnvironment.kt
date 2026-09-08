package io.github.landwarderer.futon.reader

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager

/** HiltTestApplication does not implement the production application's WorkManager configuration. */
internal fun initializeReaderTestWorkManager(context: Context) {
    try {
        WorkManager.getInstance(context)
    } catch (_: IllegalStateException) {
        WorkManager.initialize(context, Configuration.Builder().build())
    }
}
