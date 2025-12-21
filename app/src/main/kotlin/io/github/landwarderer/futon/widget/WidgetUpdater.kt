package io.github.landwarderer.futon.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.room.InvalidationTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.db.TABLE_FAVOURITES
import io.github.landwarderer.futon.core.db.TABLE_HISTORY
import io.github.landwarderer.futon.widget.recent.RecentWidgetProvider
import io.github.landwarderer.futon.widget.shelf.ShelfWidgetProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdater @Inject constructor(
	@ApplicationContext private val context: Context,
) : InvalidationTracker.Observer(TABLE_HISTORY, TABLE_FAVOURITES) {

	override fun onInvalidated(tables: Set<String>) {
		if (TABLE_HISTORY in tables) {
			updateWidgets(RecentWidgetProvider::class.java)
		}
		if (TABLE_FAVOURITES in tables) {
			updateWidgets(ShelfWidgetProvider::class.java)
		}
	}

	private fun updateWidgets(cls: Class<*>) {
		val intent = Intent(context, cls)
		intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
		val ids = (AppWidgetManager.getInstance(context) ?: return)
			.getAppWidgetIds(ComponentName(context, cls))
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
		context.sendBroadcast(intent)
	}
}
