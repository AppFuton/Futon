package io.github.landwarderer.futon.main.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
import com.google.android.material.search.SearchView
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.backups.ui.periodical.PeriodicalBackupService
import io.github.landwarderer.futon.browser.AdListUpdateService
import io.github.landwarderer.futon.core.exceptions.resolve.SnackbarErrorObserver
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.os.VoiceInputContract
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.NavItem
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.ui.components.AppTheme
import io.github.landwarderer.futon.core.ui.components.BottomNavBar
import io.github.landwarderer.futon.core.ui.components.BottomNavBarState
import io.github.landwarderer.futon.core.ui.util.FadingAppbarMediator
import io.github.landwarderer.futon.core.util.ext.consume
import io.github.landwarderer.futon.core.util.ext.end
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.start
import io.github.landwarderer.futon.databinding.ActivityMainBinding
import io.github.landwarderer.futon.details.service.MangaPrefetchService
import io.github.landwarderer.futon.favourites.ui.container.FavouritesContainerFragment
import io.github.landwarderer.futon.history.ui.HistoryListFragment
import io.github.landwarderer.futon.local.ui.LocalIndexUpdateService
import io.github.landwarderer.futon.local.ui.LocalStorageCleanupWorker
import io.github.landwarderer.futon.main.ui.owners.AppBarOwner
import io.github.landwarderer.futon.main.ui.owners.BottomNavOwner
import io.github.landwarderer.futon.remotelist.ui.MangaSearchMenuProvider
import io.github.landwarderer.futon.search.ui.suggestion.SearchSuggestionItemCallback
import io.github.landwarderer.futon.search.ui.suggestion.SearchSuggestionListenerImpl
import io.github.landwarderer.futon.search.ui.suggestion.SearchSuggestionMenuProvider
import io.github.landwarderer.futon.search.ui.suggestion.SearchSuggestionViewModel
import io.github.landwarderer.futon.search.ui.suggestion.adapter.SearchSuggestionAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject
import com.google.android.material.R as materialR

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(), AppBarOwner, BottomNavOwner,
	View.OnClickListener,
	SearchSuggestionItemCallback.SuggestionItemListener,
	MainNavigationDelegate.OnFragmentChangedListener,
	SearchView.TransitionListener {

	@Inject
	lateinit var settings: AppSettings

	private val viewModel by viewModels<MainViewModel>()
	private val searchSuggestionViewModel by viewModels<SearchSuggestionViewModel>()
	private val voiceInputLauncher = registerForActivityResult(VoiceInputContract()) { result ->
		if (result != null) {
			viewBinding.searchView.setText(result)
		}
	}
	private lateinit var navigationDelegate: MainNavigationDelegate
	private lateinit var fadingAppbarMediator: FadingAppbarMediator

	// Track pinned state locally so updateContainerBottomMargin can read it synchronously.
	// Initialized lazily so it is read after Hilt injects `settings`.
	private var isNavBarPinned: Boolean = false

	// Compose state for the bottom nav bar (null on wide/landscape layouts that use navRail)
	private var _bottomNavState: BottomNavBarState? = null

	override val appBar: AppBarLayout
		get() = viewBinding.appbar

	/** The ComposeView hosting the Compose nav bar, or null on wide layouts. */
	override val bottomNavView: ComposeView?
		get() = viewBinding.bottomNav

	/** The Compose state driving the nav bar, or null on wide layouts. */
	override val bottomNavState: BottomNavBarState?
		get() = _bottomNavState

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMainBinding.inflate(layoutInflater))
		setSupportActionBar(viewBinding.searchBar)

		// Read settings only after Hilt has injected `settings`
		isNavBarPinned = settings.isNavBarPinned

		viewBinding.fab?.setOnClickListener(this)
		viewBinding.navRail?.headerView?.findViewById<View>(R.id.railFab)?.setOnClickListener(this)
		fadingAppbarMediator =
			FadingAppbarMediator(viewBinding.appbar, viewBinding.layoutSearch ?: viewBinding.searchBar)

		// Run the one-time migration to Futon nav defaults (also enables the tutorial tip)
		settings.applyFutonNavDefaultIfNeeded()

		// Build nav state and wire Compose if we are on the phone (bottom nav) layout
		val navRail = viewBinding.navRail
		if (navRail == null && viewBinding.bottomNav != null) {
			val state = BottomNavBarState(settings).also { _bottomNavState = it }
			viewBinding.bottomNav!!.setContent {
				AppTheme {
					BottomNavBar(
						navItems = state.navItems,
						selectedItemId = state.selectedItemId,
						badgeCounts = state.badgeCounts,
						showTip = state.showTip,
						onItemClick = { item -> onComposeNavItemClick(item) },
						onTipDismiss = {
							settings.closeTip(AppSettings.TIP_NEW_NAV_DEFAULT)
							state.showTip = false
						},
					)
				}
			}
		}

		navigationDelegate = MainNavigationDelegate(
			navBar = viewBinding.navRail,
			fragmentManager = supportFragmentManager,
			settings = settings,
			composeNavState = _bottomNavState,
			context = this,
		)
		navigationDelegate.addOnFragmentChangedListener(this)
		navigationDelegate.onCreate(this, savedInstanceState)
		viewBinding.textViewTitle?.let { tv ->
			navigationDelegate.observeTitle().observe(this) { tv.text = it }
		}

		addMenuProvider(MainMenuProvider(router, viewModel))

		val exitCallback = ExitCallback(this, viewBinding.container)
		onBackPressedDispatcher.addCallback(exitCallback)
		onBackPressedDispatcher.addCallback(navigationDelegate)

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !resources.getBoolean(R.bool.is_predictive_back_enabled)) {
			val legacySearchCallback = SearchViewLegacyBackCallback(viewBinding.searchView)
			viewBinding.searchView.addTransitionListener(legacySearchCallback)
			onBackPressedDispatcher.addCallback(legacySearchCallback)
		}

		if (savedInstanceState == null) {
			onFirstStart()
		}

		viewBinding.searchView.addTransitionListener(this)
		viewBinding.searchView.addTransitionListener(exitCallback)

		// Observe bottom nav height changes for container margin (Compose layout)
		viewBinding.bottomNav?.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
			if (top != oldTop || bottom != oldBottom) updateContainerBottomMargin()
		}

		// Defer heavy initialisation to after the window is shown to prevent ANR on slow devices
		lifecycleScope.launch {
			lifecycle.withResumed {
				viewModel.onOpenReader.observeEvent(this@MainActivity, this@MainActivity::onOpenReader)
				viewModel.onError.observeEvent(this@MainActivity, SnackbarErrorObserver(viewBinding.container, null))
				viewModel.isLoading.observe(this@MainActivity, this@MainActivity::onLoadingStateChanged)
				viewModel.isResumeEnabled.observe(this@MainActivity, this@MainActivity::onResumeEnabledChanged)
				viewModel.feedCounter.observe(this@MainActivity, ::onFeedCounterChanged)
				viewModel.onFirstStart.observeEvent(this@MainActivity) { router.showWelcomeSheet() }
				viewModel.isBottomNavPinned.observe(this@MainActivity, ::setNavbarPinned)
				searchSuggestionViewModel.isIncognitoModeEnabled.observe(this@MainActivity, this@MainActivity::onIncognitoModeChanged)
				initSearch()
			}
		}
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		adjustSearchUI(viewBinding.searchView.isShowing)
		navigationDelegate.syncSelectedItem()
	}

	override fun onFragmentChanged(fragment: Fragment, fromUser: Boolean) {
		adjustFabVisibility(topFragment = fragment)
		adjustAppbar(topFragment = fragment)
		if (fromUser) {
			actionModeDelegate.finishActionMode()
			viewBinding.appbar.setExpanded(true)
		}
	}

	override fun addMenuProvider(provider: MenuProvider, owner: LifecycleOwner, state: Lifecycle.State) {
		if (provider !is MangaSearchMenuProvider) {
			super.addMenuProvider(provider, owner, state)
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.fab, R.id.railFab -> viewModel.openLastReader()
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		val searchBarDefaultMargin = resources.getDimensionPixelOffset(materialR.dimen.m3_searchbar_margin_horizontal)
		viewBinding.searchBar.updateLayoutParams<MarginLayoutParams> {
			marginEnd = searchBarDefaultMargin + barsInsets.end(v)
			marginStart = if (viewBinding.navRail != null) {
				searchBarDefaultMargin
			} else {
				searchBarDefaultMargin + barsInsets.start(v)
			}
		}
		// Pass only horizontal insets to the ComposeView — NavigationBar handles bottom insets
		// internally via WindowInsets.navigationBars, so adding bottom here causes double padding.
		viewBinding.bottomNav?.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
		)
		viewBinding.navRail?.updateLayoutParams<MarginLayoutParams> {
			marginStart = barsInsets.start(v)
			topMargin = barsInsets.top
			bottomMargin = barsInsets.bottom
		}
		updateContainerBottomMargin()
		return insets.consume(v, typeMask, start = viewBinding.navRail != null).also {
			handleSearchSuggestionsInsets(it)
		}
	}

	override fun onStateChanged(
		searchView: SearchView,
		previousState: SearchView.TransitionState,
		newState: SearchView.TransitionState,
	) {
		val wasOpened = previousState >= SearchView.TransitionState.SHOWING
		val isOpened = newState >= SearchView.TransitionState.SHOWING
		if (isOpened != wasOpened) {
			adjustSearchUI(isOpened)
		}
	}

	override fun onRemoveQuery(query: String) {
		searchSuggestionViewModel.deleteQuery(query)
	}

	override fun onSupportActionModeStarted(mode: ActionMode) {
		super.onSupportActionModeStarted(mode)
		adjustFabVisibility()
		setBottomNavVisible(false)
		(viewBinding.layoutSearch ?: viewBinding.searchBar).isInvisible = true
		updateContainerBottomMargin()
	}

	override fun onSupportActionModeFinished(mode: ActionMode) {
		super.onSupportActionModeFinished(mode)
		adjustFabVisibility()
		setBottomNavVisible(true)
		(viewBinding.layoutSearch ?: viewBinding.searchBar).isInvisible = false
		updateContainerBottomMargin()
	}

	// ---------------------------------------------------------------------------
	// Compose nav bar item click
	// ---------------------------------------------------------------------------

	private fun onComposeNavItemClick(item: NavItem) {
		val state = _bottomNavState ?: return
		val wasAlreadySelected = item.id == state.selectedItemId

		if (!item.isActivityLauncher) {
			// Update selection immediately so the bar responds on first tap
			state.selectedItemId = item.id
		}

		navigationDelegate.onComposeItemClick(item) { router.openSettings() }

		// Reselect → scroll to top
		if (wasAlreadySelected && !item.isActivityLauncher) {
			navigationDelegate.syncSelectedItem()
		}
	}

	// ---------------------------------------------------------------------------
	// Internal helpers
	// ---------------------------------------------------------------------------

	private fun onOpenReader(manga: Manga) {
		val fab = viewBinding.fab ?: viewBinding.navRail?.headerView
		router.openReader(manga, fab)
	}

	private fun onFeedCounterChanged(counter: Int) {
		navigationDelegate.setCounter(NavItem.FEED, counter)
	}

	private fun onIncognitoModeChanged(isIncognito: Boolean) {
		var options = viewBinding.searchView.getEditText().imeOptions
		options = if (isIncognito) {
			options or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
		} else {
			options and EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()
		}
		viewBinding.searchView.getEditText().imeOptions = options
		invalidateOptionsMenu()
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		val fab = viewBinding.fab ?: viewBinding.navRail?.headerView ?: return
		fab.isEnabled = !isLoading
	}

	private fun onResumeEnabledChanged(isEnabled: Boolean) {
		adjustFabVisibility(isResumeEnabled = isEnabled)
	}

	private fun onFirstStart() = try {
		lifecycleScope.launch(Dispatchers.Main) {
			withContext(Dispatchers.IO) {
				LocalStorageCleanupWorker.enqueue(applicationContext)
			}
			withResumed {
				MangaPrefetchService.prefetchLast(this@MainActivity)
				requestNotificationsPermission()
				startService(Intent(this@MainActivity, LocalIndexUpdateService::class.java))
				startService(Intent(this@MainActivity, PeriodicalBackupService::class.java))
				if (settings.isAdBlockEnabled) {
					startService(Intent(this@MainActivity, AdListUpdateService::class.java))
				}
			}
		}
	} catch (e: IllegalStateException) {
		e.printStackTraceDebug("MainActivity::onFirstStart")
	}

	private fun adjustAppbar(topFragment: Fragment) {
		if (topFragment is FavouritesContainerFragment) {
			viewBinding.appbar.fitsSystemWindows = true
			fadingAppbarMediator.bind()
		} else {
			viewBinding.appbar.fitsSystemWindows = false
			fadingAppbarMediator.unbind()
		}
	}

	private fun adjustFabVisibility(
		isResumeEnabled: Boolean = viewModel.isResumeEnabled.value,
		topFragment: Fragment? = navigationDelegate.primaryFragment,
		isSearchOpened: Boolean = viewBinding.searchView.isShowing,
	) {
		navigationDelegate.navRailHeader?.railFab?.isVisible = isResumeEnabled
		val fab = viewBinding.fab ?: return
		if (isResumeEnabled && !actionModeDelegate.isActionModeStarted && !isSearchOpened && topFragment is HistoryListFragment) {
			if (!fab.isVisible) fab.show()
		} else {
			if (fab.isVisible) fab.hide()
		}
	}

	private fun adjustSearchUI(isOpened: Boolean) {
		val appBarScrollFlags = if (isOpened) {
			SCROLL_FLAG_NO_SCROLL
		} else {
			SCROLL_FLAG_SCROLL or SCROLL_FLAG_ENTER_ALWAYS or SCROLL_FLAG_SNAP
		}
		viewBinding.insetsHolder.updateLayoutParams<AppBarLayout.LayoutParams> {
			scrollFlags = appBarScrollFlags
		}
		adjustFabVisibility(isSearchOpened = isOpened)
		setBottomNavVisible(!isOpened)
		updateContainerBottomMargin()
	}

	/** Show or hide the bottom nav bar (Compose state or View, whichever is present). */
	private fun setBottomNavVisible(visible: Boolean) {
		_bottomNavState?.isVisible = visible
		// The ComposeView itself needs to be visible/gone so CoordinatorLayout knows the height
		viewBinding.bottomNav?.let { composeView ->
			composeView.isVisible = visible
		}
	}

	private fun setNavbarPinned(isPinned: Boolean) {
		isNavBarPinned = isPinned
		for (view in viewBinding.appbar.children) {
			val lp = view.layoutParams as? AppBarLayout.LayoutParams ?: continue
			val scrollFlags = if (isPinned) {
				lp.scrollFlags and SCROLL_FLAG_SCROLL.inv()
			} else {
				lp.scrollFlags or SCROLL_FLAG_SCROLL
			}
			if (scrollFlags != lp.scrollFlags) {
				lp.scrollFlags = scrollFlags
				view.layoutParams = lp
			}
		}
		updateContainerBottomMargin()
	}

	private fun updateContainerBottomMargin() {
		val composeView = viewBinding.bottomNav ?: return
		val newMargin = if (isNavBarPinned && composeView.isVisible) composeView.height else 0
		with(viewBinding.container) {
			val params = layoutParams as MarginLayoutParams
			if (params.bottomMargin != newMargin) {
				params.bottomMargin = newMargin
				layoutParams = params
			}
		}
	}

	private fun requestNotificationsPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.POST_NOTIFICATIONS,
			) != PERMISSION_GRANTED
		) {
			ActivityCompat.requestPermissions(
				this,
				arrayOf(Manifest.permission.POST_NOTIFICATIONS),
				1,
			)
		}
	}

	private fun handleSearchSuggestionsInsets(insets: WindowInsetsCompat) {
		val typeMask = WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		viewBinding.recyclerViewSearch.setPadding(barsInsets.left, 0, barsInsets.right, barsInsets.bottom)
	}

	private fun initSearch() {
		val listener = SearchSuggestionListenerImpl(router, viewBinding.searchView, searchSuggestionViewModel)
		val adapter = SearchSuggestionAdapter(listener)
		viewBinding.searchView.toolbar.addMenuProvider(
			SearchSuggestionMenuProvider(this, voiceInputLauncher, searchSuggestionViewModel),
		)
		viewBinding.searchView.editText.addTextChangedListener(listener)
		viewBinding.recyclerViewSearch.adapter = adapter
		viewBinding.searchView.editText.setOnEditorActionListener(listener)

		viewBinding.searchView.observeState()
			.map { it >= SearchView.TransitionState.SHOWING }
			.distinctUntilChanged()
			.flatMapLatest { isShowing ->
				if (isShowing) searchSuggestionViewModel.suggestion else emptyFlow()
			}.observe(this, adapter)
		searchSuggestionViewModel.onError.observeEvent(
			this,
			SnackbarErrorObserver(viewBinding.recyclerViewSearch, null),
		)
		ItemTouchHelper(SearchSuggestionItemCallback(this))
			.attachToRecyclerView(viewBinding.recyclerViewSearch)
	}

	private fun SearchView.observeState() = callbackFlow {
		val listener = SearchView.TransitionListener { _, _, state ->
			trySendBlocking(state)
		}
		addTransitionListener(listener)
		awaitClose { removeTransitionListener(listener) }
	}
}
