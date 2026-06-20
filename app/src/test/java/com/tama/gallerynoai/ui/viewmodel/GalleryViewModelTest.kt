package com.tama.gallerynoai.ui.viewmodel

import com.tama.gallerynoai.data.repository.MediaRepository
import com.tama.gallerynoai.data.repository.MediaSearchProvider // Tambahkan import ini
import com.tama.gallerynoai.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    private lateinit var viewModel: GalleryViewModel
    private lateinit var repository: MediaRepository
    private lateinit var settingsManager: SettingsManager
    private lateinit var searchProvider: MediaSearchProvider // 1. Tambahkan variabel mock ini

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock(MediaRepository::class.java)
        settingsManager = mock(SettingsManager::class.java)
        searchProvider = mock(MediaSearchProvider::class.java) // 2. Inisialisasi mock

        // Mock settings flows
        `when`(settingsManager.defaultSort).thenReturn(MutableStateFlow("DATE_NEWEST"))
        `when`(settingsManager.dateFormat).thenReturn(MutableStateFlow("dd/MM/yyyy"))
        `when`(settingsManager.useDefaultEditor).thenReturn(MutableStateFlow(false))
        `when`(settingsManager.defaultEditorPackage).thenReturn(MutableStateFlow(null))
        `when`(settingsManager.useDefaultVideoEditor).thenReturn(MutableStateFlow(false))
        `when`(settingsManager.defaultVideoEditorPackage).thenReturn(MutableStateFlow(null))
        `when`(settingsManager.autoPlayVideo).thenReturn(MutableStateFlow(true))
        `when`(settingsManager.defaultMuteVideo).thenReturn(MutableStateFlow(false))
        `when`(settingsManager.fullscreenRotationMode).thenReturn(MutableStateFlow(com.tama.gallerynoai.data.settings.FullscreenRotationMode.SYSTEM_SETTING))
        `when`(settingsManager.gridColumns).thenReturn(MutableStateFlow(3))
        `when`(settingsManager.trashWarningEnabled).thenReturn(MutableStateFlow(true))
        `when`(settingsManager.searchAllFilesByDefault).thenReturn(MutableStateFlow(true))

        `when`(repository.getAllMediaFlow()).thenReturn(emptyFlow())
        `when`(repository.observeMediaChange()).thenReturn(emptyFlow())
        `when`(repository.getRecentSearches()).thenReturn(emptyFlow())
        runBlocking { `when`(repository.getAllCustomTags()).thenReturn(emptyList()) }

        // 3. Tambahkan searchProvider ke dalam constructor GalleryViewModel
        viewModel = GalleryViewModel(repository, settingsManager, searchProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() {
        assertFalse(viewModel.selectionMode.value)
        assertTrue(viewModel.selectedIds.value.isEmpty())
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun testToggleSelection() {
        viewModel.toggleSelection(1L)
        assertTrue(viewModel.selectionMode.value)
        assertTrue(viewModel.selectedIds.value.contains(1L))

        viewModel.toggleSelection(1L)
        assertFalse(viewModel.selectedIds.value.contains(1L))
    }

    @Test
    fun testClearSelection() {
        viewModel.toggleSelection(1L)
        viewModel.clearSelection()
        assertFalse(viewModel.selectionMode.value)
        assertTrue(viewModel.selectedIds.value.isEmpty())
    }

    @Test
    fun testSearchQueryChange() = runTest {
        viewModel.onSearchQueryChange("test")
        assertEquals("test", viewModel.searchQuery.value)
        assertTrue(viewModel.isSearching.value)
    }
}