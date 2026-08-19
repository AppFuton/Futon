package io.github.landwarderer.futon.ui.view

import android.widget.LinearLayout
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.ui.PaparazziRules
import org.junit.Test

class SourcesSnapshotTest: PaparazziRules() {
    @Test
    fun dialogDownload() {
        val view = paparazzi.inflate<LinearLayout>(R.layout.dialog_download)
        paparazzi.snapshot(view)
    }
}
