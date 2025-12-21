package io.github.landwarderer.futon.explore.ui.adapter

import android.view.View
import io.github.landwarderer.futon.list.ui.adapter.ListHeaderClickListener
import io.github.landwarderer.futon.list.ui.adapter.ListStateHolderListener

interface ExploreListEventListener : ListStateHolderListener, View.OnClickListener, ListHeaderClickListener
