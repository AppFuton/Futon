package io.github.landwarderer.futon.novel.ui.reader.pdf

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseFullscreenActivity
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.databinding.ActivityPdfReaderBinding

@AndroidEntryPoint
class PdfReaderActivity :
	BaseFullscreenActivity<ActivityPdfReaderBinding>(),
	Slider.OnSliderTouchListener,
	Slider.OnChangeListener {

	private val viewModel by viewModels<PdfReaderViewModel>()
	private lateinit var adapter: PdfPageAdapter
	private var isUiVisible = true
	private var isSliderTracking = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityPdfReaderBinding.inflate(layoutInflater))

		setupToolbar()
		setupRecyclerView()
		setupControls()
		observeViewModel()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.appbarTop.setPadding(0, systemBars.top, 0, 0)
		viewBinding.cardBottom.setPadding(0, 0, 0, systemBars.bottom)
		return insets
	}

	private fun setupToolbar() {
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		val initialTitle = viewModel.manga.value?.title ?: getString(R.string.loading_)
		title = initialTitle
		supportActionBar?.title = initialTitle
		supportActionBar?.subtitle = null
		viewBinding.toolbar.setNavigationOnClickListener {
			onBackPressedDispatcher.onBackPressed()
		}
	}

	private fun setupRecyclerView() {
		val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
		viewBinding.recyclerView.layoutManager = layoutManager

		adapter = PdfPageAdapter(
			scope = lifecycleScope,
			getPdfHelper = { viewModel.getPdfHelper() },
			onPageClick = { toggleUiVisibility() },
		)
		viewBinding.recyclerView.adapter = adapter

		viewBinding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				super.onScrolled(recyclerView, dx, dy)
				if (!isSliderTracking) {
					val pos = layoutManager.findFirstVisibleItemPosition()
					if (pos != RecyclerView.NO_POSITION) {
						viewModel.onPageChanged(pos)
					}
				}
			}
		})

		val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
			override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
				toggleUiVisibility()
				return true
			}
		})

		viewBinding.recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
			override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
				gestureDetector.onTouchEvent(e)
				return false
			}
		})
	}

	private fun setupControls() {
		viewBinding.buttonPrev.setOnClickListener { viewModel.switchPage(-1) }
		viewBinding.buttonNext.setOnClickListener { viewModel.switchPage(1) }
		viewBinding.sliderProgress.addOnSliderTouchListener(this)
		viewBinding.sliderProgress.addOnChangeListener(this)
	}

	@SuppressLint("SetTextI18n")
	private fun observeViewModel() {
		viewModel.manga.observe(this) { manga ->
			if (manga != null) {
				title = manga.title
				supportActionBar?.title = manga.title
			}
		}

		viewModel.isLoading.observe(this) { loading ->
			viewBinding.progressBar.isVisible = loading
		}

		viewModel.pageCount.observe(this) { count ->
			adapter.setPageCount(count)
			if (count > 0) {
				viewBinding.sliderProgress.valueFrom = 1f
				viewBinding.sliderProgress.valueTo = count.toFloat()
				viewBinding.sliderProgress.stepSize = 1f
				updatePageIndicator(viewModel.currentPage.value, count)
			} else {
				viewBinding.sliderProgress.valueFrom = 1f
				viewBinding.sliderProgress.valueTo = 1f
				updatePageIndicator(0, 0)
			}
		}

		viewModel.currentPage.observe(this) { page ->
			val count = viewModel.pageCount.value
			updatePageIndicator(page, count)
			if (!isSliderTracking && count > 0) {
				val sliderVal = (page + 1).toFloat().coerceIn(1f, count.toFloat())
				viewBinding.sliderProgress.value = sliderVal
			}
		}

		viewModel.onJumpToPage.observeEvent(this) { page ->
			viewBinding.recyclerView.scrollToPosition(page)
		}
	}

	private fun updatePageIndicator(page: Int, total: Int) {
		val text = if (total > 0) "${page + 1} / $total" else ""
		supportActionBar?.subtitle = text
		viewBinding.textProgress.text = text
	}

	private fun toggleUiVisibility() {
		isUiVisible = !isUiVisible
		viewBinding.appbarTop.isVisible = isUiVisible
		viewBinding.cardBottom.isVisible = isUiVisible
		systemUiController.setSystemUiVisible(isUiVisible)
	}

	override fun onStartTrackingTouch(slider: Slider) {
		isSliderTracking = true
	}

	override fun onStopTrackingTouch(slider: Slider) {
		isSliderTracking = false
		val targetPage = (slider.value.toInt() - 1).coerceAtLeast(0)
		viewModel.jumpToPage(targetPage)
	}

	override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
		if (fromUser) {
			val total = viewModel.pageCount.value
			updatePageIndicator(value.toInt() - 1, total)
		}
	}
}
