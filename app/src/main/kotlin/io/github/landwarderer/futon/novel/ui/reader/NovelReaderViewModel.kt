package io.github.landwarderer.futon.novel.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.novel.data.epub.EpubParser
import io.github.landwarderer.futon.novel.data.pdf.PdfRendererHelper
import io.github.landwarderer.futon.core.nav.ReaderIntent
import io.github.landwarderer.futon.reader.ui.ReaderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import java.io.File
import javax.inject.Inject

@HiltViewModel
class NovelReaderViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val mangaDataRepository: MangaDataRepository,
	private val mangaRepositoryFactory: io.github.landwarderer.futon.core.parser.MangaRepository.Factory,
	private val historyRepository: HistoryRepository,
	val settings: AppSettings,
) : BaseViewModel() {

	private val initialManga: Manga? = savedStateHandle.get<ParcelableManga>(AppRouter.KEY_MANGA)?.manga
	private val mangaId: Long = savedStateHandle.get<Long>(AppRouter.KEY_ID) ?: initialManga?.id ?: 0L
	private val initialState: ReaderState? = savedStateHandle.get<ReaderState>(ReaderIntent.EXTRA_STATE)

	private val _manga = MutableStateFlow<Manga?>(initialManga)
	val manga: StateFlow<Manga?> = _manga.asStateFlow()

	private val _chapters = MutableStateFlow<List<MangaChapter>>(emptyList())
	val chapters: StateFlow<List<MangaChapter>> = _chapters.asStateFlow()

	private val _currentChapterIndex = MutableStateFlow(0)
	val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

	private val _currentChapter = MutableStateFlow<MangaChapter?>(null)
	val currentChapter: StateFlow<MangaChapter?> = _currentChapter.asStateFlow()

	private val _chapterContent = MutableStateFlow<String?>(null)
	val chapterContent: StateFlow<String?> = _chapterContent.asStateFlow()

	private val _isReadingLoading = MutableStateFlow(false)
	val isReadingLoading: StateFlow<Boolean> = _isReadingLoading.asStateFlow()

	private val _scrollProgress = MutableStateFlow(0f)
	val scrollProgress: StateFlow<Float> = _scrollProgress.asStateFlow()

	private val _isPdf = MutableStateFlow(false)
	val isPdf: StateFlow<Boolean> = _isPdf.asStateFlow()

	val onChapterAppended = MutableEventFlow<Pair<String, String>>() // title, html

	private var epubParser: EpubParser? = null
	private var pdfHelper: PdfRendererHelper? = null

	init {
		loadNovel()
	}

	override fun onCleared() {
		super.onCleared()
		pdfHelper?.close()
		pdfHelper = null
	}

	fun loadNovel() {
		launchJob(Dispatchers.IO) {
			_isReadingLoading.value = true
			try {
				var current = _manga.value
				if (current == null && mangaId != 0L) {
					current = mangaDataRepository.findMangaById(mangaId, withChapters = true)
					_manga.value = current
				}

				if (current == null) {
					_isReadingLoading.value = false
					return@launchJob
				}

				val mangaFile = current.url.let { url ->
					if (url.startsWith("file://")) File(url.removePrefix("file://"))
					else File(url)
				}

				val isPdfFile = mangaFile.exists() && mangaFile.extension.equals("pdf", ignoreCase = true)
				val isEpubFile = mangaFile.exists() && mangaFile.extension.equals("epub", ignoreCase = true)
				_isPdf.value = isPdfFile

				val chapterList = if (isEpubFile) {
					epubParser = EpubParser(mangaFile)
					val book = epubParser?.parseBook()
					book?.chapters?.map { ec ->
						MangaChapter(
							id = ec.id,
							title = ec.title,
							number = ec.index.toFloat(),
							volume = 0,
							url = ec.href,
							uploadDate = 0L,
							source = current.source,
							scanlator = null,
							branch = null,
						)
					} ?: emptyList()
				} else if (isPdfFile) {
					pdfHelper = PdfRendererHelper(mangaFile)
					val count = pdfHelper?.pageCount ?: 0
					(0 until count).map { pageIdx ->
						MangaChapter(
							id = pageIdx.toLong(),
							title = "Page ${pageIdx + 1}",
							number = (pageIdx + 1).toFloat(),
							volume = 0,
							url = "pdf://page/$pageIdx",
							uploadDate = 0L,
							source = current.source,
							scanlator = null,
							branch = null,
						)
					}
				} else {
					// Online novel or database chapters
					val repo = mangaRepositoryFactory.create(current.source)
					val fullDetails = repo.getDetails(current)
					fullDetails.chapters.orEmpty()
				}

				_chapters.value = chapterList

				// Determine start chapter
				val targetChapterId = initialState?.chapterId
				val startIdx = if (targetChapterId != null) {
					chapterList.indexOfFirst { it.id == targetChapterId }.coerceAtLeast(0)
				} else {
					val lastHistory = historyRepository.getOne(current)
					if (lastHistory != null) {
						chapterList.indexOfFirst { it.id == lastHistory.chapterId }.coerceAtLeast(0)
					} else {
						0
					}
				}

				if (chapterList.isNotEmpty()) {
					loadChapterAtIndex(startIdx)
				} else {
					_chapterContent.value = "<div style='padding: 32px; text-align: center;'><p>No chapters found for this novel.</p></div>"
				}
			} catch (e: Exception) {
				e.printStackTraceDebug()
				_chapterContent.value = "<div style='padding: 32px; text-align: center;'><p>Error loading novel details.</p></div>"
			} finally {
				_isReadingLoading.value = false
			}
		}
	}

	fun loadChapterAtIndex(index: Int) {
		val list = _chapters.value
		if (index !in list.indices) return
		val target = list[index]
		_currentChapterIndex.value = index
		_currentChapter.value = target

		launchJob(Dispatchers.IO) {
			_isReadingLoading.value = true
			try {
				val content = fetchChapterHtml(target)
				if (content.isNullOrBlank()) {
					_chapterContent.value = "<div style='padding: 32px; text-align: center;'><p>Unable to load chapter content.</p><p style='opacity: 0.7; font-size: 0.9em;'>Please check your network connection and try again.</p></div>"
				} else {
					_chapterContent.value = content
				}
				saveReadingProgress(0f)
			} catch (e: Exception) {
				e.printStackTraceDebug()
				_chapterContent.value = "<div style='padding: 32px; text-align: center;'><p>Error loading chapter.</p></div>"
			} finally {
				_isReadingLoading.value = false
			}
		}
	}

	fun switchChapter(delta: Int) {
		val nextIdx = _currentChapterIndex.value + delta
		loadChapterAtIndex(nextIdx)
	}

	fun loadNextChapterForInfiniteScroll() {
		val list = _chapters.value
		val nextIdx = _currentChapterIndex.value + 1
		if (nextIdx !in list.indices) return
		val nextChapter = list[nextIdx]

		launchJob(Dispatchers.IO) {
			try {
				val html = fetchChapterHtml(nextChapter)
				if (!html.isNullOrBlank()) {
					_currentChapterIndex.value = nextIdx
					_currentChapter.value = nextChapter
					onChapterAppended.call(Pair(nextChapter.title.orEmpty(), html))
					saveReadingProgress(0f)
				}
			} catch (e: Exception) {
				e.printStackTraceDebug()
			}
		}
	}

	private suspend fun fetchChapterHtml(chapter: MangaChapter): String? = withContext(Dispatchers.IO) {
		val parser = epubParser
		if (parser != null) {
			return@withContext parser.getChapterHtml(chapter.url)
		}

		val curManga = _manga.value ?: return@withContext null
		try {
			// Online novel source: fetch pages / chapter content
			val repo = mangaRepositoryFactory.create(curManga.source)
			val pages = repo.getPages(chapter)
			// For text sources, pages may contain formatted text in url or html content
			val textBuilder = StringBuilder()
			for (page in pages) {
				val trimmed = page.url.trim()
				if (trimmed.startsWith("<p") || trimmed.startsWith("<div") || trimmed.startsWith("<html") || trimmed.startsWith("<span") || trimmed.startsWith("<article")) {
					textBuilder.append(trimmed)
				} else if (trimmed.isNotEmpty()) {
					textBuilder.append("<p>${trimmed}</p>")
				}
			}
			textBuilder.toString()
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
	}

	fun updateScrollProgress(percent: Float) {
		_scrollProgress.value = percent
		saveReadingProgress(percent)
	}

	private fun saveReadingProgress(percent: Float) {
		val cur = _manga.value ?: return
		val chap = _currentChapter.value ?: return
		viewModelScope.launch(Dispatchers.IO) {
			val chaps = _chapters.value
			historyRepository.addOrUpdate(
				manga = cur.copy(chapters = chaps),
				chapterId = chap.id,
				page = _currentChapterIndex.value,
				scroll = (percent * 100).toInt(),
				percent = percent,
				force = false,
			)
		}
	}

	fun getPdfHelper(): PdfRendererHelper? = pdfHelper

	fun getEpubParser(): EpubParser? = epubParser
}
