package com.tama.gallerynoai.ui.viewmodel

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tama.gallerynoai.data.model.*
import com.tama.gallerynoai.data.repository.MediaRepository
import com.tama.gallerynoai.data.repository.MediaSearchProvider
import com.tama.gallerynoai.data.settings.FullscreenRotationMode
import com.tama.gallerynoai.data.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.flatMapLatest
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import javax.inject.Inject

sealed class GalleryItem {
    data class Header(val date: String, val timestamp: Long) : GalleryItem()
    data class Media(val item: MediaItem) : GalleryItem()
}

@HiltViewModel
@OptIn(FlowPreview::class)
class GalleryViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsManager: SettingsManager,
    private val searchProvider: MediaSearchProvider
) : ViewModel() {

    private val _sortType = MutableStateFlow(SortType.DATE_NEWEST)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    val dateFormat: StateFlow<String> = settingsManager.dateFormat

    // Calculate masterGridCount in Background Thread
    val masterGridCount: StateFlow<Int> = combine(
        repository.getAllMediaFlow(),
        settingsManager.dateFormat,
        _sortType
    ) { items, format, sort ->
        withContext(Dispatchers.Default) {
            if (items.isEmpty()) return@withContext 0
            if (sort != SortType.DATE_NEWEST && sort != SortType.DATE_OLDEST) {
                return@withContext items.size
            }

            var total = 0
            var lastDate = ""
            val formatter = try {
                DateTimeFormatter.ofPattern(format, Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
            } catch (e: Exception) {
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
            }

            items.forEach { media ->
                val date = try {
                    formatter.format(Instant.ofEpochSecond(media.dateModified))
                } catch (e: Exception) { "" }

                if (date != lastDate) {
                    total += 1 // Count Header
                    lastDate = date
                }
                total += 1 // Count Media
            }
            total
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5.seconds),
        initialValue = 0
    )

    private val dateCache = ConcurrentHashMap<Long, String>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMediaItems: Flow<PagingData<GalleryItem>> = combine(
        settingsManager.dateFormat,
        _sortType,
        settingsManager.hiddenAlbumIds
    ) { format, sort, hiddenIds ->
        Triple(format, sort, hiddenIds.toList())
    }.flatMapLatest { (format, sort, hiddenIds) ->
        val formatter = try {
            DateTimeFormatter.ofPattern(format, Locale.getDefault())
                .withZone(ZoneId.systemDefault())
        } catch (e: Exception) {
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
                .withZone(ZoneId.systemDefault())
        }

        repository.getMediaPaged(sort, hiddenIds).map { pagingData ->
            dateCache.clear()
            val mapped = pagingData.map { GalleryItem.Media(it) as GalleryItem }

            if (sort == SortType.DATE_NEWEST || sort == SortType.DATE_OLDEST) {
                mapped.insertSeparators { before, after ->
                    if (after == null) return@insertSeparators null
                    val afterMedia = after as? GalleryItem.Media ?: return@insertSeparators null

                    if (before == null) {
                        val date = dateCache.getOrPut(afterMedia.item.dateModified) {
                            formatter.format(Instant.ofEpochSecond(afterMedia.item.dateModified))
                        }
                        return@insertSeparators GalleryItem.Header(date, afterMedia.item.dateModified)
                    }

                    val beforeMedia = before as? GalleryItem.Media ?: return@insertSeparators null

                    val beforeDate = dateCache.getOrPut(beforeMedia.item.dateModified) {
                        formatter.format(Instant.ofEpochSecond(beforeMedia.item.dateModified))
                    }
                    val afterDate = dateCache.getOrPut(afterMedia.item.dateModified) {
                        formatter.format(Instant.ofEpochSecond(afterMedia.item.dateModified))
                    }

                    if (beforeDate != afterDate) {
                        GalleryItem.Header(afterDate, afterMedia.item.dateModified)
                    } else {
                        null
                    }
                }
            } else {
                mapped
            }
        }
    }.cachedIn(viewModelScope)

    val useDefaultEditor: StateFlow<Boolean> = settingsManager.useDefaultEditor
    val defaultEditorPackage: StateFlow<String?> = settingsManager.defaultEditorPackage
    val useDefaultVideoEditor: StateFlow<Boolean> = settingsManager.useDefaultVideoEditor
    val defaultVideoEditorPackage: StateFlow<String?> = settingsManager.defaultVideoEditorPackage
    val autoPlayVideo: StateFlow<Boolean> = settingsManager.autoPlayVideo
    val defaultMuteVideo: StateFlow<Boolean> = settingsManager.defaultMuteVideo
    val fullscreenRotationMode: StateFlow<FullscreenRotationMode> = settingsManager.fullscreenRotationMode
    val gridColumns: StateFlow<Int> = settingsManager.gridColumns
    val trashWarningEnabled: StateFlow<Boolean> = settingsManager.trashWarningEnabled
    val enableVideoPreload: StateFlow<Boolean> = settingsManager.enableVideoPreload
    val hiddenAlbumIds: StateFlow<Set<String>> = settingsManager.hiddenAlbumIds
    val quickAccessItems: StateFlow<Set<String>> = settingsManager.quickAccessItems

    private val _scrollToTopTrigger = MutableSharedFlow<String>(replay = 0)
    val scrollToTopTrigger: SharedFlow<String> = _scrollToTopTrigger.asSharedFlow()

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _favoriteItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val favoriteItems: StateFlow<List<MediaItem>> = _favoriteItems.asStateFlow()

    private val _videoItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val videoItems: StateFlow<List<MediaItem>> = _videoItems.asStateFlow()

    private val _screenshotItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val screenshotItems: StateFlow<List<MediaItem>> = _screenshotItems.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val albums: StateFlow<List<AlbumItem>> = _albums.asStateFlow()

    private val _trashedMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val trashedMedia: StateFlow<List<MediaItem>> = _trashedMedia.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _hasLoadedOnce = MutableStateFlow(false)
    val hasLoadedOnce: StateFlow<Boolean> = _hasLoadedOnce.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedSearchResults: Flow<PagingData<MediaItem>> = combine(
        _searchQuery.debounce(300.milliseconds),
        settingsManager.hiddenAlbumIds
    ) { query, hiddenIds ->
        query to hiddenIds.toList()
    }.flatMapLatest { (query, hiddenIds) ->
        if (query.isBlank()) {
            flowOf(PagingData.empty())
        } else {
            repository.searchMediaPaged(query, hiddenIds)
                .cachedIn(viewModelScope)
        }
    }

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _searchOptions = MutableStateFlow(SearchOptions())

    private val _allUniqueTags = MutableStateFlow<List<String>>(emptyList())
    val allUniqueTags: StateFlow<List<String>> = _allUniqueTags.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _albumSelectionMode = MutableStateFlow(false)
    val albumSelectionMode = _albumSelectionMode.asStateFlow()

    private val _selectedAlbumIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedAlbumIds: StateFlow<Set<String>> = _selectedAlbumIds.asStateFlow()

    private val _pendingMovePath = MutableStateFlow<String?>(null)

    private val _pendingMoveUris = MutableStateFlow<List<Uri>>(emptyList())

    private var pendingRenameUri: Uri? = null
    private var pendingRenameName: String? = null

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            var isFirst = true
            repository.observeMediaChange()
                .collectLatest {
                    if (!isFirst) {
                        delay(500.milliseconds)
                    }
                    loadMedia(silent = !isFirst)
                    isFirst = false
                }
        }

        repository.getAllMediaFlow()
            .debounce(500.milliseconds)
            .onEach { items ->
                val favorites = withContext(Dispatchers.Default) { items.filter { it.isFavorite } }
                val videos = withContext(Dispatchers.Default) { items.filter { it.isVideo } }
                val screenshots = withContext(Dispatchers.Default) {
                    items.filter {
                        it.relativePath?.contains("Screenshots", ignoreCase = true) == true ||
                                it.name.contains("Screenshot", ignoreCase = true)
                    }
                }

                withContext(Dispatchers.Main) {
                    _mediaItems.value = items
                    _favoriteItems.value = favorites
                    _videoItems.value = videos
                    _screenshotItems.value = screenshots
                    updateDisplayItems()

                    if (_searchQuery.value.isNotEmpty()) {
                        filterMedia(_searchQuery.value)
                    }
                }
            }
            .launchIn(viewModelScope)

        repository.getAlbumStats()
            .onEach {
                _albums.value = it
            }
            .launchIn(viewModelScope)

        _searchQuery
            .debounce(300.milliseconds)
            .onEach { query ->
                // filterMedia(query) // Removed for Paging
            }
            .launchIn(viewModelScope)

        settingsManager.dateFormat
            .onEach { updateDisplayItems() }
            .launchIn(viewModelScope)

        repository.getRecentSearches()
            .onEach {
                _recentSearches.value = it
            }
            .launchIn(viewModelScope)

        settingsManager.defaultSort
            .onEach { sortString ->
                try {
                    val newSortType = SortType.valueOf(sortString)
                    _sortType.value = newSortType
                } catch (e: Exception) {
                    // Ignore invalid sort types
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isNotEmpty()) {
            _isSearching.value = true
            updateSuggestions(query)
        } else {
            _searchSuggestions.value = emptyList()
        }
    }

    private fun updateSuggestions(query: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val normalizedQuery = query.lowercase().trim()
            if (normalizedQuery.isEmpty()) {
                _searchSuggestions.value = emptyList()
                return@launch
            }

            val tags = _allUniqueTags.value
            val categories = listOf("Videos", "Screenshots", "Camera", "WhatsApp")

            val allSuggestions = (tags + categories).distinct()
            val filtered = allSuggestions
                .filter { it.lowercase().contains(normalizedQuery) }
                .sortedBy {
                    val lower = it.lowercase()
                    when {
                        lower == normalizedQuery -> 0
                        lower.startsWith(normalizedQuery) -> 1
                        else -> 2
                    }
                }
                .take(5)

            _searchSuggestions.value = filtered
        }
    }

    fun saveSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            repository.saveSearch(query)
        }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            repository.deleteRecentSearch(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    fun triggerScrollToTop(route: String) {
        viewModelScope.launch {
            _scrollToTopTrigger.emit(route)
        }
    }

    private fun filterMedia(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val options = _searchOptions.value.copy(
                searchAllFiles = settingsManager.searchAllFilesByDefault.value
            )

            val results = searchProvider.processSearch(
                items = _mediaItems.value,
                query = query,
                options = options
            )

            if (isActive) {
                _searchResults.value = results
                _isSearching.value = false
            }
        }
    }

    fun loadMedia(silent: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                if (!silent) _isLoading.value = true

                // Sync MediaStore with Room first to ensure we have up-to-date data
                repository.syncMediaStoreWithRoom()

                loadTrashedMedia()
                refreshUniqueTags()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    if (isActive) {
                        _isLoading.value = false
                        _hasLoadedOnce.value = true
                    }
                }
            }
        }
    }

    fun loadTrashedMedia() {
        viewModelScope.launch {
            _trashedMedia.value = repository.fetchTrashedMedia()
        }
    }

    fun restoreMedia(uris: List<Uri>): IntentSender? {
        return repository.createRestoreRequest(uris)
    }

    fun deletePermanently(uris: List<Uri>): IntentSender? {
        return repository.createDeletePermanentlyRequest(uris)
    }

    fun deleteAlbums(albumIds: Set<String>): IntentSender? {
        val allMediaUris = _mediaItems.value.filter { albumIds.contains(it.bucketId) }.map { it.uri }
        return if (allMediaUris.isNotEmpty()) {
            repository.createTrashRequest(allMediaUris)
        } else null
    }

    fun moveToTrash(uris: List<Uri>): IntentSender? {
        return repository.createTrashRequest(uris)
    }

    fun favoriteMedia(uris: List<Uri>, favorite: Boolean): IntentSender? {
        return repository.createFavoriteRequest(uris, favorite)
    }

    fun moveMedia(uris: List<Uri>, targetPath: String): IntentSender? {
        _pendingMovePath.value = targetPath
        _pendingMoveUris.value = uris
        val intentSender = repository.moveMedia(uris)
        if (intentSender == null) {
            onMoveConfirmed()
        }
        return intentSender
    }

    fun renameMedia(item: MediaItem, newName: String): IntentSender? {
        pendingRenameUri = item.uri
        pendingRenameName = newName
        
        val result = repository.renameMedia(item.uri, newName)
        if (result == null) {
            loadMedia(false)
            pendingRenameUri = null
            pendingRenameName = null
        }
        return result
    }

    fun onRenameConfirmed() {
        val uri = pendingRenameUri ?: return
        val name = pendingRenameName ?: return
        viewModelScope.launch {
            repository.renameMedia(uri, name)
            pendingRenameUri = null
            pendingRenameName = null
            loadMedia(false)
        }
    }

    fun onMoveConfirmed() {
        val path = _pendingMovePath.value
        val uris = _pendingMoveUris.value
        if (path != null && uris.isNotEmpty()) {
            viewModelScope.launch {
                repository.performMove(uris, path)
                loadMedia()
                clearSelection()
                _pendingMovePath.value = null
                _pendingMoveUris.value = emptyList()
            }
        }
    }

    fun copyMedia(uris: List<Uri>, targetPath: String) {
        viewModelScope.launch {
            repository.copyMedia(uris, targetPath)
            delay(500.milliseconds)
            loadMedia()
            clearSelection()
        }
    }

    fun selectAll(items: List<MediaItem>) {
        _selectedIds.value = items.map { it.id }.toSet()
        _selectionMode.value = true
    }

    fun setSortType(type: SortType) {
        settingsManager.setDefaultSort(type.name)
    }

    fun toggleSelection(itemId: Long) {
        _selectedIds.value = if (_selectedIds.value.contains(itemId)) _selectedIds.value - itemId else _selectedIds.value + itemId
        if (_selectedIds.value.isNotEmpty() && !_selectionMode.value) _selectionMode.value = true
    }

    fun setSelectionMode(enabled: Boolean) {
        _selectionMode.value = enabled
        if (!enabled) clearSelection()
    }

    fun setSelectedIds(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _selectionMode.value = false
        _selectedAlbumIds.value = emptySet()
        _albumSelectionMode.value = false
    }

    fun toggleAlbumHidden(id: String) {
        settingsManager.toggleAlbumHidden(id)
    }

    fun toggleAlbumSelection(albumId: String) {
        _selectedAlbumIds.value = if (_selectedAlbumIds.value.contains(albumId)) _selectedAlbumIds.value - albumId else _selectedAlbumIds.value + albumId
        if (_selectedAlbumIds.value.isNotEmpty() && !_albumSelectionMode.value) _albumSelectionMode.value = true
    }

    fun setAlbumSelectionMode(enabled: Boolean) {
        _albumSelectionMode.value = enabled
        if (!enabled) clearAlbumSelection()
    }

    fun clearAlbumSelection() {
        _selectedAlbumIds.value = emptySet()
        _albumSelectionMode.value = false
    }

    private suspend fun updateDisplayItems() {
        withContext(Dispatchers.Default) {
            // Update albums when media items change
            loadMedia(silent = true)
        }
    }

    fun groupMediaByDate(items: List<MediaItem>, format: String? = null): List<GalleryItem> {
        val grouped = mutableListOf<GalleryItem>()
        val currentFormat = format ?: settingsManager.dateFormat.value
        val formatter = try {
            DateTimeFormatter.ofPattern(currentFormat, Locale.getDefault())
                .withZone(ZoneId.systemDefault())
        } catch (e: Exception) {
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
                .withZone(ZoneId.systemDefault())
        }

        val localDateCache = mutableMapOf<Long, String>()

        items.groupBy { item ->
            localDateCache.getOrPut(item.dateModified) {
                formatter.format(Instant.ofEpochSecond(item.dateModified))
            }
        }.forEach { (date, itemsInDate) ->
            val firstItem = itemsInDate.first()
            grouped.add(GalleryItem.Header(date, firstItem.dateModified))
            itemsInDate.forEach { grouped.add(GalleryItem.Media(it)) }
        }
        return grouped
    }

    fun updateMediaMetadata(item: MediaItem, customTags: List<String>? = null) {
        viewModelScope.launch {
            repository.updateMediaMetadata(id = item.id, customTags = customTags)
            refreshUniqueTags()
        }
    }

    fun batchUpdateMediaMetadata(itemIds: List<Long>, tag: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                itemIds.forEach { id ->
                    val existing = repository.getMediaItemById(id)
                    if (existing != null && !existing.customTags.contains(tag)) {
                        repository.updateMediaMetadata(id = id, customTags = existing.customTags + tag)
                    }
                }
            }
            refreshUniqueTags()
            clearSelection()
        }
    }

    private fun refreshUniqueTags() {
        viewModelScope.launch {
            val allTagsJson = repository.getAllCustomTags()
            val uniqueTags = allTagsJson
                .flatMap { json ->
                    try {
                        val array = org.json.JSONArray(json)
                        val list = mutableListOf<String>()
                        for (i in 0 until array.length()) list.add(array.getString(i))
                        list
                    } catch (e: Exception) { emptyList() }
                }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            _allUniqueTags.value = uniqueTags
        }
    }
}
