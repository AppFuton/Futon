package io.github.landwarderer.futon.main.ui

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.core.view.size
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.transition.MaterialFadeThrough
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.bookmarks.ui.AllBookmarksFragment
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.NavItem
import io.github.landwarderer.futon.core.ui.components.BottomNavBarState
import io.github.landwarderer.futon.core.ui.util.RecyclerViewOwner
import io.github.landwarderer.futon.core.util.ext.buildBundle
import io.github.landwarderer.futon.core.util.ext.setContentDescriptionAndTooltip
import io.github.landwarderer.futon.core.util.ext.smoothScrollToTop
import io.github.landwarderer.futon.databinding.NavigationRailFabBinding
import io.github.landwarderer.futon.explore.ui.ExploreFragment
import io.github.landwarderer.futon.favourites.ui.container.FavouritesContainerFragment
import io.github.landwarderer.futon.history.ui.HistoryListFragment
import io.github.landwarderer.futon.local.ui.LocalListFragment
import io.github.landwarderer.futon.suggestions.ui.SuggestionsFragment
import io.github.landwarderer.futon.tracker.ui.feed.FeedFragment
import io.github.landwarderer.futon.tracker.ui.updates.UpdatesFragment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.LinkedList

private const val TAG_PRIMARY = "primary"

/**
 * Drives main navigation — fragment switching and (for wide layouts) the [NavigationRailView].
 *
 * For phone layouts the bottom bar is now a Compose [BottomNavBar]; pass [composeNavState] and
 * leave [navBar] as null.  For wide layouts pass [navBar] (the rail) as before.
 *
 * Exactly one of [navBar] / [composeNavState] must be non-null.
 */
