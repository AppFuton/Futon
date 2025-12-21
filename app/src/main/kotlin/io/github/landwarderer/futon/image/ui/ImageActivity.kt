package io.github.landwarderer.futon.image.ui

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.lifecycle
import coil3.target.GenericViewTarget
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.exceptions.resolve.SnackbarErrorObserver
import io.github.landwarderer.futon.core.image.CoilMemoryCacheKey
import io.github.landwarderer.futon.core.model.MangaSource
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.ui.util.PopupMenuMediator
import io.github.landwarderer.futon.core.util.ShareHelper
import io.github.landwarderer.futon.core.util.ext.consumeAll
import io.github.landwarderer.futon.core.util.ext.end
import io.github.landwarderer.futon.core.util.ext.enqueueWith
import io.github.landwarderer.futon.core.util.ext.getDisplayIcon
import io.github.landwarderer.futon.core.util.ext.getDisplayMessage
import io.github.landwarderer.futon.core.util.ext.getParcelableExtraCompat
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import io.github.landwarderer.futon.core.util.ext.mangaSourceExtra
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.core.util.ext.start
import io.github.landwarderer.futon.databinding.ActivityImageBinding
import io.github.landwarderer.futon.databinding.ItemErrorStateBinding
import javax.inject.Inject
import androidx.appcompat.R as appcompatR

@AndroidEntryPoint
class ImageActivity : BaseActivity<ActivityImageBinding>(),
	ImageRequest.Listener,
	View.OnClickListener {

	@Inject
	lateinit var coil: ImageLoader

	private var errorBinding: ItemErrorStateBinding? = null
	private val viewModel: ImageViewModel by viewModels()
	private lateinit var menuMediator: PopupMenuMediator

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityImageBinding.inflate(layoutInflater))
		viewBinding.buttonBack.setOnClickListener(this)
		viewBinding.buttonMenu.setOnClickListener(this)

		val menuProvider = ImageMenuProvider(
			activity = this,
			snackbarHost = viewBinding.root,
			viewModel = viewModel,
		)
		menuMediator = PopupMenuMediator(menuProvider)
		viewModel.isLoading.observe(this, ::onLoadingStateChanged)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.root, null))
		viewModel.onImageSaved.observeEvent(this, ::onImageSaved)
		loadImage()
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_back -> dispatchNavigateUp()
			R.id.button_menu -> menuMediator.onLongClick(v)
			else -> loadImage()
		}
	}

	override fun onError(request: ImageRequest, result: ErrorResult) {
		viewBinding.progressBar.hide()
		with(errorBinding ?: ItemErrorStateBinding.bind(viewBinding.stubError.inflate())) {
			errorBinding = this
			root.isVisible = true
			textViewError.text = result.throwable.getDisplayMessage(resources)
			textViewError.setCompoundDrawablesWithIntrinsicBounds(0, result.throwable.getDisplayIcon(), 0, 0)
			buttonRetry.isVisible = true
			buttonRetry.setOnClickListener(this@ImageActivity)
		}
	}

	override fun onStart(request: ImageRequest) {
		viewBinding.progressBar.show()
		(errorBinding?.root ?: viewBinding.stubError).isVisible = false
	}

	override fun onSuccess(request: ImageRequest, result: SuccessResult) {
		viewBinding.progressBar.hide()
		(errorBinding?.root ?: viewBinding.stubError).isVisible = false
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		val baseMargin = v.resources.getDimensionPixelOffset(R.dimen.screen_padding)
		viewBinding.buttonMenu.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			marginEnd = barsInsets.end(v) + baseMargin
			topMargin = barsInsets.top + baseMargin
		}
		viewBinding.buttonBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			marginStart = barsInsets.start(v) + baseMargin
			topMargin = barsInsets.top + baseMargin
		}
		return insets.consumeAll(typeMask)
	}

	private fun loadImage() {
		ImageRequest.Builder(this)
			.data(intent.data)
			.memoryCacheKey(intent.getParcelableExtraCompat<CoilMemoryCacheKey>(AppRouter.KEY_PREVIEW)?.data)
			.memoryCachePolicy(CachePolicy.READ_ONLY)
			.lifecycle(this)
			.listener(this)
			.mangaSourceExtra(MangaSource(intent.getStringExtra(AppRouter.KEY_SOURCE)))
			.target(SsivTarget(viewBinding.ssiv))
			.enqueueWith(coil)
	}

	private fun onImageSaved(uri: Uri) {
		Snackbar.make(viewBinding.root, R.string.page_saved, Snackbar.LENGTH_LONG)
			.setAction(R.string.share) {
				ShareHelper(this).shareImage(uri)
			}.show()
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		val button = viewBinding.buttonMenu
		button.isClickable = !isLoading
		if (isLoading) {
			button.setImageDrawable(
				CircularProgressDrawable(this).also {
					it.setStyle(CircularProgressDrawable.LARGE)
					it.setColorSchemeColors(getThemeColor(appcompatR.attr.colorControlNormal))
					it.start()
				},
			)
		} else {
			button.setImageResource(appcompatR.drawable.abc_ic_menu_overflow_material)
		}
	}

	private class SsivTarget(
		override val view: SubsamplingScaleImageView,
	) : GenericViewTarget<SubsamplingScaleImageView>() {

		override var drawable: Drawable? = null
			set(value) {
				field = value
				setImageDrawable(value)
			}

		override fun equals(other: Any?): Boolean {
			return (this === other) || (other is SsivTarget && view == other.view)
		}

		override fun hashCode() = view.hashCode()

		override fun toString() = "SsivTarget(view=$view)"

		private fun setImageDrawable(drawable: Drawable?) {
			if (drawable != null) {
				view.setImage(ImageSource.bitmap(drawable.toBitmap()))
			} else {
				view.recycle()
			}
		}
	}
}
