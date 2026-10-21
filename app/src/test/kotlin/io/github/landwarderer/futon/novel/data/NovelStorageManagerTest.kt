package io.github.landwarderer.futon.novel.data

import android.content.Context
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

class NovelStorageManagerTest {

	@get:Rule
	val tempFolder = TemporaryFolder()

	private lateinit var context: Context
	private lateinit var mangaRepository: MangaDataRepository
	private lateinit var settings: AppSettings
	private lateinit var manager: NovelStorageManager

	@Before
	fun setUp() {
		context = mock(Context::class.java)
		mangaRepository = mock(MangaDataRepository::class.java)
		settings = mock(AppSettings::class.java)

		val appFilesDir = tempFolder.newFolder("app_files")
		`when`(context.getExternalFilesDir(null)).thenReturn(appFilesDir)

		manager = NovelStorageManager(context, mangaRepository, settings)
	}

	@Test
	fun testIsSupportedFile() {
		assertTrue(NovelStorageManager.isSupportedFile(File("book.epub")))
		assertTrue(NovelStorageManager.isSupportedFile(File("doc.PDF")))
		assertTrue(NovelStorageManager.isSupportedFile(File("sample.Epub")))
		assertTrue(NovelStorageManager.isSupportedFile(File("document.pdf")))
		assertFalse(NovelStorageManager.isSupportedFile(File("image.png")))
		assertFalse(NovelStorageManager.isSupportedFile(File("text.txt")))
		assertFalse(NovelStorageManager.isSupportedFile(File("archive.zip")))
		assertFalse(NovelStorageManager.isSupportedFile(File("no_extension")))
	}

	@Test
	fun testGetNovelDirectoriesDiscovery() {
		val userDir1 = tempFolder.newFolder("user_novels_1")
		val userDir2 = tempFolder.newFolder("user_novels_2")
		`when`(settings.userSpecifiedNovelDirectories).thenReturn(setOf(userDir1, userDir2))

		val directories = manager.getNovelDirectories()

		val expectedNovelsDir = manager.novelsDir
		assertEquals(3, directories.size)
		assertEquals(expectedNovelsDir, directories[0])
		assertTrue(directories.contains(userDir1))
		assertTrue(directories.contains(userDir2))
	}

	@Test
	fun testAddNovelDirectory() {
		val existingDir = tempFolder.newFolder("existing_dir")
		val newDir = tempFolder.newFolder("new_dir")
		`when`(settings.userSpecifiedNovelDirectories).thenReturn(setOf(existingDir))

		manager.addNovelDirectory(newDir)

		verify(settings).userSpecifiedNovelDirectories = setOf(existingDir, newDir)
	}

	@Test
	fun testRemoveNovelDirectory() {
		val dir1 = tempFolder.newFolder("dir_1")
		val dir2 = tempFolder.newFolder("dir_2")
		`when`(settings.userSpecifiedNovelDirectories).thenReturn(setOf(dir1, dir2))

		manager.removeNovelDirectory(dir1)

		verify(settings).userSpecifiedNovelDirectories = setOf(dir2)
	}
}
