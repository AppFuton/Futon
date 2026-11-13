package io.github.landwarderer.futon.novel.ui.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import io.github.landwarderer.futon.novel.data.lnreader.repository.LnPluginManager

@OptIn(ExperimentalCoroutinesApi::class)
class NovelPluginsManagerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var pluginManager: LnPluginManager
    private val plugin1 = LnPlugin(
        id = "royalroad",
        name = "Royal Road",
        site = "https://royalroad.com",
        lang = "English",
        version = "1.0.0",
        url = "https://royalroad.com/index.js",
        iconUrl = "https://royalroad.com/icon.png",
        isInstalled = true,
    )
    private val plugin2 = LnPlugin(
        id = "novelfull",
        name = "NovelFull",
        site = "https://novelfull.com",
        lang = "English",
        version = "1.0.1",
        url = "https://novelfull.com/index.js",
        iconUrl = "https://novelfull.com/icon.png",
        isInstalled = false,
    )
    private val plugin3 = LnPlugin(
        id = "tunovel",
        name = "TuNovelaLigera",
        site = "https://tunovelaligera.com",
        lang = "Spanish",
        version = "1.0.0",
        url = "https://tunovelaligera.com/index.js",
        iconUrl = "https://tunovelaligera.com/icon.png",
        isInstalled = false,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pluginManager = mock(LnPluginManager::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadPluginsSuccess() = runTest {
        `when`(pluginManager.fetchAvailablePlugins()).thenReturn(listOf(plugin1, plugin2, plugin3))

        val viewModel = NovelPluginsManagerViewModel(pluginManager)
        viewModel.ioDispatcher = testDispatcher
        viewModel.loadPlugins()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(3, state.items.size)
        // Installed plugin should be ordered first
        assertEquals("Royal Road", state.items[0].name)
    }

    @Test
    fun testFilterPlugins() = runTest {
        `when`(pluginManager.fetchAvailablePlugins()).thenReturn(listOf(plugin1, plugin2, plugin3))

        val viewModel = NovelPluginsManagerViewModel(pluginManager)
        viewModel.ioDispatcher = testDispatcher
        viewModel.loadPlugins()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.filter("NovelFull")
        val state = viewModel.state.value
        assertEquals(1, state.items.size)
        assertEquals("NovelFull", state.items[0].name)

        viewModel.filter("Spanish")
        val spanishState = viewModel.state.value
        assertEquals(1, spanishState.items.size)
        assertEquals("TuNovelaLigera", spanishState.items[0].name)

        viewModel.filter("")
        val allState = viewModel.state.value
        assertEquals(3, allState.items.size)
    }

    @Test
    fun testInstallPlugin() = runTest {
        `when`(pluginManager.fetchAvailablePlugins()).thenReturn(listOf(plugin1, plugin2))
        `when`(pluginManager.installPlugin(plugin2)).thenReturn(true)

        val viewModel = NovelPluginsManagerViewModel(pluginManager)
        viewModel.ioDispatcher = testDispatcher
        viewModel.loadPlugins()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.installPlugin(plugin2)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.installingIds.isEmpty())
    }

    @Test
    fun testUninstallPlugin() = runTest {
        `when`(pluginManager.fetchAvailablePlugins()).thenReturn(listOf(plugin1, plugin2))
        `when`(pluginManager.uninstallPlugin(plugin1.id)).thenReturn(true)

        val viewModel = NovelPluginsManagerViewModel(pluginManager)
        viewModel.ioDispatcher = testDispatcher
        viewModel.loadPlugins()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uninstallPlugin(plugin1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.installingIds.isEmpty())
    }
}

