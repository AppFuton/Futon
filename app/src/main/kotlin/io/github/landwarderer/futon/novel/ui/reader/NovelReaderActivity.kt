package io.github.landwarderer.futon.novel.ui.reader

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.network.MangaHttpClient
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseFullscreenActivity
import io.github.landwarderer.futon.core.util.MimeTypes
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.databinding.ActivityNovelReaderBinding
import io.github.landwarderer.futon.novel.data.model.NovelReadingMode
import io.github.landwarderer.futon.novel.data.source.BaseNovelRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class NovelReaderActivity :
	BaseFullscreenActivity<ActivityNovelReaderBinding>(),
	NovelReaderConfigSheet.Callback,
	Slider.OnSliderTouchListener,
	Slider.OnChangeListener {

	@Inject
	lateinit var appSettings: AppSettings

	@Inject
	@MangaHttpClient
	lateinit var baseOkHttpClient: OkHttpClient

	private val imageOkHttpClient by lazy {
		baseOkHttpClient.newBuilder()
			.connectTimeout(15, TimeUnit.SECONDS)
			.readTimeout(20, TimeUnit.SECONDS)
			.build()
	}

	private val viewModel by viewModels<NovelReaderViewModel>()
	private lateinit var readerSettings: NovelReaderSettings
	private var isUiVisible = true
	private var isSliderTracking = false
	@Volatile
	private var defaultUserAgent: String? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityNovelReaderBinding.inflate(layoutInflater))
		readerSettings = NovelReaderSettings.load(this)

		setupToolbar()
		setupWebView()
		setupControls()
		observeViewModel()
	}

	override fun onDestroy() {
		viewBinding.webView.destroy()
		super.onDestroy()
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

	@SuppressLint("SetJavaScriptEnabled")
	private fun setupWebView() {
		with(viewBinding.webView.settings) {
			javaScriptEnabled = true
			domStorageEnabled = true
			allowFileAccess = true
			loadWithOverviewMode = true
			useWideViewPort = true
			displayZoomControls = false
			builtInZoomControls = false
			defaultUserAgent = userAgentString
		}

		viewBinding.webView.setBackgroundColor(readerSettings.effectiveBackgroundColor)

		val bridge = NovelReaderBridge(
			scope = lifecycleScope,
			onScroll = { percent ->
				if (!isSliderTracking) {
					viewModel.updateScrollProgress(percent)
				}
			},
			onNearBottomReached = {
				viewModel.loadNextChapterForInfiniteScroll()
			},
			onCenterTapped = {
				toggleUiVisibility()
			},
			onPageChange = { cur, total ->
				viewBinding.textProgress.text = "$cur / $total"
			},
		)
		viewBinding.webView.addJavascriptInterface(bridge, "AndroidNovelBridge")

		viewBinding.webView.webViewClient = object : WebViewClient() {
			override fun shouldInterceptRequest(
				view: WebView?,
				request: WebResourceRequest?,
			): WebResourceResponse? {
				return try {
					val url = request?.url ?: return null
					val path = url.path.orEmpty()
					val epubParser = viewModel.getEpubParser()
					if (epubParser != null && (url.scheme == "epub" || url.host == "futon.reader")) {
						val cleanPath = path.removePrefix("/")
						val stream: InputStream? = epubParser.readEntry(cleanPath)
						if (stream != null) {
							val ext = cleanPath.substringAfterLast('.', "")
							val mime = MimeTypes.getMimeTypeFromExtension(ext)?.toString() ?: "application/octet-stream"
							return WebResourceResponse(mime, null, stream)
						}
					}
					val host = url.host.orEmpty()
					if (host.equals("img.lnori.com", ignoreCase = true) || host.endsWith(".lnori.com", ignoreCase = true)) {
						return interceptLnoriImage(request)
					}
					super.shouldInterceptRequest(view, request)
				} catch (e: Throwable) {
					null
				}
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: androidx.core.view.WindowInsetsCompat): androidx.core.view.WindowInsetsCompat {
		val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
		viewBinding.appbarTop.setPadding(0, systemBars.top, 0, 0)
		viewBinding.cardBottom.setPadding(0, 0, 0, systemBars.bottom)
		return insets
	}

	private fun setupControls() {
		viewBinding.buttonPrev.setOnClickListener { viewModel.switchChapter(-1) }
		viewBinding.buttonNext.setOnClickListener { viewModel.switchChapter(1) }
		viewBinding.buttonFontSettings.setOnClickListener {
			NovelReaderConfigSheet.newInstance().show(supportFragmentManager, NovelReaderConfigSheet.TAG)
		}

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

		viewModel.currentChapter.observe(this) { chapter ->
			if (chapter != null) {
				supportActionBar?.subtitle = chapter.title
				val idx = viewModel.currentChapterIndex.value + 1
				val total = viewModel.chapters.value.size
				viewBinding.textProgress.text = if (total > 0) "$idx / $total" else chapter.title
			}
		}

		viewModel.isReadingLoading.observe(this) { loading ->
			viewBinding.progressBar.isVisible = loading
		}

		viewModel.chapterContent.observe(this) { html ->
			if (!html.isNullOrEmpty()) {
				renderChapterContent(html)
			}
		}

		viewModel.onChapterAppended.observeEvent(this) { (title, html) ->
			val quotedTitle = org.json.JSONObject.quote(title)
			val quotedHtml = org.json.JSONObject.quote(html)
			viewBinding.webView.evaluateJavascript("appendChapter($quotedTitle, $quotedHtml);", null)
		}

		viewModel.scrollProgress.observe(this) { percent ->
			if (!isSliderTracking) {
				viewBinding.sliderProgress.value = (percent * 100f).coerceIn(0f, 100f)
			}
		}
	}

	private fun renderChapterContent(html: String) {
		val title = viewModel.currentChapter.value?.title ?: ""
		val fullDoc = NovelHtmlTemplate.buildDocument(title, html, readerSettings)
		val chapterUrl = viewModel.currentChapter.value?.url?.substringBefore('#')
		val isOnline = !chapterUrl.isNullOrEmpty() && (chapterUrl.startsWith("http://") || chapterUrl.startsWith("https://"))
		val baseUrl = if (isOnline) chapterUrl else "https://futon.reader/"
		viewBinding.webView.loadDataWithBaseURL(baseUrl, fullDoc, "text/html", "UTF-8", null)
	}

	private fun interceptLnoriImage(request: WebResourceRequest): WebResourceResponse? {
		var response: Response? = null
		return try {
			val urlString = request.url.toString()
			val userAgent = request.requestHeaders?.get("User-Agent")
				?: defaultUserAgent
				?: BaseNovelRepository.DEFAULT_USER_AGENT

			val okHttpRequest = Request.Builder()
				.url(urlString)
				.method(request.method, null)
				.header("Referer", "https://lnori.com/")
				.apply {
					if (!userAgent.isNullOrBlank()) {
						header("User-Agent", userAgent)
					}
				}
				.build()

			response = imageOkHttpClient.newCall(okHttpRequest).execute()
			if (!response.isSuccessful) {
				response.close()
				return null
			}

			val body = response.body ?: run {
				response.close()
				return null
			}

			val rawContentType = response.header("Content-Type")?.substringBefore(';')?.trim()
			val mimeType = if (!rawContentType.isNullOrBlank() && rawContentType != "application/octet-stream") {
				rawContentType
			} else {
				val ext = request.url.path?.substringAfterLast('.', "") ?: "avif"
				MimeTypes.getMimeTypeFromExtension(ext)?.toString() ?: "image/avif"
			}

			val responseHeaders = mutableMapOf<String, String>()
			for (i in 0 until response.headers.size) {
				val name = response.headers.name(i)
				if (name.equals("content-encoding", ignoreCase = true) ||
					name.equals("transfer-encoding", ignoreCase = true)
				) {
					continue
				}
				responseHeaders[name] = response.headers.value(i)
			}

			val wrappedStream = object : FilterInputStream(body.byteStream()) {
				override fun close() {
					try {
						super.close()
					} finally {
						response.close()
					}
				}
			}

			WebResourceResponse(
				mimeType,
				null,
				response.code,
				response.message.ifBlank { "OK" },
				responseHeaders,
				wrappedStream,
			)
		} catch (e: Throwable) {
			response?.close()
			null
		}
	}

	private fun toggleUiVisibility() {
		isUiVisible = !isUiVisible
		viewBinding.appbarTop.isVisible = isUiVisible
		viewBinding.cardBottom.isVisible = isUiVisible
	}

	override fun onNovelSettingsChanged(settings: NovelReaderSettings) {
		val modeChanged = settings.readingMode != readerSettings.readingMode
		readerSettings = settings
		viewBinding.webView.setBackgroundColor(settings.effectiveBackgroundColor)

		if (modeChanged) {
			val currentHtml = viewModel.chapterContent.value
			if (!currentHtml.isNullOrEmpty()) {
				renderChapterContent(currentHtml)
			}
		} else {
			val cssText = org.json.JSONObject.quote(settings.toCssVariables())
			viewBinding.webView.evaluateJavascript("updateStyles($cssText);", null)
		}
	}

	override fun onStartTrackingTouch(slider: Slider) {
		isSliderTracking = true
	}

	override fun onStopTrackingTouch(slider: Slider) {
		isSliderTracking = false
		val percent = slider.value / 100f
		viewModel.updateScrollProgress(percent)
		viewBinding.webView.evaluateJavascript("scrollToPercent($percent);", null)
	}

	override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
		if (fromUser) {
			viewBinding.textProgress.text = "${value.toInt()}%"
		}
	}
}
