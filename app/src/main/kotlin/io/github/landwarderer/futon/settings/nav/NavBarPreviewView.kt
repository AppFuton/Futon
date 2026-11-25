package io.github.landwarderer.futon.settings.nav

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationBarView
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.DockStyle
import io.github.landwarderer.futon.core.prefs.NavItem
import io.github.landwarderer.futon.core.ui.widgets.SlidingBottomNavigationView
import com.google.android.material.R as materialR

private const val FAB_MARGIN_DP = 16
private const val MAX_HEIGHT_DP = 400

/**
 * A look at the navigation bar as the History tab shows it, built from the real bar, so it follows the colour scheme
 * and every setting. It only shows; touches go to whatever holds it.
 */
class NavBarPreviewView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

	/** The Classic style's floating Continue button, which sits above the bar rather than in it. */
	private val fab = ExtendedFloatingActionButton(context).apply {
		setText(R.string._continue)
		setIconResource(R.drawable.ic_read)
		isClickable = false
		isFocusable = false
		isVisible = false
	}

	private val bar = SlidingBottomNavigationView(context).apply {
		isClickable = false
		isFocusable = false
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
	}

	private var tabs: List<NavItem> = emptyList()

	init {
		orientation = VERTICAL
		val margin = (FAB_MARGIN_DP * resources.displayMetrics.density).toInt()
		addView(
			fab,
			LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
				gravity = Gravity.END
				marginEnd = margin
				bottomMargin = margin
			},
		)
		addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
	}

	fun bind(
		style: DockStyle,
		tabs: List<NavItem>,
		isCompact: Boolean,
		isLabeled: Boolean,
		isContinueEnabled: Boolean,
	) {
		if (tabs != this.tabs) {
			this.tabs = tabs
			bar.menu.clear()
			for (item in tabs.take(bar.maxItemCount)) {
				bar.menu.add(Menu.NONE, item.id, Menu.NONE, item.title).setIcon(item.icon)
			}
		}
		// As the main screen sets them.
		bar.minimumHeight = resources.getDimensionPixelSize(
			if (isLabeled) materialR.dimen.m3_bottom_nav_min_height else R.dimen.nav_bar_height_compact,
		)
		bar.labelVisibilityMode = if (isLabeled) {
			NavigationBarView.LABEL_VISIBILITY_LABELED
		} else {
			NavigationBarView.LABEL_VISIBILITY_UNLABELED
		}
		bar.dockStyle = style
		bar.isCompact = isCompact
		val isOnHistory = NavItem.HISTORY in tabs
		if (isOnHistory) {
			bar.selectedItemId = NavItem.HISTORY.id
		}
		// Continue only appears on the History tab.
		val isContinueShown = isContinueEnabled && isOnHistory
		bar.setContinueVisible(isContinueShown)
		fab.isVisible = isContinueShown && !style.isDocked
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		// A list measures its rows with no height limit, and the bar would then collapse to nothing.
		val heightSpec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
			MeasureSpec.makeMeasureSpec((MAX_HEIGHT_DP * resources.displayMetrics.density).toInt(), MeasureSpec.AT_MOST)
		} else {
			heightMeasureSpec
		}
		super.onMeasure(widthMeasureSpec, heightSpec)
	}

	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean = false
}
