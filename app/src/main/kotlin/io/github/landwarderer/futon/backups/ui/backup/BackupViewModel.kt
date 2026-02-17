package io.github.landwarderer.futon.backups.ui.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.landwarderer.futon.backups.data.BackupRepository
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.require
import io.github.landwarderer.futon.core.util.progress.Progress
import java.util.zip.Deflater
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val repository: BackupRepository,
	@ApplicationContext context: Context,
) : BaseViewModel() {

	val progress = MutableStateFlow(Progress.INDETERMINATE)
	val onBackupDone = MutableEventFlow<Uri>()

	private val destination = savedStateHandle.require<Uri>(AppRouter.KEY_DATA)
	private val contentResolver: ContentResolver = context.contentResolver

	init {
		launchLoadingJob(Dispatchers.IO) {
			ZipOutputStream(checkNotNull(contentResolver.openOutputStream(destination))).use {
				it.setLevel(Deflater.BEST_COMPRESSION)
				repository.createBackup(it, progress)
			}
			onBackupDone.call(destination)
		}
	}
}
