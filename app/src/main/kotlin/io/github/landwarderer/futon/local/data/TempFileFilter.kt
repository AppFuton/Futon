package io.github.landwarderer.futon.local.data

import java.io.File
import java.io.FileFilter

class TempFileFilter : FileFilter {

	override fun accept(file: File): Boolean {
		return file.name.endsWith(".tmp", ignoreCase = true)
	}
}