class MainNavigationDelegate(
	private val navBar: NavigationBarView?,
	private val fragmentManager: FragmentManager,
	private val settings: AppSettings,
	private val composeNavState: BottomNavBarState? = null,
	private val context: android.content.Context,
) : OnBackPressedCallback(false),
	NavigationBarView.OnItemSelectedListener,
	NavigationBarView.OnItemReselectedListener,
	View.OnClickListener {

	init {
		require((navBar != null) xor (composeNavState != null)) {
			"Exactly one of navBar or composeNavState must be non-null"
		}
	}

	private val listeners = LinkedList<OnFragmentChangedListener>()
	val navRailHeader = (navBar as? NavigationRailView)?.headerView?.let {
		NavigationRailFabBinding.bind(it)
	}

	val primaryFragment: Fragment?
		get() = fragmentManager.findFragmentByTag(TAG_PRIMARY)

	init {
		navBar?.setOnItemSelectedListener(this)
		navBar?.setOnItemReselectedListener(this)
		navRailHeader?.run {
			root.updateLayoutParams<FrameLayout.LayoutParams> {
				gravity = Gravity.TOP or Gravity.CENTER
			}
			val horizontalPadding = (navBar as NavigationRailView).itemActiveIndicatorMarginHorizontal
			root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			buttonExpand.setOnClickListener(this@MainNavigationDelegate)
			buttonExpand.setContentDescriptionAndTooltip(R.string.expand)
			railFab.isExtended = false
			railFab.isAnimationEnabled = false
		}
	}

	// ---------------------------------------------------------------------------
	// NavigationBarView.OnItemSelectedListener (rail only)
	// ---------------------------------------------------------------------------

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		return if (onNavigationItemSelected(item.itemId)) {
			item.isChecked = true
			true
		} else {
			false
		}
	}

	override fun onNavigationItemReselected(item: MenuItem) {
		onNavigationItemReselected()
	}

	// ---------------------------------------------------------------------------
	// Compose nav bar callback — called from MainActivity when an item is tapped
	// ---------------------------------------------------------------------------

	/**
	 * Handle a tap on a Compose [BottomNavBar] item.
	 * Activity-launcher items (e.g. Settings) are handled here too.
	 */
	fun onComposeItemClick(item: NavItem, openSettings: () -> Unit) {
		if (item.isActivityLauncher) {
			// e.g. SETTINGS — open the corresponding activity, do not change fragment selection
			openSettings()
			return
		}
		onNavigationItemSelected(item.id)
	}

	// ---------------------------------------------------------------------------
	// View.OnClickListener (nav rail expand button)
	// ---------------------------------------------------------------------------

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_expand -> {
				if (navBar is NavigationRailView) {
					setNavbarIsExpanded(!navBar.isExpanded)
				}
			}
		}
	}

	override fun handleOnBackPressed() {
		val firstId = firstItemId() ?: return
		navBar?.selectedItemId = firstId
		composeNavState?.let { state ->
			onNavigationItemSelected(firstId)
			state.selectedItemId = firstId
		}
	}

	// ---------------------------------------------------------------------------
	// Lifecycle
	// ---------------------------------------------------------------------------

	fun onCreate(lifecycleOwner: LifecycleOwner, savedInstanceState: Bundle?) {
		if (navBar != null && navBar.menu.isEmpty()) {
			// Wide layout: populate the rail menu from settings (excluding activity-launchers)
			createMenu(settings.mainNavItems.filter { !it.isActivityLauncher }, navBar.menu)
		}
		observeSettings(lifecycleOwner)
		val fragment = primaryFragment
		if (fragment != null) {
			onFragmentChanged(fragment, fromUser = false)
			val itemId = getItemId(fragment)
			navBar?.let { bar ->
				if (bar.selectedItemId != itemId) bar.selectedItemId = itemId
			}
			composeNavState?.let { state ->
				if (state.selectedItemId != itemId) state.selectedItemId = itemId
			}
		} else {
			val itemId = if (savedInstanceState == null) {
				firstItemId() ?: (navBar?.selectedItemId ?: composeNavState?.selectedItemId ?: 0)
			} else {
				navBar?.selectedItemId ?: composeNavState?.selectedItemId ?: 0
			}
			onNavigationItemSelected(itemId)
			composeNavState?.selectedItemId = itemId
		}
	}

	fun observeTitle() = callbackFlow {
		val listener = OnFragmentChangedListener { f, _ ->
			trySendBlocking(getItemId(f))
		}
		addOnFragmentChangedListener(listener)
		awaitClose { removeOnFragmentChangedListener(listener) }
	}.map { id ->
		// Rail: look up title from menu; Compose: look up from NavItem list
		navBar?.menu?.findItem(id)?.title
			?: settings.mainNavItems.firstOrNull { it.id == id }
				?.let { navBar?.context?.getString(it.title) }
	}

	fun setCounter(item: NavItem, counter: Int) {
		setCounter(item.id, counter)
	}

	fun syncSelectedItem() {
		val fragment = primaryFragment ?: return
		onFragmentChanged(fragment, fromUser = false)
		val itemId = getItemId(fragment)
		navBar?.let { if (it.selectedItemId != itemId) it.selectedItemId = itemId }
		composeNavState?.let { if (it.selectedItemId != itemId) it.selectedItemId = itemId }
	}

	private fun setCounter(@IdRes id: Int, counter: Int) {
		// Rail path
		if (navBar != null) {
			if (counter == 0) {
				navBar.getBadge(id)?.isVisible = false
			} else {
				val badge = navBar.getOrCreateBadge(id)
				if (counter < 0) badge.clearNumber() else badge.number = counter
				badge.isVisible = true
			}
		}
		// Compose path
		composeNavState?.setBadge(id, counter)
	}

	fun setItemVisibility(@IdRes itemId: Int, isVisible: Boolean) {
		val item = navBar?.menu?.findItem(itemId) ?: return
		item.isVisible = isVisible
		if (item.isChecked && !isVisible) {
			navBar?.selectedItemId = firstItemId() ?: return
		}
	}

	fun addOnFragmentChangedListener(listener: OnFragmentChangedListener) {
		listeners.add(listener)
	}

	fun removeOnFragmentChangedListener(listener: OnFragmentChangedListener) {
		listeners.remove(listener)
	}

	// ---------------------------------------------------------------------------
	// Internal navigation routing
	// ---------------------------------------------------------------------------

	private fun onNavigationItemSelected(@IdRes itemId: Int): Boolean {
		if (itemId == R.id.nav_settings) return false // handled by onComposeItemClick

		val newFragment = when (itemId) {
			R.id.nav_history -> HistoryListFragment::class.java
			R.id.nav_favorites -> FavouritesContainerFragment::class.java
			R.id.nav_explore -> ExploreFragment::class.java
			R.id.nav_feed -> FeedFragment::class.java
			R.id.nav_local -> LocalListFragment::class.java
			R.id.nav_suggestions -> SuggestionsFragment::class.java
			R.id.nav_bookmarks -> AllBookmarksFragment::class.java
			R.id.nav_updated -> UpdatesFragment::class.java
			else -> return false
		}
		if (!setPrimaryFragment(newFragment)) {
			onNavigationItemReselected()
		}
		return true
	}

	private fun getItemId(fragment: Fragment) = when (fragment) {
		is HistoryListFragment -> R.id.nav_history
		is FavouritesContainerFragment -> R.id.nav_favorites
		is ExploreFragment -> R.id.nav_explore
		is FeedFragment -> R.id.nav_feed
		is LocalListFragment -> R.id.nav_local
		is SuggestionsFragment -> R.id.nav_suggestions
		is AllBookmarksFragment -> R.id.nav_bookmarks
		is UpdatesFragment -> R.id.nav_updated
		else -> 0
	}

	private fun setPrimaryFragment(fragmentClass: Class<out Fragment>): Boolean {
		if (fragmentManager.isStateSaved || fragmentClass.isInstance(primaryFragment)) {
			return false
		}
		val fragment = instantiateFragment(fragmentClass)
		val args = buildBundle(1) {
			putBoolean(AppRouter.KEY_IS_BOTTOMTAB, true)
		}
		fragment.enterTransition = MaterialFadeThrough()
		fragmentManager.beginTransaction()
			.setReorderingAllowed(true)
			.replace(R.id.container, fragmentClass, args, TAG_PRIMARY)
			.runOnCommit { onFragmentChanged(fragment, fromUser = true) }
			.commit()
		return true
	}

	private fun onNavigationItemReselected() {
		val recyclerView = (primaryFragment as? RecyclerViewOwner)?.recyclerView ?: return
		recyclerView.smoothScrollToTop()
	}

	private fun onFragmentChanged(fragment: Fragment, fromUser: Boolean) {
		isEnabled = getItemId(fragment) != firstItemId()
		listeners.forEach { it.onFragmentChanged(fragment, fromUser) }
	}

	private fun createMenu(items: List<NavItem>, menu: Menu) {
		val bar = navBar ?: return
		for (item in items) {
			menu.add(Menu.NONE, item.id, Menu.NONE, item.title)
				.setIcon(item.icon)
			if (menu.size >= bar.maxItemCount) break
		}
	}

	private fun instantiateFragment(fragmentClass: Class<out Fragment>): Fragment {
		val ctx = navBar?.context ?: context
		return fragmentManager.fragmentFactory.instantiate(ctx.classLoader, fragmentClass.name)
	}

	private fun observeSettings(lifecycleOwner: LifecycleOwner) {
		settings.observe(AppSettings.KEY_TRACKER_ENABLED, AppSettings.KEY_SUGGESTIONS, AppSettings.KEY_NAV_LABELS)
			.onEach {
				// Rail only: hide items that are disabled by settings
				setItemVisibility(R.id.nav_suggestions, settings.isSuggestionsEnabled)
				setItemVisibility(R.id.nav_feed, settings.isTrackerEnabled)
				setNavbarIsLabeled(settings.isNavLabelsVisible)
				// Compose path: update the items list so disabled items disappear
				composeNavState?.navItems = settings.mainNavItems.filter { it.isAvailable(settings) }
			}.launchIn(lifecycleOwner.lifecycleScope)
	}

	private fun firstItemId(): Int? {
		navBar?.menu?.let { menu ->
			for (item in menu) {
				if (item.isVisible) return item.itemId
			}
		}
		composeNavState?.navItems?.firstOrNull { !it.isActivityLauncher }?.let { return it.id }
		return null
	}

	private fun setNavbarIsLabeled(value: Boolean) {
		navRailHeader?.buttonExpand?.isVisible = value
		if (!value) setNavbarIsExpanded(false)
		navBar?.labelVisibilityMode = if (value) {
			NavigationBarView.LABEL_VISIBILITY_LABELED
		} else {
			NavigationBarView.LABEL_VISIBILITY_UNLABELED
		}
	}

	private fun setNavbarIsExpanded(value: Boolean) {
		val rail = navBar as? NavigationRailView ?: return
		if (value) {
			rail.expand()
			navRailHeader?.run {
				root.updateLayoutParams<FrameLayout.LayoutParams> {
					gravity = Gravity.TOP or Gravity.START
				}
				railFab.extend()
				buttonExpand.setImageResource(R.drawable.ic_drawer_menu_open)
				buttonExpand.setContentDescriptionAndTooltip(R.string.collapse)
				val horizontalPadding = rail.itemActiveIndicatorExpandedMarginHorizontal
				root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			}
		} else {
			rail.collapse()
			navRailHeader?.run {
				root.updateLayoutParams<FrameLayout.LayoutParams> {
					gravity = Gravity.TOP or Gravity.CENTER
				}
				railFab.shrink()
				buttonExpand.setImageResource(R.drawable.ic_drawer_menu)
				buttonExpand.setContentDescriptionAndTooltip(R.string.expand)
				val horizontalPadding = rail.itemActiveIndicatorMarginHorizontal
				root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			}
		}
	}

	fun interface OnFragmentChangedListener {

		fun onFragmentChanged(fragment: Fragment, fromUser: Boolean)
	}

	companion object {
		const val MAX_ITEM_COUNT = 6
	}
}
