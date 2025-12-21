package io.github.landwarderer.futon.download.ui.list

import androidx.lifecycle.LifecycleOwner
import io.github.landwarderer.futon.core.ui.BaseListAdapter
import io.github.landwarderer.futon.list.ui.adapter.ListItemType
import io.github.landwarderer.futon.list.ui.adapter.emptyStateListAD
import io.github.landwarderer.futon.list.ui.adapter.listHeaderAD
import io.github.landwarderer.futon.list.ui.adapter.loadingStateAD
import io.github.landwarderer.futon.list.ui.model.ListModel

class DownloadsAdapter(
	lifecycleOwner: LifecycleOwner,
	listener: DownloadItemListener,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.DOWNLOAD, downloadItemAD(lifecycleOwner, listener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
		addDelegate(ListItemType.HEADER, listHeaderAD(null))
	}
}
