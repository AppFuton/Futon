package io.github.landwarderer.futon.novel.data.lnreader

import android.content.Context
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import io.github.landwarderer.futon.novel.data.lnreader.repository.LnPluginManager
import io.github.landwarderer.futon.novel.data.lnreader.storage.LnPluginStorage
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class LnPluginStorageTest {

	@get:Rule
	val tempFolder = TemporaryFolder()

	private lateinit var context: Context
	private lateinit var storage: LnPluginStorage
	private lateinit var manager: LnPluginManager

	@Before
	fun setUp() {
		context = mock(Context::class.java)
		val filesDir = tempFolder.newFolder("files")
		`when`(context.filesDir).thenReturn(filesDir)

		storage = LnPluginStorage(context)
		manager = LnPluginManager(OkHttpClient(), storage)
	}

	@Test
	fun testPluginSaveReadAndIsInstalled() = runTest {
		val plugin = LnPlugin(
			id = "test_novel",
			name = "Test Novel",
			site = "https://example.com",
			lang = "en",
			version = "1.0.0",
			url = "https://example.com/test.js",
			iconUrl = "https://example.com/icon.png",
		)

		assertFalse(storage.isPluginInstalled(plugin.id))

		val fakeScript = "console.log('hello plugin');"
		storage.savePlugin(plugin, fakeScript)

		assertTrue(storage.isPluginInstalled(plugin.id))
		assertEquals(fakeScript, storage.readPluginScript(plugin.id))

		val installedList = storage.getInstalledPlugins()
		assertEquals(1, installedList.size)
		assertEquals("test_novel", installedList[0].id)
		assertEquals("Test Novel", installedList[0].name)
		assertTrue(installedList[0].isInstalled)
	}

	@Test
	fun testPluginDelete() = runTest {
		val plugin = LnPlugin(
			id = "to_delete",
			name = "Delete Me",
			site = "https://example.com",
			lang = "en",
			version = "1.0.0",
			url = "https://example.com/del.js",
			iconUrl = "https://example.com/del.png",
		)

		storage.savePlugin(plugin, "test")
		assertTrue(storage.isPluginInstalled(plugin.id))

		val deleted = storage.deletePlugin(plugin.id)
		assertTrue(deleted)
		assertFalse(storage.isPluginInstalled(plugin.id))
		assertEquals(0, storage.getInstalledPlugins().size)
	}

	@Test
	fun testSemverComparison() {
		assertTrue(manager.isNewerVersion("2.0.0", "1.9.9"))
		assertTrue(manager.isNewerVersion("1.1.0", "1.0.9"))
		assertTrue(manager.isNewerVersion("1.0.1", "1.0.0"))
		assertTrue(manager.isNewerVersion("2.3.4", "2.3.3"))

		assertFalse(manager.isNewerVersion("1.0.0", "1.0.0"))
		assertFalse(manager.isNewerVersion("1.0.0", "2.0.0"))
		assertFalse(manager.isNewerVersion("1.2.3", "1.2.4"))
	}
}

